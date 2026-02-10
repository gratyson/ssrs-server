package com.gt.ssrs.reviewSession;

import com.gt.ssrs.language.Language;
import com.gt.ssrs.language.LearningTestOptions;
import com.gt.ssrs.language.TestRelationship;
import com.gt.ssrs.language.WordElement;
import com.gt.ssrs.lexicon.LexiconService;
import com.gt.ssrs.word.WordService;
import com.gt.ssrs.word.model.TestOnWordPair;
import com.gt.ssrs.reviewSession.model.DBReviewEvent;
import com.gt.ssrs.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

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
    private final WordService wordService;
    private final ScheduledReviewService scheduledReviewService;
    private final WordReviewHelper wordReviewHelper;


    @Autowired
    public ReviewSessionService(ReviewSessionDao reviewSessionDao,
                                LexiconService lexiconService,
                                WordService wordService,
                                ScheduledReviewService scheduledReviewService,
                                WordReviewHelper wordReviewHelper) {
        this.reviewSessionDao = reviewSessionDao;
        this.lexiconService = lexiconService;
        this.wordService = wordService;
        this.scheduledReviewService = scheduledReviewService;
        this.wordReviewHelper = wordReviewHelper;
    }

    public void saveReviewEvent(ReviewEvent event, String username, Instant eventInstant) {
        reviewSessionDao.saveReviewEvent(DBReviewEvent.fromReviewEvent(event, username, eventInstant), event.scheduledEventId());
    }

    public void recordManualEvent(ReviewEvent event, String username) {
        Optional<ScheduledWordReview> nextScheduledReview = scheduledReviewService.loadEarliestScheduledReview(event.lexiconId(), username, event.wordId());
        if (nextScheduledReview.isEmpty()) {
            log.warn("Manual event requested for word " + event.wordId() + ". However, no scheduled events exist for word.");
            return;
        }

        TestRelationship scheduledTestRelationship = getTestRelationFromId(event.lexiconId(), nextScheduledReview.get().reviewRelationShip());
        if (scheduledTestRelationship == null) {
            log.warn("Unknown relation scheduled for test " + nextScheduledReview.get().reviewId());
            return;
        }

        ReviewEvent reviewEventToSave = new ReviewEvent(
                nextScheduledReview.get().reviewId(),
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
        LexiconMetadata lexicon = lexiconService.getLexiconMetadata(lexiconId);
        Language language = Language.getLanguageById(lexicon.languageId());

        for(TestRelationship testRelationship : language.getReviewTestRelationships()) {
            if (testRelationship.getId().equals(relationshipId)) {
                return testRelationship;
            }
        }

        return null;
    }

    public List<List<WordReview>> generateLearningSession(String lexiconId, int wordCnt, String username) {
        LexiconMetadata lexiconMetadata = lexiconService.getLexiconMetadata(lexiconId);
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
        LexiconMetadata lexiconMetadata = lexiconService.getLexiconMetadata(lexiconId);
        Language language = Language.getLanguageById(lexiconMetadata.languageId());

        if (maxWordCnt <= 0 || maxWordCnt > MAX_REVIEW_SIZE) {
            maxWordCnt = MAX_REVIEW_SIZE;
        }

        List<ScheduledWordReview> scheduledReviewWords = scheduledReviewService.getCurrentScheduledReviewForLexicon(lexiconId, username, reviewRelationShip, cutoffInstant);

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
        Map<String, Word> scheduledWordMap = wordService.loadWords(scheduledWordIds).stream().collect(Collectors.toMap(word -> word.id(), word -> word));

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
}
