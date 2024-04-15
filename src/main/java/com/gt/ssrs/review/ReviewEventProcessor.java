package com.gt.ssrs.review;

import com.gt.ssrs.language.LanguageService;
import com.gt.ssrs.lexicon.LexiconDao;
import com.gt.ssrs.review.model.DBLexiconReviewHistory;
import com.gt.ssrs.review.model.DBReviewEvent;
import com.gt.ssrs.review.model.DBScheduledReview;
import com.gt.ssrs.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class ReviewEventProcessor {

    private static final Logger log = LoggerFactory.getLogger(ReviewEventProcessor.class);

    static final int QUERY_BATCH_SIZE = 1000;

    private final ReviewSessionDao reviewSessionDao;
    private final LexiconDao lexiconDao;
    private final LanguageService languageService;
    private final int initialLearningDelaySec;
    private final int nearMissCorrectDelaySec;
    private final double standardIncorrectBoost;
    private final double nearMissBoost;

    @Autowired
    public ReviewEventProcessor(ReviewSessionDao reviewSessionDao,
                                LexiconDao lexiconDao,
                                LanguageService languageService,
                                @Value("${ssrs.learning.initialLearningDelaySec}") int initialLearningDelaySec,
                                @Value("${ssrs.learning.nearMissCorrectLearningDelaySec}") int nearMissCorrectDelaySec,
                                @Value("${ssrs.learning.standardIncorrectBoost}") double standardIncorrectBoost,
                                @Value("${ssrs.learning.nearMissBoost}") double nearMissBoost) {
        this.reviewSessionDao = reviewSessionDao;
        this.lexiconDao = lexiconDao;
        this.languageService = languageService;

        this.initialLearningDelaySec = initialLearningDelaySec;
        this.nearMissCorrectDelaySec = nearMissCorrectDelaySec;
        this.standardIncorrectBoost = standardIncorrectBoost;
        this.nearMissBoost = nearMissBoost;
    }


    public void processEvents(String username, String lexiconId) {
        List<DBReviewEvent> allEvents = loadAllEvents(username, lexiconId);
        List<DBLexiconReviewHistory> newWordHistories = new ArrayList<>();
        List<DBScheduledReview> newScheduledReviews = new ArrayList<>();

        if (allEvents.size() > 0) {
            Language language = languageService.GetLanguageById(lexiconDao.getLexiconMetadata(lexiconId).languageId());
            Map<String, List<DBReviewEvent>> eventsByWord = allEvents.stream().collect(Collectors.groupingBy(DBReviewEvent::wordId));

            Map<String, Word> words = lexiconDao.loadWords(eventsByWord.keySet()).stream().collect(Collectors.toMap(word -> word.id(), word -> word));
            Map<String, DBLexiconReviewHistory> histories = reviewSessionDao.getLexiconReviewHistoryBatch(lexiconId, username, words.keySet()).stream().collect(Collectors.toMap(history -> history.wordId(), history -> history));


            eventsByWord.forEach((wordId, eventList) -> {
                Word word = words.get(wordId);

                ProcessedHistoryAndNextReview processedHistoryAndNextReview = processWordEvents(lexiconId, language, word, histories.get(wordId), eventsByWord.get(wordId));
                if (processedHistoryAndNextReview != null) {
                    if (processedHistoryAndNextReview.newHistory != null) {
                        newWordHistories.add(processedHistoryAndNextReview.newHistory);
                    }
                    if (processedHistoryAndNextReview.scheduledReview != null) {
                        newScheduledReviews.add(processedHistoryAndNextReview.scheduledReview);
                    }
                }
            });

            long daoStart = Instant.now().toEpochMilli();
            if (newWordHistories.size() > 0) {
                reviewSessionDao.updateLexiconReviewHistoryBatch(username, newWordHistories);
            }
            if (newScheduledReviews.size() > 0) {
                reviewSessionDao.createScheduledReviewsBatch(newScheduledReviews);
            }
            markEventsAsProcessed(allEvents);
        }
    }

    public LexiconReviewSummary getLexiconReviewSummary(String lexiconId, String username, Instant futureEventCutoff) {
        return new LexiconReviewSummary(lexiconDao.getTotalLexiconWordCount(lexiconId),
                reviewSessionDao.getTotalLearnedWordCount(lexiconId, username),
                getFutureReviewEvents(lexiconId, username, futureEventCutoff));
    }

    private List<FutureReviewEvent> getFutureReviewEvents(String lexiconId, String username, Instant cutoff) {
        Lexicon lexiconMetadata = lexiconDao.getLexiconMetadata(lexiconId);
        Language language = languageService.GetLanguageById(lexiconMetadata.languageId());

        List<FutureReviewEvent> futureReviewEvents = new ArrayList<>();

        List<DBScheduledReview> scheduledReviews = reviewSessionDao.loadScheduledReviews(lexiconId, "", Optional.of(cutoff));

        Map<String, DBLexiconReviewHistory> reviewHistoryByWordId = reviewSessionDao.getLexiconReviewHistoryBatch(lexiconId, username,
                        scheduledReviews.stream().map(review -> review.wordId()).toList())
                .stream().collect(Collectors.toMap(history -> history.wordId(), history -> history));

        for(DBScheduledReview scheduledReview : scheduledReviews) {
            Instant reviewTime = scheduledReview.scheduledTestTime().isAfter(Instant.now()) ? scheduledReview.scheduledTestTime() : Instant.now();

            futureReviewEvents.add(new FutureReviewEvent(lexiconId, scheduledReview.wordId(), reviewTime, false));
            futureReviewEvents.addAll(inferFutureReviewEvents(lexiconId, language, cutoff, reviewTime, scheduledReview, reviewHistoryByWordId.get(scheduledReview.wordId())));
        }

        return futureReviewEvents;
    }

    private List<FutureReviewEvent> inferFutureReviewEvents(String lexiconId, Language language, Instant cutoff, Instant reviewTime, DBScheduledReview scheduledReview, DBLexiconReviewHistory reviewHistory) {
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

    private ProcessedHistoryAndNextReview processWordEvents(String lexiconId, Language language, Word word, DBLexiconReviewHistory wordHistory, List<DBReviewEvent> reviewEvents) {
        Map<ReviewType, List<DBReviewEvent>> eventsByType = reviewEvents.stream().collect(Collectors.groupingBy(DBReviewEvent::reviewType));

        if (wordHistory != null && wordHistory.learned()) {
            if (eventsByType.containsKey(ReviewType.Review)) {
                return processReviewEvents(lexiconId, language, word, eventsByType.get(ReviewType.Review), wordHistory);
            }
        } else if (eventsByType.containsKey(ReviewType.Learn)) {
            return processLearningEvents(lexiconId, language, word, eventsByType.get(ReviewType.Learn));
        }

        return null;
    }

    private ProcessedHistoryAndNextReview processReviewEvents(String lexiconId, Language language, Word word, List<DBReviewEvent> reviewEvents, DBLexiconReviewHistory lexiconReviewHistory) {
        List<DBReviewEvent> sortedEvents = new ArrayList<>(reviewEvents);
        sortedEvents.sort(Comparator.comparing(DBReviewEvent::eventInstant));

        DBReviewEvent eventToProcess = sortedEvents.get(0);
        for(int index = 1; index < sortedEvents.size(); index++) {
            if (sortedEvents.get(index).override()) {
                eventToProcess = sortedEvents.get(index);
            }
        }

        return processReviewEvent(lexiconId, language, word, eventToProcess, lexiconReviewHistory);
    }

    private ProcessedHistoryAndNextReview processReviewEvent(String lexiconId, Language language, Word word, DBReviewEvent reviewEvent, DBLexiconReviewHistory lexiconReviewHistory) {
        String relationshipId = getTestRelationshipId(language, reviewEvent.testOn(), reviewEvent.promptWith());

        if (reviewEvent.isCorrect()) {
            if (reviewEvent.isNearMiss()) {
                return processCorrectNearMissReviewEvent(lexiconId, language, word, reviewEvent, lexiconReviewHistory, relationshipId);
            } else {
                return processCorrectReviewEvent(lexiconId, language, word, reviewEvent, lexiconReviewHistory, relationshipId);
            }
        } else {
            return processIncorrectReviewEvent(lexiconId, language, word, reviewEvent, lexiconReviewHistory, relationshipId, reviewEvent.isNearMiss());
        }
    }

    private String getTestRelationshipId(Language language, String testOn, String promptWith) {
        for(TestRelationship testRelationship : language.testRelationships()) {
            if (testRelationship.testOn().equals(testOn) &&
                    testRelationship.promptWith().equals(promptWith)) {
                return testRelationship.id();
            }
        }

        return null;
    }

    private ProcessedHistoryAndNextReview processCorrectReviewEvent(String lexiconId, Language language, Word word, DBReviewEvent reviewEvent, DBLexiconReviewHistory lexiconReviewHistory, String testRelationshipId) {
        Duration newTestDelay = calculateNextDelayAfterSuccessfulTest(language, lexiconReviewHistory);
        boolean isCurrentBoostExpired = newTestDelay.compareTo(lexiconReviewHistory.currentBoostExpirationDelay()) >= 0;

        DBLexiconReviewHistory newLexiconReviewHistory = new DBLexiconReviewHistory(
                lexiconId,
                word.id(),
                true,
                reviewEvent.eventInstant(),
                newTestDelay,
                isCurrentBoostExpired ? 0 : lexiconReviewHistory.currentBoost(),
                isCurrentBoostExpired ? null : lexiconReviewHistory.currentBoostExpirationDelay(),
                updateTestHistory(lexiconReviewHistory.testHistory(), testRelationshipId, true));
        DBScheduledReview newScheduledReview = buildScheduledReview(language, lexiconId, word, reviewEvent, newTestDelay, newLexiconReviewHistory);

        return new ProcessedHistoryAndNextReview(newLexiconReviewHistory, newScheduledReview);
    }

    private ProcessedHistoryAndNextReview processIncorrectReviewEvent(String lexiconId, Language language, Word word, DBReviewEvent reviewEvent, DBLexiconReviewHistory lexiconReviewHistory, String testRelationshipId, boolean isNearMiss) {
        double boost = isNearMiss ? nearMissBoost : standardIncorrectBoost;

        Duration newTestDelay = Duration.ofSeconds(initialLearningDelaySec);

        DBLexiconReviewHistory newLexiconReviewHistory = new DBLexiconReviewHistory(
                lexiconId,
                word.id(),
                true,
                reviewEvent.eventInstant(),
                newTestDelay,
                boost,
                lexiconReviewHistory.currentTestDelay(),
                updateTestHistory(lexiconReviewHistory.testHistory(), testRelationshipId, false));

        DBScheduledReview newScheduledReview = buildScheduledReview(language, lexiconId, word, reviewEvent, newTestDelay, newLexiconReviewHistory);

        return new ProcessedHistoryAndNextReview(newLexiconReviewHistory, newScheduledReview);
    }
    
    private ProcessedHistoryAndNextReview processCorrectNearMissReviewEvent(String lexiconId, Language language, Word word, DBReviewEvent reviewEvent, DBLexiconReviewHistory lexiconReviewHistory, String testRelationshipId) {
        if (lexiconReviewHistory.currentTestDelay().getSeconds() < nearMissCorrectDelaySec) {
            return processCorrectReviewEvent(lexiconId, language, word, reviewEvent, lexiconReviewHistory, testRelationshipId);
        }

        Duration newTestDelay = Duration.ofSeconds(nearMissCorrectDelaySec);
        Duration newBoostExpirationDelay = lexiconReviewHistory.currentBoostExpirationDelay().compareTo(lexiconReviewHistory.currentTestDelay()) > 0
                ? lexiconReviewHistory.currentBoostExpirationDelay()
                : lexiconReviewHistory.currentTestDelay();

        DBLexiconReviewHistory newLexiconReviewHistory = new DBLexiconReviewHistory(
                lexiconId,
                word.id(),
                true,
                reviewEvent.eventInstant(),
                newTestDelay,
                nearMissBoost,
                newBoostExpirationDelay,
                updateTestHistory(lexiconReviewHistory.testHistory(), testRelationshipId, true));

        DBScheduledReview newScheduledReview = buildScheduledReview(language, lexiconId, word, reviewEvent, newTestDelay, newLexiconReviewHistory);
        
        return new ProcessedHistoryAndNextReview(newLexiconReviewHistory, newScheduledReview);
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

    private ProcessedHistoryAndNextReview processLearningEvents(String lexiconId, Language language, Word word, List<DBReviewEvent> reviewEvents) {
        List<DBReviewEvent> sortedEvents = new ArrayList<>(reviewEvents);
        sortedEvents.sort(Comparator.comparing(DBReviewEvent::eventInstant));
        DBReviewEvent eventToProcess = sortedEvents.get(sortedEvents.size() - 1);

        Duration newTestDelay = Duration.ofSeconds(initialLearningDelaySec);

        DBLexiconReviewHistory newLexiconReviewHistory = new DBLexiconReviewHistory(
                lexiconId,
                word.id(),
                true,
                eventToProcess.eventInstant(),
                newTestDelay,
                0,
                null,
                new HashMap<>());

        DBScheduledReview newScheduledReview = buildScheduledReview(language, lexiconId, word, eventToProcess, newTestDelay, newLexiconReviewHistory);

        return new ProcessedHistoryAndNextReview(newLexiconReviewHistory, newScheduledReview);
    }

    private void markEventsAsProcessed(List<DBReviewEvent> reviewEvents) {
        reviewSessionDao.markEventsAsProcessed(reviewEvents);
    }

    private Duration calculateNextDelayAfterSuccessfulTest(Language language, DBLexiconReviewHistory lexiconReviewHistory) {
        return calculateNextDelayAfterSuccessfulTest(language, lexiconReviewHistory.currentTestDelay(), lexiconReviewHistory.currentBoost(), lexiconReviewHistory.currentBoostExpirationDelay());
    }

    private Duration calculateNextDelayAfterSuccessfulTest(Language language, Duration currentTestDelay, double currentBoost, Duration currentBoostExpirationDelay) {
        double testsToDoubleDelay = language.testsToDouble() <= 0 ? 1 : language.testsToDouble();  // guard against 0 since it would cause a divide-by-zero error
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

    private DBScheduledReview buildScheduledReview(Language language, String lexiconId, Word word, DBReviewEvent reviewEvent, Duration newTestDelay, DBLexiconReviewHistory history) {
        return new DBScheduledReview(
                UUID.randomUUID().toString(),
                lexiconId,
                word.id(),
                ReviewType.Review,
                getNextTestRelationship(language, word, history, reviewEvent),
                reviewEvent.eventInstant().plus(newTestDelay),
                newTestDelay,
                false);
    }

    private String getNextTestRelationship(Language language, Word word, DBLexiconReviewHistory history, DBReviewEvent lastEvent) {
        TestRelationship selectedRelationship = null;
        int minStreak = Integer.MAX_VALUE;

        for(TestRelationship relationship : language.testRelationships()) {
            if (relationship.isReviewRelationship()) {

                if (word.elements().containsKey(relationship.testOn())
                        && word.elements().containsKey(relationship.promptWith())
                        && (lastEvent.reviewType() == ReviewType.Learn || !relationship.testOn().equals(lastEvent.testOn()) || !relationship.promptWith().equals(lastEvent.promptWith()))) {

                    int streak = history.testHistory().containsKey(relationship.id()) ? history.testHistory().get(relationship.id()).correctStreak() : 0;
                    if (streak < minStreak) {
                        selectedRelationship = relationship;
                        minStreak = streak;
                    }
                }
            }
        }

        if (selectedRelationship == null) {
            return getFirstReviewRelationship(language).id();
        }

        return selectedRelationship.id();
    }

    private TestRelationship getFirstReviewRelationship(Language language) {
        for(TestRelationship relationship : language.testRelationships()) {
            if (relationship.isReviewRelationship()) {
                return relationship;
            }
        }

        // Should be unreachable
        return language.testRelationships().get(0);
    }

    private static record ProcessedHistoryAndNextReview(DBLexiconReviewHistory newHistory, DBScheduledReview scheduledReview) { }


}
