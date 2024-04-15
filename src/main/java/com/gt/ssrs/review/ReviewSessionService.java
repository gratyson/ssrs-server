package com.gt.ssrs.review;

import com.gt.ssrs.language.LanguageService;
import com.gt.ssrs.lexicon.LexiconService;
import com.gt.ssrs.review.model.DBLexiconReviewHistory;
import com.gt.ssrs.review.model.DBReviewEvent;
import com.gt.ssrs.review.model.DBScheduledReview;
import com.gt.ssrs.exception.UserAccessException;
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
public class ReviewSessionService {

    private static final Logger log = LoggerFactory.getLogger(ReviewSessionService.class);

    private static final int MAX_REVIEW_SIZE = 999;
    private static final int MAX_FALLBACK_ATTEMPTS = 3;

    private final ReviewSessionDao reviewSessionDao;
    private final LexiconService lexiconService;
    private final WordReviewHelper wordReviewHelper;
    private final LanguageService languageService;
    private final double futureEventAllowedRatio;

    @Autowired
    public ReviewSessionService(ReviewSessionDao reviewSessionDao,
                                LexiconService lexiconService,
                                WordReviewHelper wordReviewHelper,
                                LanguageService languageService,
                                @Value("${ssrs.review.futureEventAllowedRatio}") double futureEventAllowedRatio) {
        this.reviewSessionDao = reviewSessionDao;
        this.lexiconService = lexiconService;
        this.wordReviewHelper = wordReviewHelper;
        this.languageService = languageService;

        this.futureEventAllowedRatio = futureEventAllowedRatio;
    }

    public void saveReviewEvent(ReviewEvent event, String username, Instant eventInstant) {
        reviewSessionDao.saveReviewEvent(DBReviewEvent.fromReviewEvent(event, username, eventInstant), event.scheduledEventId());
    }

    public List<LexiconReviewHistory> getLexiconHistoryBatch(String lexiconId, String username, Collection<String> wordIds) {
        if (wordIds.isEmpty()) {
            return List.of();
        } else {
            Map<String, List<DBScheduledReview>> dbScheduledReviewsByWordId =
                    loadScheduledReviewsForWords(lexiconId, wordIds)
                            .stream()
                            .filter(scheduledReview -> scheduledReview.reviewType() == ReviewType.Review)
                            .sorted(Comparator.comparing(DBScheduledReview::scheduledTestTime))
                            .collect(Collectors.groupingBy(DBScheduledReview::wordId));

            List<LexiconReviewHistory> lexiconWordHistories = new ArrayList<>();
            for(DBLexiconReviewHistory dbLexiconReviewHistory : reviewSessionDao.getLexiconReviewHistoryBatch(lexiconId, username, wordIds)) {
                DBScheduledReview nextReview = null;
                if (dbScheduledReviewsByWordId.containsKey(dbLexiconReviewHistory.wordId())) {
                    nextReview = dbScheduledReviewsByWordId.get(dbLexiconReviewHistory.wordId()).get(0);
                }

                lexiconWordHistories.add(buildLexiconWordHistory(dbLexiconReviewHistory, nextReview));
            }

            return lexiconWordHistories;
        }
    }

    public int saveLexiconHistoryBatch(List<LexiconReviewHistory> lexiconReviewHistories, String username) {
        log.info("Saving new review history for {} words", lexiconReviewHistories.size());

        Map<String, List<LexiconReviewHistory>> wordHistoriesToSaveByLexiconId =
                lexiconReviewHistories.stream().collect(Collectors.groupingBy(lexiconReviewHistory -> lexiconReviewHistory.lexiconId()));

        int rowsUpdated = reviewSessionDao.updateLexiconReviewHistoryBatch(username, lexiconReviewHistories.stream().map(lexiconReviewHistory -> buildDBLexiconWordHistory(lexiconReviewHistory)).toList());
        for(Map.Entry<String, List<LexiconReviewHistory>> lexiconWordHistoryEntry : wordHistoriesToSaveByLexiconId.entrySet()) {
            createOrUpdateScheduledReviewBatch(lexiconWordHistoryEntry.getKey(), lexiconWordHistoryEntry.getValue());
        }

        return rowsUpdated;
    }

    public void deleteLexiconHistoryBatch(String lexiconId, Collection<String> wordIds, String username) {
        verifyUserAccessAllowed(lexiconId, username);

        reviewSessionDao.deleteLexiconHistoryBatch(lexiconId, wordIds);
    }

    public void adjustNextReviewTimes(String lexiconId, Duration adjustment, String username) {
        verifyUserAccessAllowed(lexiconId, username);

        reviewSessionDao.adjustNextReviewTimes(lexiconId, adjustment);
    }

    private LexiconReviewHistory buildLexiconWordHistory(DBLexiconReviewHistory dbLexiconReviewHistory, DBScheduledReview scheduledReview) {
        String nextTestRelationshipId = scheduledReview == null ? null : scheduledReview.testRelationshipId();
        Instant nextScheduledTime = scheduledReview == null ? null : scheduledReview.scheduledTestTime();

        return new LexiconReviewHistory(dbLexiconReviewHistory.lexiconId(), dbLexiconReviewHistory.wordId(), dbLexiconReviewHistory.learned(),
                dbLexiconReviewHistory.mostRecentTestTime(), nextTestRelationshipId, dbLexiconReviewHistory.currentTestDelay(),
                nextScheduledTime, dbLexiconReviewHistory.currentBoost(), dbLexiconReviewHistory.currentBoostExpirationDelay(),
                dbLexiconReviewHistory.testHistory());
    }

    private DBLexiconReviewHistory buildDBLexiconWordHistory(LexiconReviewHistory lexiconReviewHistory) {
        return new DBLexiconReviewHistory(lexiconReviewHistory.lexiconId(), lexiconReviewHistory.wordId(), lexiconReviewHistory.learned(),
                lexiconReviewHistory.mostRecentTestTime(), lexiconReviewHistory.currentTestDelay(), lexiconReviewHistory.currentBoost(),
                lexiconReviewHistory.currentBoostExpirationDelay(), lexiconReviewHistory.testHistory());
    }


    public List<DBScheduledReview> loadScheduledReviewsForWords(String lexiconId, Collection<String> wordIds) {
        return reviewSessionDao.loadScheduledReviewsForWords(lexiconId, wordIds);
    }


    public void createOrUpdateScheduledReviewBatch(String lexiconId, List<LexiconReviewHistory> lexiconWordHistories) {
        List<String> wordHistoryWordIds = lexiconWordHistories.stream().map(LexiconReviewHistory::wordId).toList();
        Map<String, List<DBScheduledReview>> existingScheduledReviews =
                loadScheduledReviewsForWords(lexiconId, wordHistoryWordIds)
                        .stream()
                        .collect(Collectors.groupingBy(DBScheduledReview::wordId));

        List<DBScheduledReview> reviewsToSave = new ArrayList<>();
        for(LexiconReviewHistory wordHistory : lexiconWordHistories) {
            String idToUse = getReviewId(existingScheduledReviews.computeIfAbsent(wordHistory.wordId(), (wordId) -> List.of()));
            reviewsToSave.add(buildScheduledReviewFromHistory(idToUse, wordHistory));
        }

        reviewSessionDao.createScheduledReviewsBatch(reviewsToSave);
    }

    private DBScheduledReview buildScheduledReviewFromHistory(String id, LexiconReviewHistory wordHistory) {
        return new DBScheduledReview(id, wordHistory.lexiconId(), wordHistory.wordId(), ReviewType.Review, wordHistory.nextTestRelationId(),
                wordHistory.nextTestTime(), Duration.between(wordHistory.mostRecentTestTime(), wordHistory.nextTestTime()), false);
    }

    private String getReviewId(List<DBScheduledReview> scheduledReviews) {
        for (DBScheduledReview scheduledReview : scheduledReviews) {
            if (!scheduledReview.completed() && scheduledReview.reviewType() == ReviewType.Review) {
                return scheduledReview.id();
            }
        }

        return UUID.randomUUID().toString();
    }

    public List<List<WordReview>> generateLearningSession(String lexiconId, int wordCnt, String username) {
        Lexicon lexiconMetadata = lexiconService.getLexiconMetadata(lexiconId);
        Language language = languageService.GetLanguageById(lexiconMetadata.languageId());
        Map<String, TestRelationship> relationshipMap = getTestRelationshipMap(language, false);

        List<Word> wordsToLearn = wordReviewHelper.getWordsToLearn(lexiconId, username, wordCnt);
        if (wordsToLearn == null || wordsToLearn.size() == 0) {
            return List.of();
        }

        List<LanguageSequenceValue> learningSequence = languageService.getLanguageSequence(lexiconMetadata.languageId(), ReviewType.Learn);

        List<List<WordReview>> wordReviewQueues = new ArrayList<>();
        for(Word word : wordsToLearn) {
            List<WordReview> queue = new ArrayList<>();
            Map<String, List<String>> similarWordsByElement = new HashMap<>();

            for(int sequenceIndex = 0; sequenceIndex < learningSequence.size(); sequenceIndex++) {
                LanguageSequenceValue sequenceValue = learningSequence.get(sequenceIndex);
                TestRelationship reviewRelationship = getReviewRelationship(word, sequenceValue.relationshipId(), relationshipMap);

                // Skip if the sequence specified a relationship, but word does not contain the required elements in the relationship or any fallbacks
                if (reviewRelationship != null || sequenceValue.relationshipId() == null) {
                    queue.add(buildWordReviewForLearningSession(language, lexiconId, word, sequenceValue, reviewRelationship, similarWordsByElement));
                }
            }

            wordReviewQueues.add(queue);
        }

        return wordReviewQueues;
    }

    private WordReview buildWordReviewForLearningSession(Language language, String lexiconId, Word word, LanguageSequenceValue sequenceValue, TestRelationship relationship, Map<String, List<String>> similarWordsByElement) {
        List<String> similarWords = relationship.testOn() == null ? List.of()
                : similarWordsByElement.computeIfAbsent(relationship.testOn(), element -> wordReviewHelper.getSimilarWordElementValues(lexiconId, word, relationship.testOn()));

        return buildWordReview(language, word, sequenceValue, relationship, similarWords);
    }

    private Map<String, TestRelationship> getTestRelationshipMap(Language language, boolean reviewRelationsOnly) {
        return language.testRelationships()
                .stream()
                .filter(relation -> relation.isReviewRelationship() || !reviewRelationsOnly)
                .collect(Collectors.toMap(relation -> relation.id(), relation -> relation));
    }

    private TestRelationship getReviewRelationship(Language language, Word word, String relationshipId) {
        return getReviewRelationship(word, relationshipId, getTestRelationshipMap(language, false));
    }
    private TestRelationship getReviewRelationship(Word word, String relationshipId, Map<String, TestRelationship> languageRelationships) {
        if (relationshipId == null) {
            return TestRelationship.EMPTY_TEST_RELATIONSHIP;
        }

        TestRelationship relationship = languageRelationships.get(relationshipId);
        int fallbackAttemptCnt = 0;

        while(++fallbackAttemptCnt <= MAX_FALLBACK_ATTEMPTS && relationship != null && (!word.elements().containsKey(relationship.testOn()) || !word.elements().containsKey(relationship.promptWith()))) {
            String curRelationshipId = relationship.fallbackId();
            relationship = curRelationshipId == null ? null : languageRelationships.get(curRelationshipId);
        }

        if (fallbackAttemptCnt == MAX_FALLBACK_ATTEMPTS) {
            log.error("Exceeded max relationship fallback attempts. Aborting to avoid infinite loop. Relationship configuration likely has circular links.");
            return null;
        }

        return relationship;
    }


    public List<WordReview> generateReviewSession(String lexiconId, Optional<String> reviewRelationShip, Optional<Instant> cutoffInstant, int maxWordCnt, String username) {
        Lexicon lexiconMetadata = lexiconService.getLexiconMetadata(lexiconId);
        Language language = languageService.GetLanguageById(lexiconMetadata.languageId());

        if(maxWordCnt <= 0 || maxWordCnt > MAX_REVIEW_SIZE) {
            maxWordCnt = MAX_REVIEW_SIZE;
        }

        List<ScheduledWordReview> scheduledReviewWords = getWordsToReview(lexiconId, reviewRelationShip, cutoffInstant);
        if (scheduledReviewWords == null || scheduledReviewWords.size() == 0) {
            return List.of();
        }

        if (scheduledReviewWords.size() > maxWordCnt) {
            scheduledReviewWords = scheduledReviewWords.subList(0, maxWordCnt);
        }

        List<String> scheduledWordIds = scheduledReviewWords.stream().map(scheduledWordReview -> scheduledWordReview.wordId()).toList();
        Map<String, Word> scheduledWordMap = lexiconService.loadWords(scheduledWordIds).stream().collect(Collectors.toMap(word -> word.id(), word -> word));
        return scheduledReviewWords.stream().map(scheduledReview -> buildWordReview(language, lexiconId, scheduledReview, scheduledWordMap.get(scheduledReview.wordId()))).toList();
    }

    public Map<String, Integer> getScheduledReviewCounts(String username, String lexiconId, Optional<Instant> cutoffInstant) {
        Map<String, Integer> scheduledReviewCounts = new HashMap<>();

        for (DBScheduledReview scheduledReview : getCurrentScheduledReviewForLexicon(lexiconId, Optional.empty(), cutoffInstant)) {
            scheduledReviewCounts.put(scheduledReview.testRelationshipId(), scheduledReviewCounts.getOrDefault(scheduledReview.testRelationshipId(), 0) + 1);
        }

        return scheduledReviewCounts;
    }

    private List<DBScheduledReview> getCurrentScheduledReviewForLexicon(String lexiconId, Optional<String> reviewRelationship, Optional<Instant> cutoffInstant) {
        Instant now = Instant.now();

        List<DBScheduledReview> scheduledReviews = reviewSessionDao.loadScheduledReviews(lexiconId, reviewRelationship.orElse(""), cutoffInstant);

        if (cutoffInstant.isPresent() && cutoffInstant.get().isAfter(now)) {
            scheduledReviews = scheduledReviews.stream().filter(scheduledReview -> isFutureEventAllowed(scheduledReview, now)).toList();
        }

        return scheduledReviews;
    }

    private boolean isFutureEventAllowed(DBScheduledReview dbScheduledReview, Instant now) {
        return (dbScheduledReview.scheduledTestTime().toEpochMilli() - now.toEpochMilli()) < (dbScheduledReview.testDelay().toMillis() * futureEventAllowedRatio);
    }

    private List<ScheduledWordReview> getWordsToReview(String lexiconId, Optional<String> reviewRelationship, Optional<Instant> cutoffInstant) {
        List<DBScheduledReview> scheduledReviews = getCurrentScheduledReviewForLexicon(lexiconId, reviewRelationship, cutoffInstant);

        return scheduledReviews.stream().map(dbScheduledReview -> new ScheduledWordReview(dbScheduledReview.id(), dbScheduledReview.wordId(), dbScheduledReview.testRelationshipId(), ReviewType.Review)).toList();
    }

    private WordReview buildWordReview(Language language, String lexiconId, ScheduledWordReview scheduledReview, Word word) {
        TestRelationship reviewRelationship = getReviewRelationship(language, word, scheduledReview.reviewRelationShip());

        return new WordReview(
                language.id(),
                word,
                scheduledReview.reviewId(),
                reviewRelationship.testOn(),
                reviewRelationship.promptWith(),
                reviewRelationship.showAfterTest(),
                ReviewMode.TypingTest,
                scheduledReview.reviewType(),
                true,
                wordReviewHelper.getWordAllowedTime(language, word, ReviewMode.TypingTest, reviewRelationship.testOn()),
                wordReviewHelper.getSimilarCharacterSelection(word, reviewRelationship.testOn(), wordReviewHelper.getSimilarWordElementValues(lexiconId, word, reviewRelationship.testOn())),
                List.of());
    }

    private WordReview buildWordReview(Language language, Word word, LanguageSequenceValue sequenceValue, TestRelationship relationship, List<String> similarWords) {
        return new WordReview(language.id(),
                word,
                null,
                relationship.testOn(),
                relationship.promptWith(),
                relationship.showAfterTest(),
                sequenceValue.reviewMode(),
                ReviewType.Learn,
                sequenceValue.recordEvent(),
                wordReviewHelper.getWordAllowedTime(language, word, sequenceValue.reviewMode(), relationship.testOn()),
                sequenceValue.reviewMode() == ReviewMode.TypingTest || sequenceValue.reviewMode() == ReviewMode.WordOverviewWithTyping ? wordReviewHelper.getSimilarCharacterSelection(word, relationship.testOn(), similarWords) : List.of(),
                sequenceValue.reviewMode() == ReviewMode.MultipleChoiceTest ? wordReviewHelper.getSimilarWordSelection(word, relationship.testOn(), sequenceValue.optionCount(), similarWords) : List.of());
    }

    private TestRelationship getLearningReviewRelationship(Language language, ScheduledWordReview scheduledReview) {
        TestRelationship scheduledReviewRelationship = null;
        for(TestRelationship reviewRelationship : language.testRelationships()) {
            if (reviewRelationship.id().equals(scheduledReview.reviewRelationShip())) {
                scheduledReviewRelationship = reviewRelationship;
                break;
            }
        }

        if (scheduledReviewRelationship == null) {
            log.error("Invalid relationship {} on review {}. Defaulting to first valid relationship.");
            scheduledReviewRelationship = language.testRelationships().get(0);
        }

        return scheduledReviewRelationship;
    }

    private void verifyUserAccessAllowed(String lexiconId, String username) {
        Lexicon lexiconMetadata = lexiconService.getLexiconMetadata(lexiconId);

        if (lexiconMetadata == null) {
            throw new IllegalArgumentException("Lexicon " + lexiconId + " does not exist");
        }

        verifyUserAccessAllowed(lexiconMetadata, username);
    }

    private void verifyUserAccessAllowed(Lexicon lexiconMetadata, String username) {
        if (!lexiconMetadata.owner().equals(username)) {
            String errMsg = "User " + username + " does not have access to review lexicon " + lexiconMetadata.id();

            log.error(errMsg);
            throw new UserAccessException(errMsg);
        }
    }
}
