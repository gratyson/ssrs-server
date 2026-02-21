package com.gt.ssrs.reviewSession;

import com.gt.ssrs.language.Language;
import com.gt.ssrs.lexicon.LexiconService;
import com.gt.ssrs.model.*;
import com.gt.ssrs.reviewHistory.WordReviewHistoryService;
import com.gt.ssrs.model.ReviewEvent;
import com.gt.ssrs.reviewHistory.model.LearnedStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.gt.ssrs.word.WordService;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class ReviewEventProcessor {

    private static final Logger log = LoggerFactory.getLogger(ReviewEventProcessor.class);

    static final int QUERY_BATCH_SIZE = 1000;

    private final ReviewEventDao reviewEventDao;
    private final ScheduledReviewDao scheduledReviewDao;
    private final LexiconService lexiconService;
    private final WordService wordService;
    private final WordReviewHistoryService wordReviewHistoryService;
    private final int initialLearningDelaySec;
    private final int nearMissCorrectDelaySec;
    private final double standardIncorrectBoost;
    private final double nearMissBoost;

    @Autowired
    public ReviewEventProcessor(ReviewEventDao reviewEventDao,
                                ScheduledReviewDao scheduledReviewDao,
                                LexiconService lexiconService,
                                WordService wordService,
                                WordReviewHistoryService wordReviewHistoryService,
                                @Value("${ssrs.learning.initialLearningDelaySec}") int initialLearningDelaySec,
                                @Value("${ssrs.learning.nearMissCorrectLearningDelaySec}") int nearMissCorrectDelaySec,
                                @Value("${ssrs.learning.standardIncorrectBoost}") double standardIncorrectBoost,
                                @Value("${ssrs.learning.nearMissBoost}") double nearMissBoost) {
        this.reviewEventDao = reviewEventDao;
        this.scheduledReviewDao = scheduledReviewDao;
        this.lexiconService = lexiconService;
        this.wordService = wordService;
        this.wordReviewHistoryService = wordReviewHistoryService;

        this.initialLearningDelaySec = initialLearningDelaySec;
        this.nearMissCorrectDelaySec = nearMissCorrectDelaySec;
        this.standardIncorrectBoost = standardIncorrectBoost;
        this.nearMissBoost = nearMissBoost;
    }


    public void processEvents(String username, String lexiconId) {
        List<ReviewEvent> allEvents = reviewEventDao.loadUnprocessedReviewEventsForUser(username, lexiconId);
        List<WordReviewHistory> newWordHistories = new ArrayList<>();
        List<ScheduledReview> newScheduledReviews = new ArrayList<>();

        if (allEvents.size() > 0) {
            Language language = Language.getLanguageById(lexiconService.getLexiconMetadata(lexiconId).languageId());
            Map<String, List<ReviewEvent>> eventsByWord = allEvents.stream().collect(Collectors.groupingBy(ReviewEvent::wordId));

            Map<String, Word> words = wordService.loadWords(eventsByWord.keySet()).stream().collect(Collectors.toMap(word -> word.id(), word -> word));
            Map<String, WordReviewHistory> histories = wordReviewHistoryService.getWordReviewHistory(lexiconId, username, words.keySet()).stream().collect(Collectors.toMap(history -> history.wordId(), history -> history));

            eventsByWord.forEach((wordId, eventList) -> {
                Word word = words.get(wordId);

                ProcessedHistoryAndNextReview processedHistoryAndNextReview = processWordEvents(lexiconId, username, language, word, histories.get(wordId), eventList);

                if (processedHistoryAndNextReview != null) {
                    if (processedHistoryAndNextReview.newHistory != null) {
                        newWordHistories.add(processedHistoryAndNextReview.newHistory);
                    }
                    if (processedHistoryAndNextReview.scheduledReview != null) {
                        newScheduledReviews.add(processedHistoryAndNextReview.scheduledReview);
                    }
                }
            });

            if (newWordHistories.size() > 0) {
                wordReviewHistoryService.updateWordReviewHistoryBatch(username, newWordHistories);
            }
            if (newScheduledReviews.size() > 0) {
                scheduledReviewDao.createScheduledReviewsBatch(newScheduledReviews);
            }
            markEventsAsProcessed(allEvents);
        }
    }

    public LexiconReviewSummary getLexiconReviewSummary(String lexiconId, String username, Instant futureEventCutoff) {
        Map<LearnedStatus, List<String>> wordIdsByLearned = wordReviewHistoryService.getWordIdsForUserByLearned(lexiconId, username);

        int totalWords = (int)wordIdsByLearned.values().stream().flatMap(wordIdList -> wordIdList.stream()).count();
        int learnedWords = wordIdsByLearned.getOrDefault(LearnedStatus.Learned, List.of()).size();

        return new LexiconReviewSummary(totalWords, learnedWords, getFutureReviewEvents(lexiconId, username, futureEventCutoff));
    }

    private List<FutureReviewEvent> getFutureReviewEvents(String lexiconId, String username, Instant cutoff) {
        LexiconMetadata lexiconMetadata = lexiconService.getLexiconMetadata(lexiconId);
        Language language = Language.getLanguageById(lexiconMetadata.languageId());

        List<FutureReviewEvent> futureReviewEvents = new ArrayList<>();

        List<ScheduledReview> scheduledReviews = scheduledReviewDao.loadScheduledReviews(username, lexiconId, "", Optional.of(cutoff));

        Map<String, WordReviewHistory> reviewHistoryByWordId = wordReviewHistoryService.getWordReviewHistory(lexiconId, username,
                        scheduledReviews.stream().map(review -> review.wordId()).toList())
                .stream().collect(Collectors.toMap(history -> history.wordId(), history -> history));

        for(ScheduledReview scheduledReview : scheduledReviews) {
            Instant reviewTime = scheduledReview.scheduledTestTime().isAfter(Instant.now()) ? scheduledReview.scheduledTestTime() : Instant.now();

            futureReviewEvents.add(new FutureReviewEvent(lexiconId, scheduledReview.wordId(), reviewTime, false));
            futureReviewEvents.addAll(inferFutureReviewEvents(lexiconId, language, cutoff, reviewTime, scheduledReview, reviewHistoryByWordId.get(scheduledReview.wordId())));
        }

        return futureReviewEvents;
    }

    private List<FutureReviewEvent> inferFutureReviewEvents(String lexiconId, Language language, Instant cutoff, Instant reviewTime, ScheduledReview scheduledReview, WordReviewHistory reviewHistory) {
        List<FutureReviewEvent> futureReviewEvents = new ArrayList<>();

        if (scheduledReview.reviewType() == ReviewType.Review) {
            Duration nextReviewDelay = calculateNextDelayAfterSuccessfulTest(language, reviewHistory.currentTestDelay(), reviewHistory.currentBoost(), reviewHistory.currentBoostExpirationDelay());
            Instant nextReviewTime = reviewTime.plus(nextReviewDelay);

            while (nextReviewTime.isBefore(cutoff)) {
                futureReviewEvents.add(new FutureReviewEvent(lexiconId, reviewHistory.wordId(), nextReviewTime, true));

                nextReviewDelay = calculateNextDelayAfterSuccessfulTest(language, nextReviewDelay, reviewHistory.currentBoost(), reviewHistory.currentBoostExpirationDelay());
                nextReviewTime = nextReviewTime.plus(nextReviewDelay);
            }
        }

        return futureReviewEvents;
    }

    private ProcessedHistoryAndNextReview processWordEvents(String lexiconId, String username, Language language, Word word, WordReviewHistory wordHistory, List<ReviewEvent> reviewEvents) {
        Map<ReviewType, List<ReviewEvent>> eventsByType = reviewEvents.stream().collect(Collectors.groupingBy(ReviewEvent::reviewType));

        if (wordHistory != null && wordHistory.learned()) {
            if (eventsByType.containsKey(ReviewType.Review)) {
                return processReviewEvents(lexiconId, username, language, word, eventsByType.get(ReviewType.Review), wordHistory);
            }
        } else if (eventsByType.containsKey(ReviewType.Learn)) {
            return processLearningEvents(lexiconId, username, language, word, eventsByType.get(ReviewType.Learn));
        }

        return null;
    }

    private ProcessedHistoryAndNextReview processReviewEvents(String lexiconId, String username, Language language, Word word, List<ReviewEvent> reviewEvents, WordReviewHistory wordReviewHistory) {
        List<ReviewEvent> sortedEvents = new ArrayList<>(reviewEvents);
        sortedEvents.sort(Comparator.comparing(ReviewEvent::eventInstant));

        ReviewEvent eventToProcess = sortedEvents.get(0);
        for(int index = 1; index < sortedEvents.size(); index++) {
            if (sortedEvents.get(index).override()) {
                eventToProcess = sortedEvents.get(index);
            }
        }

        return processReviewEvent(lexiconId, username, language, word, eventToProcess, wordReviewHistory);
    }

    private ProcessedHistoryAndNextReview processReviewEvent(String lexiconId, String username, Language language, Word word, ReviewEvent reviewEvent, WordReviewHistory wordReviewHistory) {
        if (reviewEvent.isCorrect()) {
            if (reviewEvent.isNearMiss()) {
                return processCorrectNearMissReviewEvent(lexiconId, username, language, word, reviewEvent, wordReviewHistory, reviewEvent.testRelationship().getId());
            } else {
                return processCorrectReviewEvent(lexiconId, username, language, word, reviewEvent, wordReviewHistory, reviewEvent.testRelationship().getId());
            }
        } else {
            return processIncorrectReviewEvent(lexiconId, username, language, word, reviewEvent, wordReviewHistory, reviewEvent.testRelationship().getId(), reviewEvent.isNearMiss());
        }
    }

    private ProcessedHistoryAndNextReview processCorrectReviewEvent(String lexiconId, String username, Language language, Word word, ReviewEvent reviewEvent, WordReviewHistory wordReviewHistory, String testRelationshipId) {
        Duration newTestDelay = calculateNextDelayAfterSuccessfulTest(language, wordReviewHistory);
        boolean isCurrentBoostExpired = newTestDelay.compareTo(wordReviewHistory.currentBoostExpirationDelay()) >= 0;

        WordReviewHistory newWordReviewHistory = new WordReviewHistory(
                lexiconId,
                username,
                word.id(),
                true,
                reviewEvent.eventInstant(),
                reviewEvent.testRelationship().getId(),
                newTestDelay,
                isCurrentBoostExpired ? 0 : wordReviewHistory.currentBoost(),
                isCurrentBoostExpired ? Duration.ZERO : wordReviewHistory.currentBoostExpirationDelay(),
                updateTestHistory(wordReviewHistory.testHistory(), testRelationshipId, true));

        ScheduledReview newScheduledReview = buildScheduledReview(language, lexiconId, username, word, reviewEvent, newTestDelay, newWordReviewHistory);

        return new ProcessedHistoryAndNextReview(newWordReviewHistory, newScheduledReview);
    }

    private ProcessedHistoryAndNextReview processIncorrectReviewEvent(String lexiconId, String username, Language language, Word word, ReviewEvent reviewEvent, WordReviewHistory wordReviewHistory, String testRelationshipId, boolean isNearMiss) {
        double boost = isNearMiss ? nearMissBoost : standardIncorrectBoost;

        Duration newTestDelay = Duration.ofSeconds(initialLearningDelaySec);

        WordReviewHistory newWordReviewHistory = new WordReviewHistory(
                lexiconId,
                username,
                word.id(),
                true,
                reviewEvent.eventInstant(),
                reviewEvent.testRelationship().getId(),
                newTestDelay,
                boost,
                wordReviewHistory.currentTestDelay(),
                updateTestHistory(wordReviewHistory.testHistory(), testRelationshipId, false));

        ScheduledReview newScheduledReview = buildScheduledReview(language, lexiconId, username, word, reviewEvent, newTestDelay, newWordReviewHistory);

        return new ProcessedHistoryAndNextReview(newWordReviewHistory, newScheduledReview);
    }
    
    private ProcessedHistoryAndNextReview processCorrectNearMissReviewEvent(String lexiconId, String username, Language language, Word word, ReviewEvent reviewEvent, WordReviewHistory wordReviewHistory, String testRelationshipId) {
        if (wordReviewHistory.currentTestDelay().getSeconds() < nearMissCorrectDelaySec) {
            return processCorrectReviewEvent(lexiconId, username, language, word, reviewEvent, wordReviewHistory, testRelationshipId);
        }

        Duration newTestDelay = Duration.ofSeconds(nearMissCorrectDelaySec);
        Duration newBoostExpirationDelay = wordReviewHistory.currentBoostExpirationDelay().compareTo(wordReviewHistory.currentTestDelay()) > 0
                ? wordReviewHistory.currentBoostExpirationDelay()
                : wordReviewHistory.currentTestDelay();

        WordReviewHistory newWordReviewHistory = new WordReviewHistory(
                lexiconId,
                username,
                word.id(),
                true,
                reviewEvent.eventInstant(),
                reviewEvent.testRelationship().getId(),
                newTestDelay,
                nearMissBoost,
                newBoostExpirationDelay,
                updateTestHistory(wordReviewHistory.testHistory(), testRelationshipId, true));

        ScheduledReview newScheduledReview = buildScheduledReview(language, lexiconId, username, word, reviewEvent, newTestDelay, newWordReviewHistory);
        
        return new ProcessedHistoryAndNextReview(newWordReviewHistory, newScheduledReview);
    }

    private Map<String, TestHistory> updateTestHistory(Map<String, TestHistory> oldTestHistory, String testRelationshipId, boolean isCorrect) {
        TestHistory oldRelationshipHistory = oldTestHistory.getOrDefault(testRelationshipId, new TestHistory(0,0,0));
        TestHistory nweRelationshipHistory = new TestHistory(
                oldRelationshipHistory.totalTests() + 1,
                isCorrect ? oldRelationshipHistory.correct() + 1 : oldRelationshipHistory.correct(),
                isCorrect ? oldRelationshipHistory.correctStreak() + 1 : 0);

        Map<String, TestHistory> newTestHistory = new HashMap<>(oldTestHistory);
        newTestHistory.put(testRelationshipId, nweRelationshipHistory);

        return newTestHistory;
    }

    private ProcessedHistoryAndNextReview processLearningEvents(String lexiconId, String username, Language language, Word word, List<ReviewEvent> reviewEvents) {
        List<ReviewEvent> sortedEvents = new ArrayList<>(reviewEvents);
        sortedEvents.sort(Comparator.comparing(ReviewEvent::eventInstant));
        ReviewEvent eventToProcess = sortedEvents.get(sortedEvents.size() - 1);

        Duration newTestDelay = Duration.ofSeconds(initialLearningDelaySec);

        WordReviewHistory newLexiconReviewHistory = new WordReviewHistory(
                lexiconId,
                username,
                word.id(),
                true,
                eventToProcess.eventInstant(),
                "",
                newTestDelay,
                0,
                Duration.ZERO,
                new HashMap<>());

        ScheduledReview newScheduledReview = buildScheduledReview(language, lexiconId, username, word, eventToProcess, newTestDelay, newLexiconReviewHistory);

        return new ProcessedHistoryAndNextReview(newLexiconReviewHistory, newScheduledReview);
    }

    private List<String> markEventsAsProcessed(List<ReviewEvent> reviewEvents) {
        return reviewEventDao.markEventsAsProcessed(reviewEvents);
    }

    private Duration calculateNextDelayAfterSuccessfulTest(Language language, WordReviewHistory lexiconReviewHistory) {
        return calculateNextDelayAfterSuccessfulTest(language, lexiconReviewHistory.currentTestDelay(), lexiconReviewHistory.currentBoost(), lexiconReviewHistory.currentBoostExpirationDelay());
    }

    private Duration calculateNextDelayAfterSuccessfulTest(Language language, Duration currentTestDelay, double currentBoost, Duration currentBoostExpirationDelay) {
        double testsToDoubleDelay = language.getTestsToDouble() <= 0 ? 1 : language.getTestsToDouble();  // guard against 0 since it would cause a divide-by-zero error
        Duration standardDelay = calculateNextDelayWithBoost(currentTestDelay, 1, testsToDoubleDelay);

        double boost = currentBoost > 1 ? currentBoost : 1;
        if (boost > 1) {
            Duration boostedDelay = calculateNextDelayWithBoost(currentTestDelay, boost, testsToDoubleDelay);

            // If boosted but expired, return either the standard delay or the expiration delay, whichever is greater
            if (boostedDelay.compareTo(currentBoostExpirationDelay) > 0) {
                if (standardDelay.compareTo(currentBoostExpirationDelay) < 0) {
                    return currentBoostExpirationDelay;
                }
            } else {
                return boostedDelay;
            }
        }

        return standardDelay;
    }

    private Duration calculateNextDelayWithBoost(Duration currentTestDelay, double boost, double testsToDoubleDelay) {
        return Duration.ofSeconds((long)(currentTestDelay.getSeconds() * Math.pow(2, boost / testsToDoubleDelay)));
    }

    private ScheduledReview buildScheduledReview(Language language, String lexiconId, String username, Word word, ReviewEvent reviewEvent, Duration newTestDelay, WordReviewHistory history) {
        return new ScheduledReview(
                UUID.randomUUID().toString(),
                username,
                lexiconId,
                word.id(),
                ReviewType.Review,
                WordReviewHelper.getNextTestRelationship(language, word, history),
                reviewEvent.eventInstant().plus(newTestDelay),
                newTestDelay,
                false);
    }

    private record ProcessedHistoryAndNextReview(WordReviewHistory newHistory, ScheduledReview scheduledReview) { }
}
