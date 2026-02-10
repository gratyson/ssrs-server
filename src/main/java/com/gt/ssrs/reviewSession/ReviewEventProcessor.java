package com.gt.ssrs.reviewSession;

import com.gt.ssrs.language.Language;
import com.gt.ssrs.language.TestRelationship;
import com.gt.ssrs.lexicon.LexiconService;
import com.gt.ssrs.model.*;
import com.gt.ssrs.reviewHistory.WordReviewHistoryService;
import com.gt.ssrs.reviewSession.model.DBReviewEvent;
import com.gt.ssrs.reviewSession.model.DBScheduledReview;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
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

    private final ReviewSessionDao reviewSessionDao;
    private final LexiconService lexiconService;
    private final WordService wordService;
    private final WordReviewHistoryService wordReviewHistoryService;
    private final int initialLearningDelaySec;
    private final int nearMissCorrectDelaySec;
    private final double standardIncorrectBoost;
    private final double nearMissBoost;

    @Autowired
    public ReviewEventProcessor(ReviewSessionDao reviewSessionDao,
                                LexiconService lexiconService,
                                WordService wordService,
                                WordReviewHistoryService wordReviewHistoryService,
                                @Value("${ssrs.learning.initialLearningDelaySec}") int initialLearningDelaySec,
                                @Value("${ssrs.learning.nearMissCorrectLearningDelaySec}") int nearMissCorrectDelaySec,
                                @Value("${ssrs.learning.standardIncorrectBoost}") double standardIncorrectBoost,
                                @Value("${ssrs.learning.nearMissBoost}") double nearMissBoost) {
        this.reviewSessionDao = reviewSessionDao;
        this.lexiconService = lexiconService;
        this.wordService = wordService;
        this.wordReviewHistoryService = wordReviewHistoryService;

        this.initialLearningDelaySec = initialLearningDelaySec;
        this.nearMissCorrectDelaySec = nearMissCorrectDelaySec;
        this.standardIncorrectBoost = standardIncorrectBoost;
        this.nearMissBoost = nearMissBoost;
    }


    public void processEvents(String username, String lexiconId) {
        List<DBReviewEvent> allEvents = loadAllEvents(username, lexiconId);
        List<WordReviewHistory> newWordHistories = new ArrayList<>();
        List<DBScheduledReview> newScheduledReviews = new ArrayList<>();

        if (allEvents.size() > 0) {
            Language language = Language.getLanguageById(lexiconService.getLexiconMetadata(lexiconId).languageId());
            Map<String, List<DBReviewEvent>> eventsByWord = allEvents.stream().collect(Collectors.groupingBy(DBReviewEvent::wordId));

            Map<String, Word> words = wordService.loadWords(eventsByWord.keySet()).stream().collect(Collectors.toMap(word -> word.id(), word -> word));
            Map<String, WordReviewHistory> histories = wordReviewHistoryService.getWordReviewHistory(lexiconId, username, words.keySet()).stream().collect(Collectors.toMap(history -> history.wordId(), history -> history));

            eventsByWord.forEach((wordId, eventList) -> {
                Word word = words.get(wordId);

                ProcessedHistoryAndNextReview processedHistoryAndNextReview = processWordEvents(lexiconId, username, language, word, histories.get(wordId), eventsByWord.get(wordId));

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
                reviewSessionDao.createScheduledReviewsBatch(newScheduledReviews, username);
            }
            markEventsAsProcessed(allEvents);
        }
    }

    public LexiconReviewSummary getLexiconReviewSummary(String lexiconId, String username, Instant futureEventCutoff) {
        return new LexiconReviewSummary(wordService.getTotalLexiconWordCount(lexiconId),
                reviewSessionDao.getTotalLearnedWordCount(lexiconId, username),
                getFutureReviewEvents(lexiconId, username, futureEventCutoff));
    }

    private List<FutureReviewEvent> getFutureReviewEvents(String lexiconId, String username, Instant cutoff) {
        LexiconMetadata lexiconMetadata = lexiconService.getLexiconMetadata(lexiconId);
        Language language = Language.getLanguageById(lexiconMetadata.languageId());

        List<FutureReviewEvent> futureReviewEvents = new ArrayList<>();

        List<DBScheduledReview> scheduledReviews = reviewSessionDao.loadScheduledReviews(username, lexiconId, "", Optional.of(cutoff));

        Map<String, WordReviewHistory> reviewHistoryByWordId = wordReviewHistoryService.getWordReviewHistory(lexiconId, username,
                        scheduledReviews.stream().map(review -> review.wordId()).toList())
                .stream().collect(Collectors.toMap(history -> history.wordId(), history -> history));

        for(DBScheduledReview scheduledReview : scheduledReviews) {
            Instant reviewTime = scheduledReview.scheduledTestTime().isAfter(Instant.now()) ? scheduledReview.scheduledTestTime() : Instant.now();

            futureReviewEvents.add(new FutureReviewEvent(lexiconId, scheduledReview.wordId(), reviewTime, false));
            futureReviewEvents.addAll(inferFutureReviewEvents(lexiconId, language, cutoff, reviewTime, scheduledReview, reviewHistoryByWordId.get(scheduledReview.wordId())));
        }

        return futureReviewEvents;
    }

    private List<FutureReviewEvent> inferFutureReviewEvents(String lexiconId, Language language, Instant cutoff, Instant reviewTime, DBScheduledReview scheduledReview, WordReviewHistory reviewHistory) {
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

    private List<DBReviewEvent> loadAllEvents(String username, String lexiconId) {
        List<DBReviewEvent> allEvents = new ArrayList<>();

        List<DBReviewEvent> reviewEventsBatch;
        int lastId = Integer.MIN_VALUE;
        do {
            reviewEventsBatch = reviewSessionDao.loadUnprocessedReviewEventsForUserBatch(username, lexiconId, lastId, QUERY_BATCH_SIZE);

            if (reviewEventsBatch != null && reviewEventsBatch.size() > 0) {
                allEvents.addAll(reviewEventsBatch);

                lastId = reviewEventsBatch.get(reviewEventsBatch.size() - 1).eventId();
            }
        } while(reviewEventsBatch.size() == QUERY_BATCH_SIZE);

        return allEvents;
    }

    private ProcessedHistoryAndNextReview processWordEvents(String lexiconId, String username, Language language, Word word, WordReviewHistory wordHistory, List<DBReviewEvent> reviewEvents) {
        Map<ReviewType, List<DBReviewEvent>> eventsByType = reviewEvents.stream().collect(Collectors.groupingBy(DBReviewEvent::reviewType));

        if (wordHistory != null && wordHistory.learned()) {
            if (eventsByType.containsKey(ReviewType.Review)) {
                return processReviewEvents(lexiconId, username, language, word, eventsByType.get(ReviewType.Review), wordHistory);
            }
        } else if (eventsByType.containsKey(ReviewType.Learn)) {
            return processLearningEvents(lexiconId, username, language, word, eventsByType.get(ReviewType.Learn));
        }

        return null;
    }

    private ProcessedHistoryAndNextReview processReviewEvents(String lexiconId, String username, Language language, Word word, List<DBReviewEvent> reviewEvents, WordReviewHistory wordReviewHistory) {
        List<DBReviewEvent> sortedEvents = new ArrayList<>(reviewEvents);
        sortedEvents.sort(Comparator.comparing(DBReviewEvent::eventInstant));

        DBReviewEvent eventToProcess = sortedEvents.get(0);
        for(int index = 1; index < sortedEvents.size(); index++) {
            if (sortedEvents.get(index).override()) {
                eventToProcess = sortedEvents.get(index);
            }
        }

        return processReviewEvent(lexiconId, username, language, word, eventToProcess, wordReviewHistory);
    }

    private ProcessedHistoryAndNextReview processReviewEvent(String lexiconId, String username, Language language, Word word, DBReviewEvent reviewEvent, WordReviewHistory wordReviewHistory) {
        String relationshipId = getTestRelationshipId(language, reviewEvent.testOn(), reviewEvent.promptWith());

        if (reviewEvent.isCorrect()) {
            if (reviewEvent.isNearMiss()) {
                return processCorrectNearMissReviewEvent(lexiconId, username, language, word, reviewEvent, wordReviewHistory, relationshipId);
            } else {
                return processCorrectReviewEvent(lexiconId, username, language, word, reviewEvent, wordReviewHistory, relationshipId);
            }
        } else {
            return processIncorrectReviewEvent(lexiconId, username, language, word, reviewEvent, wordReviewHistory, relationshipId, reviewEvent.isNearMiss());
        }
    }

    private String getTestRelationshipId(Language language, DBReviewEvent reviewEvent) {
        return getTestRelationshipId(language, reviewEvent.testOn(), reviewEvent.promptWith());
    }

    private String getTestRelationshipId(Language language, String testOnId, String promptWithId) {
        for(TestRelationship testRelationship : language.getReviewTestRelationships()) {
            if (testRelationship.getTestOn().getId().equals(testOnId) &&
                    testRelationship.getPromptWith().getId().equals(promptWithId)) {
                return testRelationship.getId();
            }
        }

        throw new DataIntegrityViolationException("No test relation for testOn=" + testOnId + " promptWith=" + promptWithId + " in language " + language.getDisplayName());
    }

    private ProcessedHistoryAndNextReview processCorrectReviewEvent(String lexiconId, String username, Language language, Word word, DBReviewEvent reviewEvent, WordReviewHistory wordReviewHistory, String testRelationshipId) {
        Duration newTestDelay = calculateNextDelayAfterSuccessfulTest(language, wordReviewHistory);
        boolean isCurrentBoostExpired = newTestDelay.compareTo(wordReviewHistory.currentBoostExpirationDelay()) >= 0;

        WordReviewHistory newWordReviewHistory = new WordReviewHistory(
                lexiconId,
                username,
                word.id(),
                true,
                reviewEvent.eventInstant(),
                getTestRelationshipId(language, reviewEvent),
                newTestDelay,
                isCurrentBoostExpired ? 0 : wordReviewHistory.currentBoost(),
                isCurrentBoostExpired ? Duration.ZERO : wordReviewHistory.currentBoostExpirationDelay(),
                updateTestHistory(wordReviewHistory.testHistory(), testRelationshipId, true));
        DBScheduledReview newScheduledReview = buildScheduledReview(language, lexiconId, username, word, reviewEvent, newTestDelay, newWordReviewHistory);

        return new ProcessedHistoryAndNextReview(newWordReviewHistory, newScheduledReview);
    }

    private ProcessedHistoryAndNextReview processIncorrectReviewEvent(String lexiconId, String username, Language language, Word word, DBReviewEvent reviewEvent, WordReviewHistory wordReviewHistory, String testRelationshipId, boolean isNearMiss) {
        double boost = isNearMiss ? nearMissBoost : standardIncorrectBoost;

        Duration newTestDelay = Duration.ofSeconds(initialLearningDelaySec);

        WordReviewHistory newWordReviewHistory = new WordReviewHistory(
                lexiconId,
                username,
                word.id(),
                true,
                reviewEvent.eventInstant(),
                getTestRelationshipId(language, reviewEvent),
                newTestDelay,
                boost,
                wordReviewHistory.currentTestDelay(),
                updateTestHistory(wordReviewHistory.testHistory(), testRelationshipId, false));

        DBScheduledReview newScheduledReview = buildScheduledReview(language, lexiconId, username, word, reviewEvent, newTestDelay, newWordReviewHistory);

        return new ProcessedHistoryAndNextReview(newWordReviewHistory, newScheduledReview);
    }
    
    private ProcessedHistoryAndNextReview processCorrectNearMissReviewEvent(String lexiconId, String username, Language language, Word word, DBReviewEvent reviewEvent, WordReviewHistory wordReviewHistory, String testRelationshipId) {
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
                getTestRelationshipId(language, reviewEvent),
                newTestDelay,
                nearMissBoost,
                newBoostExpirationDelay,
                updateTestHistory(wordReviewHistory.testHistory(), testRelationshipId, true));

        DBScheduledReview newScheduledReview = buildScheduledReview(language, lexiconId, username, word, reviewEvent, newTestDelay, newWordReviewHistory);
        
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

    private ProcessedHistoryAndNextReview processLearningEvents(String lexiconId, String username, Language language, Word word, List<DBReviewEvent> reviewEvents) {
        List<DBReviewEvent> sortedEvents = new ArrayList<>(reviewEvents);
        sortedEvents.sort(Comparator.comparing(DBReviewEvent::eventInstant));
        DBReviewEvent eventToProcess = sortedEvents.get(sortedEvents.size() - 1);

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

        DBScheduledReview newScheduledReview = buildScheduledReview(language, lexiconId, username, word, eventToProcess, newTestDelay, newLexiconReviewHistory);

        return new ProcessedHistoryAndNextReview(newLexiconReviewHistory, newScheduledReview);
    }

    private void markEventsAsProcessed(List<DBReviewEvent> reviewEvents) {
        reviewSessionDao.markEventsAsProcessed(reviewEvents);
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

    private DBScheduledReview buildScheduledReview(Language language, String lexiconId, String username, Word word, DBReviewEvent reviewEvent, Duration newTestDelay, WordReviewHistory history) {
        return new DBScheduledReview(
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

    private record ProcessedHistoryAndNextReview(WordReviewHistory newHistory, DBScheduledReview scheduledReview) { }
}
