package com.gt.ssrs.review;

import com.gt.ssrs.language.Language;
import com.gt.ssrs.language.LearningTestOptions;
import com.gt.ssrs.language.TestRelationship;
import com.gt.ssrs.language.WordElement;
import com.gt.ssrs.lexicon.LexiconService;
import com.gt.ssrs.lexicon.model.TestOnWordPair;
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
    private final double futureEventAllowedRatio;

    @Autowired
    public ReviewSessionService(ReviewSessionDao reviewSessionDao,
                                LexiconService lexiconService,
                                WordReviewHelper wordReviewHelper,
                                @Value("${ssrs.review.futureEventAllowedRatio}") double futureEventAllowedRatio) {
        this.reviewSessionDao = reviewSessionDao;
        this.lexiconService = lexiconService;
        this.wordReviewHelper = wordReviewHelper;

        this.futureEventAllowedRatio = futureEventAllowedRatio;
    }

    public void saveReviewEvent(ReviewEvent event, String username, Instant eventInstant) {
        reviewSessionDao.saveReviewEvent(DBReviewEvent.fromReviewEvent(event, username, eventInstant), event.scheduledEventId());
    }

    public void recordManualEvent(ReviewEvent event, String username) {
        log.warn(reviewSessionDao.loadScheduledReviewsForWords(username, event.lexiconId(), List.of(event.wordId())).toString());

        Optional<DBScheduledReview> scheduledReviewForWord = reviewSessionDao.loadScheduledReviewsForWords(username, event.lexiconId(), List.of(event.wordId()))
                .stream()
                .filter(scheduledReview -> scheduledReview.reviewType().equals(ReviewType.Review) && !scheduledReview.completed())
                .sorted(Comparator.comparing(DBScheduledReview::scheduledTestTime))
                .findFirst();

        if (scheduledReviewForWord.isEmpty()) {
            log.warn("Manual event requested for word " + event.wordId() + ". However, no scheduled events exist for word.");
            return;
        }

        TestRelationship scheduledTestRelationship = getTestRelationFromId(event.lexiconId(), scheduledReviewForWord.get().testRelationshipId());
        if (scheduledTestRelationship == null) {
            log.warn("Unknown relation scheduled for test " + scheduledReviewForWord.get().id());
            return;
        }

        ReviewEvent reviewEventToSave = new ReviewEvent(
                scheduledReviewForWord.get().id(),
                event.lexiconId(),
                event.wordId(),
                event.reviewType(),
                event.reviewMode(),
                scheduledTestRelationship.getId(),
                event.isCorrect(),
                event.isNearMiss(),
                0,
                false);

        saveReviewEvent(reviewEventToSave, username, Instant.now());
    }

    private TestRelationship getTestRelationFromId(String lexiconId, String relationshipId) {
        Lexicon lexicon = lexiconService.getLexiconMetadata(lexiconId);
        Language language = Language.getLanguageById(lexicon.languageId());

        for(TestRelationship testRelationship : language.getReviewTestRelationships()) {
            if (testRelationship.getId().equals(relationshipId)) {
                return testRelationship;
            }
        }

        return null;
    }

    public List<LexiconReviewHistory> getLexiconHistoryBatch(String lexiconId, String username, Collection<String> wordIds) {
        if (wordIds.isEmpty()) {
            return List.of();
        } else {
            Map<String, List<DBScheduledReview>> dbScheduledReviewsByWordId =
                    loadScheduledReviewsForWords(lexiconId, username, wordIds)
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
            createOrUpdateScheduledReviewBatch(lexiconWordHistoryEntry.getKey(), username, lexiconWordHistoryEntry.getValue());
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


    public List<DBScheduledReview> loadScheduledReviewsForWords(String lexiconId, String username, Collection<String> wordIds) {
        return reviewSessionDao.loadScheduledReviewsForWords(username, lexiconId, wordIds);
    }


    public void createOrUpdateScheduledReviewBatch(String lexiconId, String username, List<LexiconReviewHistory> lexiconWordHistories) {
        List<String> wordHistoryWordIds = lexiconWordHistories.stream().map(LexiconReviewHistory::wordId).toList();
        Map<String, List<DBScheduledReview>> existingScheduledReviews =
                loadScheduledReviewsForWords(lexiconId, username, wordHistoryWordIds)
                        .stream()
                        .collect(Collectors.groupingBy(DBScheduledReview::wordId));

        List<DBScheduledReview> reviewsToSave = new ArrayList<>();
        for(LexiconReviewHistory wordHistory : lexiconWordHistories) {
            String idToUse = getReviewId(existingScheduledReviews.computeIfAbsent(wordHistory.wordId(), (wordId) -> List.of()));
            reviewsToSave.add(buildScheduledReviewFromHistory(idToUse, username, wordHistory));
        }

        reviewSessionDao.createScheduledReviewsBatch(reviewsToSave, username);
    }

    private DBScheduledReview buildScheduledReviewFromHistory(String id, String username, LexiconReviewHistory wordHistory) {
        return new DBScheduledReview(id, username, wordHistory.lexiconId(), wordHistory.wordId(), ReviewType.Review, wordHistory.nextTestRelationId(),
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
        Language language = Language.getLanguageById(lexiconMetadata.languageId());

        List<Word> wordsToLearn = wordReviewHelper.getWordsToLearn(lexiconId, username, wordCnt);
        if (wordsToLearn == null || wordsToLearn.size() == 0) {
            return List.of();
        }

        Map<WordElement, Map<Word, List<String>>> similarElementsByWordIdByTestOn = getSimilarElementsByWordIdByTestOn(language, lexiconId, wordsToLearn);

        List<List<WordReview>> wordReviewQueues = new ArrayList<>();
        for(Word word : wordsToLearn) {
            List<WordReview> queue = new ArrayList<>();

            for(int sequenceIndex = 0; sequenceIndex < language.getLearningSequence().size(); sequenceIndex++) {
                LearningTestOptions learningSequenceTestOptions = language.getLearningSequence().get(sequenceIndex);
                TestRelationship reviewRelationship = getReviewRelationship(word, learningSequenceTestOptions.relationship());

                // Skip if the sequence specified a relationship, but word does not contain the required elements in the relationship or any fallbacks
                if (reviewRelationship != null || learningSequenceTestOptions.relationship() == null) {

                    List<String> similarWords = reviewRelationship == null ? List.of() :
                            similarElementsByWordIdByTestOn
                                    .getOrDefault(reviewRelationship.getTestOn(), Map.of())
                                    .getOrDefault(word, List.of());

                    queue.add(buildWordLearning(language, word, learningSequenceTestOptions, reviewRelationship, similarWords));
                }
            }

            wordReviewQueues.add(queue);
        }

        return wordReviewQueues;
    }

    private Map<WordElement, Map<Word, List<String>>> getSimilarElementsByWordIdByTestOn(Language language, String lexiconId, List<Word> wordsToLearn) {
        List<TestRelationship> learningRelationships = language.getLearningSequence().stream()
                .filter(learningTestOption -> learningTestOption.relationship() != null)
                .map(learningTestOption -> learningTestOption.relationship())
                .distinct()
                .collect(Collectors.toUnmodifiableList());

        Set<TestOnWordPair> testOnWordPairs = new HashSet<>();
        for(Word word : wordsToLearn) {
            for(TestRelationship testRelationship : learningRelationships) {
                TestRelationship actualRelationship = getReviewRelationship(word, testRelationship);
                if (actualRelationship != null) {
                    testOnWordPairs.add(new TestOnWordPair(actualRelationship.getTestOn(), word));
                }
            }
        }

        return wordReviewHelper.findSimilarWordElementValues(lexiconId, testOnWordPairs);
    }

    private TestRelationship getReviewRelationship(Word word, TestRelationship relationship) {
        if (relationship == null) {
            return null;
        }

        int fallbackAttemptCnt = 0;

        while(++fallbackAttemptCnt <= MAX_FALLBACK_ATTEMPTS
                && relationship != null
                && (!word.elements().containsKey(relationship.getTestOn().getId()) || !word.elements().containsKey(relationship.getPromptWith().getId()))) {
            relationship = relationship.getFallback();
        }

        if (fallbackAttemptCnt == MAX_FALLBACK_ATTEMPTS) {
            log.error("Exceeded max relationship fallback attempts. Aborting to avoid infinite loop. Relationship configuration likely has circular links.");
            return null;
        }

        return relationship;
    }


    public List<WordReview> generateReviewSession(String lexiconId, Optional<String> reviewRelationShip, Optional<Instant> cutoffInstant, int maxWordCnt, String username) {
        Lexicon lexiconMetadata = lexiconService.getLexiconMetadata(lexiconId);
        Language language = Language.getLanguageById(lexiconMetadata.languageId());

        if (maxWordCnt <= 0 || maxWordCnt > MAX_REVIEW_SIZE) {
            maxWordCnt = MAX_REVIEW_SIZE;
        }

        List<ScheduledWordReview> scheduledReviewWords = getWordsToReview(lexiconId, username, reviewRelationShip, cutoffInstant);

        if (scheduledReviewWords == null || scheduledReviewWords.size() == 0) {
            return List.of();
        }

        if (scheduledReviewWords.size() > maxWordCnt) {
            scheduledReviewWords = scheduledReviewWords.subList(0, maxWordCnt);
        }

        return toWordReview(language, lexiconId, scheduledReviewWords);
    }

    private List<WordReview> toWordReview(Language language, String lexiconId, List<ScheduledWordReview> scheduledReviewWords) {
        List<String> scheduledWordIds = scheduledReviewWords.stream().map(scheduledWordReview -> scheduledWordReview.wordId()).toList();
        Map<String, Word> scheduledWordMap = lexiconService.loadWords(scheduledWordIds).stream().collect(Collectors.toMap(word -> word.id(), word -> word));

        Map<String, TestRelationship> testRelationshipByReviewId = scheduledReviewWords
                .stream()
                .collect(Collectors.toMap(scheduledWordReview -> scheduledWordReview.reviewId(),
                                          scheduledWordReview -> getReviewRelationship(scheduledWordMap.get(scheduledWordReview.wordId()), TestRelationship.getTestRelationshipById(scheduledWordReview.reviewRelationShip()))));

        List<TestOnWordPair> testOnWordPairs = scheduledReviewWords
                .stream()
                .map(scheduledWordReview -> new TestOnWordPair(testRelationshipByReviewId.get(scheduledWordReview.reviewId()).getTestOn(), scheduledWordMap.get(scheduledWordReview.wordId())))
                .collect(Collectors.toUnmodifiableList());

        Map<WordElement, Map<Word, List<String>>> similarWordsByWordIdByTestOn = wordReviewHelper.findSimilarWordElementValues(lexiconId, testOnWordPairs);

        List<WordReview> wordReviews = new ArrayList<>();
        for(ScheduledWordReview scheduledWordReview : scheduledReviewWords){
            TestRelationship testRelationship = testRelationshipByReviewId.get(scheduledWordReview.reviewId());

            wordReviews.add(buildWordReview(
                    language,
                    testRelationship,
                    scheduledWordReview,
                    scheduledWordMap.get(scheduledWordReview.wordId()),
                    similarWordsByWordIdByTestOn
                            .getOrDefault(testRelationship.getTestOn(), Map.of())
                            .getOrDefault(scheduledWordMap.get(scheduledWordReview.wordId()), List.of())));
        }

        return wordReviews;
    }

    public Map<String, Integer> getScheduledReviewCounts(String username, String lexiconId, Optional<Instant> cutoffInstant) {
        Map<String, Integer> scheduledReviewCounts = new HashMap<>();

        for (DBScheduledReview scheduledReview : getCurrentScheduledReviewForLexicon(lexiconId, username,Optional.empty(), cutoffInstant)) {
            scheduledReviewCounts.put(scheduledReview.testRelationshipId(), scheduledReviewCounts.getOrDefault(scheduledReview.testRelationshipId(), 0) + 1);
        }

        return scheduledReviewCounts;
    }

    private List<DBScheduledReview> getCurrentScheduledReviewForLexicon(String lexiconId, String username, Optional<String> reviewRelationship, Optional<Instant> cutoffInstant) {
        Instant now = Instant.now();

        List<DBScheduledReview> scheduledReviews = reviewSessionDao.loadScheduledReviews(username, lexiconId, reviewRelationship.orElse(""), cutoffInstant);

        if (cutoffInstant.isPresent() && cutoffInstant.get().isAfter(now)) {
            scheduledReviews = scheduledReviews.stream().filter(scheduledReview -> isFutureEventAllowed(scheduledReview, now)).toList();
        }

        return scheduledReviews;
    }

    private boolean isFutureEventAllowed(DBScheduledReview dbScheduledReview, Instant now) {
        return (dbScheduledReview.scheduledTestTime().toEpochMilli() - now.toEpochMilli()) < (dbScheduledReview.testDelay().toMillis() * (1 - futureEventAllowedRatio));
    }

    private List<ScheduledWordReview> getWordsToReview(String lexiconId, String username, Optional<String> reviewRelationship, Optional<Instant> cutoffInstant) {
        List<DBScheduledReview> scheduledReviews = getCurrentScheduledReviewForLexicon(lexiconId, username, reviewRelationship, cutoffInstant);

        return scheduledReviews
                .stream()
                .map(dbScheduledReview -> new ScheduledWordReview(dbScheduledReview.id(), dbScheduledReview.wordId(), dbScheduledReview.testRelationshipId(), ReviewType.Review))
                .toList();
    }

    private WordReview buildWordReview(Language language, TestRelationship reviewRelationship, ScheduledWordReview scheduledReview, Word word, List<String> similarWords) {
        return new WordReview(
                language.getId(),
                word,
                scheduledReview.reviewId(),
                reviewRelationship,
                ReviewMode.TypingTest,
                scheduledReview.reviewType(),
                true,
                wordReviewHelper.getWordAllowedTime(language, word, ReviewMode.TypingTest, reviewRelationship),
                wordReviewHelper.getSimilarCharacterSelection(word, reviewRelationship.getTestOn(), similarWords),
                List.of());
    }

    private WordReview buildWordLearning(Language language, Word word, LearningTestOptions sequenceValueOptions, TestRelationship relationship, List<String> similarWords) {
        return new WordReview(
                language.getId(),
                word,
                null,
                relationship,
                sequenceValueOptions.reviewMode(),
                ReviewType.Learn,
                sequenceValueOptions.recordEvent(),
                wordReviewHelper.getWordAllowedTime(language, word, sequenceValueOptions.reviewMode(), relationship),
                sequenceValueOptions.reviewMode() == ReviewMode.TypingTest || sequenceValueOptions.reviewMode() == ReviewMode.WordOverviewWithTyping ? wordReviewHelper.getSimilarCharacterSelection(word, relationship.getTestOn(), similarWords) : List.of(),
                sequenceValueOptions.reviewMode() == ReviewMode.MultipleChoiceTest ? wordReviewHelper.getSimilarWordSelection(word, relationship.getTestOn(), sequenceValueOptions.optionCount(), similarWords) : List.of());
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
