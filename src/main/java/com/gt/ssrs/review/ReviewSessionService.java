package com.gt.ssrs.review;

import com.gt.ssrs.language.LanguageService;
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
                scheduledTestRelationship.testOn(),
                scheduledTestRelationship.promptWith(),
                event.isCorrect(),
                event.isNearMiss(),
                0,
                false);

        saveReviewEvent(reviewEventToSave, username, Instant.now());
    }

    private TestRelationship getTestRelationFromId(String lexiconId, String relationshipId) {
        Lexicon lexicon = lexiconService.getLexiconMetadata(lexiconId);
        Language language = languageService.GetLanguageById(lexicon.languageId());

        for(TestRelationship testRelationship : language.testRelationships()) {
            if (testRelationship.id().equals(relationshipId)) {
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
        Language language = languageService.GetLanguageById(lexiconMetadata.languageId());
        Map<String, TestRelationship> relationshipMap = getTestRelationshipMap(language, false);

        List<Word> wordsToLearn = wordReviewHelper.getWordsToLearn(lexiconId, username, wordCnt);
        if (wordsToLearn == null || wordsToLearn.size() == 0) {
            return List.of();
        }

        List<LanguageSequenceValue> learningSequence = languageService.getLanguageSequence(lexiconMetadata.languageId(), ReviewType.Learn);

        Map<String, Map<String, List<String>>> similarElementsByWordIdByTestOn = getSimilarElementsByWordIdByTestOn(lexiconId, wordsToLearn, learningSequence, relationshipMap);

        List<List<WordReview>> wordReviewQueues = new ArrayList<>();
        for(Word word : wordsToLearn) {
            List<WordReview> queue = new ArrayList<>();

            for(int sequenceIndex = 0; sequenceIndex < learningSequence.size(); sequenceIndex++) {
                LanguageSequenceValue sequenceValue = learningSequence.get(sequenceIndex);
                TestRelationship reviewRelationship = getReviewRelationship(word, sequenceValue.relationshipId(), relationshipMap);

                // Skip if the sequence specified a relationship, but word does not contain the required elements in the relationship or any fallbacks
                if (reviewRelationship != null || sequenceValue.relationshipId() == null) {

                    queue.add(buildWordLearning(language, word, sequenceValue, reviewRelationship, similarElementsByWordIdByTestOn
                            .getOrDefault(reviewRelationship.testOn(), Map.of())
                            .getOrDefault(word.id(), List.of())));
                }
            }

            wordReviewQueues.add(queue);
        }

        return wordReviewQueues;
    }

    private Map<String, TestRelationship> getTestRelationshipMap(Language language, boolean reviewRelationsOnly) {
        return language.testRelationships()
                .stream()
                .filter(relation -> relation.isReviewRelationship() || !reviewRelationsOnly)
                .collect(Collectors.toMap(relation -> relation.id(), relation -> relation));
    }

    private Map<String, Map<String, List<String>>> getSimilarElementsByWordIdByTestOn(String lexiconId, List<Word> wordsToLearn, List<LanguageSequenceValue> learningSequence, Map<String, TestRelationship> relationshipMap) {
        List<String> learningRelationshipIds = learningSequence.stream()
                .filter(languageSequenceValue -> languageSequenceValue.relationshipId() != null)
                .map(languageSequenceValue -> languageSequenceValue.relationshipId())
                .distinct()
                .collect(Collectors.toUnmodifiableList());

        Set<TestOnWordPair> testOnWordPairs = new HashSet<>();
        for(Word word : wordsToLearn) {
            for(String relationshipId : learningRelationshipIds) {
                TestRelationship actualRelationship = getReviewRelationship(word, relationshipId, relationshipMap);
                testOnWordPairs.add(new TestOnWordPair(actualRelationship.testOn(), word));
            }
        }

        return wordReviewHelper.findSimilarWordElementValuesBatch(lexiconId, testOnWordPairs);
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
                         scheduledWordReview -> getReviewRelationship(language, scheduledWordMap.get(scheduledWordReview.wordId()), scheduledWordReview.reviewRelationShip())));

        List<TestOnWordPair> testOnWordPairs = scheduledReviewWords
                .stream()
                .map(scheduledWordReview -> new TestOnWordPair(testRelationshipByReviewId.get(scheduledWordReview.reviewId()).testOn(), scheduledWordMap.get(scheduledWordReview.wordId())))
                .collect(Collectors.toUnmodifiableList());

        Map<String, Map<String, List<String>>> similarWordsByWordIdByTestOn = wordReviewHelper.findSimilarWordElementValuesBatch(lexiconId, testOnWordPairs);

        List<WordReview> wordReviews = new ArrayList<>();
        for(ScheduledWordReview scheduledWordReview : scheduledReviewWords){
            TestRelationship testRelationship = testRelationshipByReviewId.get(scheduledWordReview.reviewId());

            wordReviews.add(buildWordReview(
                    language,
                    testRelationship,
                    scheduledWordReview,
                    scheduledWordMap.get(scheduledWordReview.wordId()),
                    similarWordsByWordIdByTestOn
                            .getOrDefault(testRelationship.testOn(), Map.of())
                            .getOrDefault(scheduledWordReview.wordId(), List.of())));
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
                wordReviewHelper.getSimilarCharacterSelection(word, reviewRelationship.testOn(), similarWords),
                List.of());
    }

    private WordReview buildWordLearning(Language language, Word word, LanguageSequenceValue sequenceValue, TestRelationship relationship, List<String> similarWords) {
        return new WordReview(
                language.id(),
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
            log.error("Invalid relationship {} on review {}. Defaulting to first valid relationship.", scheduledReview.reviewRelationShip(),  scheduledReview.reviewId());
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
