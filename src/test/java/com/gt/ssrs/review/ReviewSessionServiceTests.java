package com.gt.ssrs.review;

import com.gt.ssrs.language.Language;
import com.gt.ssrs.language.LearningTestOptions;
import com.gt.ssrs.language.TestRelationship;
import com.gt.ssrs.language.WordElement;
import com.gt.ssrs.lexicon.LexiconService;
import com.gt.ssrs.lexicon.model.TestOnWordPair;
import com.gt.ssrs.review.model.DBReviewEvent;
import com.gt.ssrs.review.model.DBScheduledReview;
import com.gt.ssrs.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
public class ReviewSessionServiceTests {

    private static final String TEST_USERNAME = "testUser";
    private static final String TEST_LEXICON_ID = UUID.randomUUID().toString();
    private static final String TEST_WORD_1_ID = UUID.randomUUID().toString();
    private static final String TEST_WORD_2_ID = UUID.randomUUID().toString();
    private static final String TEST_WORD_3_ID = UUID.randomUUID().toString();
    private static final Language TEST_LANGUAGE = Language.Japanese;
    private static final LexiconMetadata TEST_LEXICON_METADATA = new LexiconMetadata(TEST_LEXICON_ID, TEST_USERNAME, "Test Lexicon title",
            "Test Lexicon description", TEST_LANGUAGE.getId(), "");
    private static final List<String> SIMILAR_ELEMENT_VALUES = List.of("A", "B", "C");

    private static final double FUTURE_EVENT_ALLOWED_RATIO = .8;

    @Mock private ReviewSessionDao reviewSessionDao;
    @Mock private LexiconService lexiconService;
    @Mock private WordReviewHelper wordReviewHelper;

    private ReviewSessionService reviewSessionService;

    @BeforeEach
    public void setup() {
        reviewSessionService = new ReviewSessionService(reviewSessionDao, lexiconService, wordReviewHelper, FUTURE_EVENT_ALLOWED_RATIO);

        when(lexiconService.getLexiconMetadata(TEST_LEXICON_ID)).thenReturn(TEST_LEXICON_METADATA);

        when(wordReviewHelper.getWordAllowedTime(eq(TEST_LANGUAGE), any(Word.class), any(ReviewMode.class), any(TestRelationship.class)))
                .then(invoc -> calcAllowedTime(invoc.getArgument(1), invoc.getArgument(2), ((TestRelationship)invoc.getArgument(3))));

        when(wordReviewHelper.findSimilarWordElementValues(eq(TEST_LEXICON_ID), anyCollection())).then(
                v -> {
                    Collection<TestOnWordPair> testOnWordPairs = v.getArgument(1);
                    return testOnWordPairs
                            .stream()
                            .collect(Collectors.groupingBy(testOnWordPair -> testOnWordPair.testOn()))
                            .entrySet().stream()
                            .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue()
                                    .stream()
                                    .collect(Collectors.toMap(testOnWordPair -> testOnWordPair.word(), testOnWordPair -> SIMILAR_ELEMENT_VALUES))));
                });

        when(wordReviewHelper.getSimilarCharacterSelection(any(Word.class), any(WordElement.class), eq(SIMILAR_ELEMENT_VALUES)))
                .then(invoc -> calcTypingTestButtons(invoc.getArgument(0), ((WordElement)invoc.getArgument(1))));

        when(wordReviewHelper.getSimilarWordSelection(any(Word.class), any(WordElement.class), anyInt(), eq(SIMILAR_ELEMENT_VALUES)))
                .then(invoc -> calcMultipleChoiceButtons(invoc.getArgument(0), ((WordElement)invoc.getArgument(1)), invoc.getArgument(2)));
    }

    @Test
    public void testSaveReviewEvent() {
        String eventId = UUID.randomUUID().toString();
        Instant eventInstant = Instant.now();

        ReviewEvent reviewEvent = new ReviewEvent(eventId, TEST_LEXICON_ID, TEST_WORD_1_ID, ReviewType.Review, ReviewMode.TypingTest,
                TestRelationship.KanaToMeaning.getId(), true, false, 3000, false);

        reviewSessionService.saveReviewEvent(reviewEvent, TEST_USERNAME, eventInstant);

        verify(reviewSessionDao).saveReviewEvent(new DBReviewEvent(null, TEST_LEXICON_ID, TEST_WORD_1_ID, TEST_USERNAME, eventInstant, ReviewType.Review,
                ReviewMode.TypingTest, WordElement.Kana.getId(), WordElement.Meaning.getId(), true, false, 3000, false), eventId);
    }

    @Test
    public void testGetScheduledReviewCounts() {
        when(reviewSessionDao.loadScheduledReviews(TEST_USERNAME, TEST_LEXICON_ID, "", Optional.empty())).thenReturn(List.of(
                buildDBScheduledReview(TEST_WORD_1_ID, 2, Instant.now().minusSeconds(60), Duration.ofSeconds(60), false),
                buildDBScheduledReview(TEST_WORD_2_ID, 1, Instant.now().minusSeconds(60), Duration.ofSeconds(60), false),
                buildDBScheduledReview(TEST_WORD_3_ID, 2, Instant.now().minusSeconds(60), Duration.ofSeconds(60), false)));

        Map<String, Integer> scheduledReviewCount = reviewSessionService.getScheduledReviewCounts(TEST_USERNAME, TEST_LEXICON_ID, Optional.empty());

        assertEquals(Map.of(TEST_LANGUAGE.getReviewTestRelationships().get(1).getId(),1,
                            TEST_LANGUAGE.getReviewTestRelationships().get(2).getId(),2),
                     scheduledReviewCount);
    }

    @Test
    public void testGenerateLearningSession() {
        List<Word> wordsToLearn = List.of(
                buildWord(TEST_WORD_1_ID, false),
                buildWord(TEST_WORD_2_ID, false),
                buildWord(TEST_WORD_3_ID, true));
        when(wordReviewHelper.getWordsToLearn(TEST_LEXICON_ID, TEST_USERNAME, wordsToLearn.size())).thenReturn(wordsToLearn);

        List<List<WordReview>> learningSession = reviewSessionService.generateLearningSession(TEST_LEXICON_ID, wordsToLearn.size(), TEST_USERNAME);

        assertEquals(wordsToLearn.size(), learningSession.size());
        for(int wordIndex = 0; wordIndex < wordsToLearn.size(); wordIndex++) {
            Word word = wordsToLearn.get(wordIndex);
            boolean isWordOnlyRequiredElements = word.elements().size() == TEST_LANGUAGE.getRequiredElements().size();

            List<WordReview> wordReviews = learningSession.get(wordIndex);
            assertEquals(TEST_LANGUAGE.getLearningSequence().size(), wordReviews.size());

            int wordReviewIndex = 0;

            for (LearningTestOptions options : TEST_LANGUAGE.getLearningSequence()) {
                WordReview wordReview = wordReviews.get(wordReviewIndex++);
                // Word is missing all non-required elements, so it should always fallback if there's a fallback option
                TestRelationship relationship = isWordOnlyRequiredElements && options.relationship() != null && options.relationship().getFallback() != null ? options.relationship().getFallback() : options.relationship();

                verifyWordReview(wordReview, word, relationship, options.reviewMode(), ReviewType.Learn, options.recordEvent(), options.optionCount());
            }
        }
    }

    @Test
    public void testGenerateLearningSession_FewerWordsThanRequested() {
        Word testWord = buildWord(TEST_WORD_1_ID, false);
        when(wordReviewHelper.getWordsToLearn(TEST_LEXICON_ID, TEST_USERNAME, 3)).thenReturn(List.of(testWord));

        List<List<WordReview>> learningSession = reviewSessionService.generateLearningSession(TEST_LEXICON_ID, 3, TEST_USERNAME);

        assertEquals(1, learningSession.size());
        List<WordReview> wordReviews = learningSession.get(0);

        assertEquals(TEST_LANGUAGE.getLearningSequence().size(), wordReviews.size());

        int wordReviewIndex = 0;
        for (LearningTestOptions options : TEST_LANGUAGE.getLearningSequence()) {
            WordReview wordReview = wordReviews.get(wordReviewIndex++);
            verifyWordReview(wordReview, testWord, options.relationship(), options.reviewMode(), ReviewType.Learn, options.recordEvent(), options.optionCount());
        }
    }

    @Test
    public void testGenerateReviewSession() {
        Instant scheduledInstant = Instant.now().minusSeconds(60);
        List<DBScheduledReview> scheduledReviews = List.of(
                buildDBScheduledReview(TEST_WORD_1_ID, 1, scheduledInstant),
                buildDBScheduledReview(TEST_WORD_2_ID, 2, scheduledInstant),
                buildDBScheduledReview(TEST_WORD_3_ID, 0, scheduledInstant));
        when(reviewSessionDao.loadScheduledReviews(TEST_USERNAME, TEST_LEXICON_ID, "", Optional.empty())).thenReturn(scheduledReviews);

        List<Word> wordsToReview = List.of(buildWord(TEST_WORD_1_ID, false),
                                           buildWord(TEST_WORD_2_ID, false),
                                           buildWord(TEST_WORD_3_ID, true));
        when(lexiconService.loadWords(List.of(TEST_WORD_1_ID, TEST_WORD_2_ID, TEST_WORD_3_ID))).thenReturn(wordsToReview);

        List<WordReview> wordReviews = reviewSessionService.generateReviewSession(TEST_LEXICON_ID, Optional.empty(), Optional.empty(), 0, TEST_USERNAME);

        assertEquals(3, wordReviews.size());
        for(int index = 0 ; index < 3; index++) {
            WordReview review = wordReviews.get(index);
            Word word = wordsToReview.get(index);
            TestRelationship relationship = TEST_LANGUAGE.getReviewTestRelationships().get((index + 1) % 3);

            assertEquals(TEST_LANGUAGE.getId(), review.languageId());
            assertEquals(word, review.word());
            assertNotNull(review.scheduledEventId());
            assertEquals(relationship, review.testRelationship());
            assertEquals(ReviewMode.TypingTest, review.reviewMode());
            assertEquals(ReviewType.Review, review.reviewType());
            assertTrue(review.recordResult());
            assertEquals(calcAllowedTime(word, ReviewMode.TypingTest, relationship.getTestOn().getId()), review.allowedTimeSec());
            assertEquals(calcTypingTestButtons(word, relationship.getTestOn().getId()), review.typingTestButtons());
            assertEquals(List.of(), review.multipleChoiceButtons());
        }
    }

    @Test
    public void testGenerateReviewSession_MaxWordsSet() {
        Instant scheduledInstant = Instant.now().minusSeconds(60);
        List<DBScheduledReview> scheduledReviews = List.of(
                buildDBScheduledReview(TEST_WORD_1_ID, 1, scheduledInstant),
                buildDBScheduledReview(TEST_WORD_2_ID, 2, scheduledInstant),
                buildDBScheduledReview(TEST_WORD_3_ID, 0, scheduledInstant));
        when(reviewSessionDao.loadScheduledReviews(TEST_USERNAME, TEST_LEXICON_ID, "", Optional.empty())).thenReturn(scheduledReviews);

        Word word = buildWord(TEST_WORD_1_ID, false);
        List<Word> wordsToReview = List.of(word);
        when(lexiconService.loadWords(List.of(TEST_WORD_1_ID))).thenReturn(wordsToReview);

        List<WordReview> wordReviews = reviewSessionService.generateReviewSession(TEST_LEXICON_ID, Optional.empty(), Optional.empty(), 1, TEST_USERNAME);

        assertEquals(1, wordReviews.size());
        WordReview review = wordReviews.get(0);
        TestRelationship relationship = TEST_LANGUAGE.getReviewTestRelationships().get(1);

        assertEquals(TEST_LANGUAGE.getId(), review.languageId());
        assertEquals(word, review.word());
        assertNotNull(review.scheduledEventId());
        assertEquals(relationship, review.testRelationship());
        assertEquals(ReviewMode.TypingTest, review.reviewMode());
        assertEquals(ReviewType.Review, review.reviewType());
        assertTrue(review.recordResult());
        assertEquals(calcAllowedTime(word, ReviewMode.TypingTest, relationship.getTestOn().getId()), review.allowedTimeSec());
        assertEquals(calcTypingTestButtons(word, relationship.getTestOn().getId()), review.typingTestButtons());
        assertEquals(List.of(), review.multipleChoiceButtons());
    }

    @Test
    public void testGenerateReviewSession_SpecifyRelationship() {
        Instant scheduledInstant = Instant.now().minusSeconds(60);
        List<DBScheduledReview> scheduledReviews = List.of(
                buildDBScheduledReview(TEST_WORD_1_ID, 1, scheduledInstant));
        when(reviewSessionDao.loadScheduledReviews(TEST_USERNAME, TEST_LEXICON_ID, TEST_LANGUAGE.getReviewTestRelationships().get(1).getId(), Optional.empty())).thenReturn(scheduledReviews);

        Word word = buildWord(TEST_WORD_1_ID, false);
        List<Word> wordsToReview = List.of(word);
        when(lexiconService.loadWords(List.of(TEST_WORD_1_ID))).thenReturn(wordsToReview);

        List<WordReview> wordReviews = reviewSessionService.generateReviewSession(TEST_LEXICON_ID, Optional.of(TEST_LANGUAGE.getReviewTestRelationships().get(1).getId()), Optional.empty(), 0, TEST_USERNAME);

        assertEquals(1, wordReviews.size());
        WordReview review = wordReviews.get(0);
        TestRelationship relationship = TEST_LANGUAGE.getReviewTestRelationships().get(1);

        assertEquals(TEST_LANGUAGE.getId(), review.languageId());
        assertEquals(word, review.word());
        assertNotNull(review.scheduledEventId());
        assertEquals(relationship, review.testRelationship());
        assertEquals(ReviewMode.TypingTest, review.reviewMode());
        assertEquals(ReviewType.Review, review.reviewType());
        assertTrue(review.recordResult());
        assertEquals(calcAllowedTime(word, ReviewMode.TypingTest, relationship.getTestOn().getId()), review.allowedTimeSec());
        assertEquals(calcTypingTestButtons(word, relationship.getTestOn().getId()), review.typingTestButtons());
        assertEquals(List.of(), review.multipleChoiceButtons());
    }

    @Test
    public void testGenerateReviewSession_FutureReview() {
        Instant cutoffInstant = Instant.now().plusSeconds(60);

        List<DBScheduledReview> scheduledReviews = List.of(
                buildDBScheduledReview(TEST_WORD_1_ID, 1, Instant.now().minusSeconds(60)),  // Past, include
                buildDBScheduledReview(TEST_WORD_2_ID, 2, Instant.now().plusSeconds(10)),      // Future, 83% of time has past, include
                buildDBScheduledReview(TEST_WORD_3_ID, 0, Instant.now().plusSeconds(50)));     // Future, 18% of time pas past, excluded
        when(reviewSessionDao.loadScheduledReviews(TEST_USERNAME, TEST_LEXICON_ID, "", Optional.of(cutoffInstant))).thenReturn(scheduledReviews);

        List<Word> wordsToReview = List.of(buildWord(TEST_WORD_1_ID, false),
                                           buildWord(TEST_WORD_2_ID, false));
        when(lexiconService.loadWords(List.of(TEST_WORD_1_ID, TEST_WORD_2_ID))).thenReturn(wordsToReview);

        List<WordReview> wordReviews = reviewSessionService.generateReviewSession(TEST_LEXICON_ID, Optional.empty(), Optional.of(cutoffInstant), 0, TEST_USERNAME);

        assertEquals(2, wordReviews.size());
        for(int index = 0 ; index < 2; index++) {
            WordReview review = wordReviews.get(index);

            Word word = wordsToReview.get(index);
            TestRelationship relationship = TEST_LANGUAGE.getReviewTestRelationships().get((index + 1) % 3);

            assertEquals(TEST_LANGUAGE.getId(), review.languageId());
            assertEquals(word, review.word());
            assertNotNull(review.scheduledEventId());
            assertEquals(relationship, review.testRelationship());
            assertEquals(ReviewMode.TypingTest, review.reviewMode());
            assertEquals(ReviewType.Review, review.reviewType());
            assertTrue(review.recordResult());
            assertEquals(calcAllowedTime(word, ReviewMode.TypingTest, relationship.getTestOn().getId()), review.allowedTimeSec());
            assertEquals(calcTypingTestButtons(word, relationship.getTestOn().getId()), review.typingTestButtons());
            assertEquals(List.of(), review.multipleChoiceButtons());
        }
    }


    private Word buildWord(String id, boolean requiredElementsOnly) {
        Map<String, String> wordElements = new HashMap<>();
        List<WordElement> elementsToUse = requiredElementsOnly ? TEST_LANGUAGE.getRequiredElements() : TEST_LANGUAGE.getCoreElements();

        for (WordElement element : elementsToUse) {
            wordElements.put(element.getId(), element.getId() + "_" + id);
        }

        return new Word(id, TEST_LEXICON_ID, TEST_USERNAME, wordElements, "n", List.of(), Instant.EPOCH, Instant.now());
    }

    private static void verifyWordReview(WordReview wordReview, Word word, TestRelationship reviewRelationship, ReviewMode reviewMode, ReviewType reviewType, boolean recordResult, int multipleChoiceCnt) {
        assertEquals(TEST_LANGUAGE.getId(), wordReview.languageId());
        assertEquals(word, wordReview.word());
        assertNull(wordReview.scheduledEventId());
        assertEquals(reviewRelationship, wordReview.testRelationship());
        assertEquals(reviewMode, wordReview.reviewMode());
        assertEquals(reviewType, wordReview.reviewType());
        assertEquals(recordResult, wordReview.recordResult());
        assertEquals(calcAllowedTime(word, reviewMode, wordReview.testRelationship()), wordReview.allowedTimeSec());
        assertEquals(reviewMode == ReviewMode.TypingTest ? calcTypingTestButtons(word, wordReview.testRelationship().getTestOn()) : List.of(), wordReview.typingTestButtons());
        assertEquals(reviewMode == ReviewMode.MultipleChoiceTest ? calcMultipleChoiceButtons(word, wordReview.testRelationship().getTestOn(), multipleChoiceCnt) : List.of(), wordReview.multipleChoiceButtons());
    }

    // generate a unique value that can be verified to be set correctly
    private static int calcAllowedTime(Word word, ReviewMode mode, TestRelationship relationship) {
        return calcAllowedTime(word, mode, relationship == null ? "" : relationship.getTestOn().getId());
    }
    private static int calcAllowedTime(Word word, ReviewMode mode, String testOnId) {
        return mode == ReviewMode.WordOverview ? 0 : (word.hashCode() + mode.getReviewModeId() + testOnId.hashCode()) % 30;
    }

    private static List<String> calcTypingTestButtons(Word word, WordElement element) {
        return calcTypingTestButtons(word, element.getId());
    }
    private static List<String> calcTypingTestButtons(Word word, String elementId) {
        return Arrays.stream(("Typing" + word.elements().get(elementId)).split("(?!^)")).toList();
    }

    private static List<String> calcMultipleChoiceButtons(Word word, WordElement element, int count) {
        List<String> multipleChoiceButtons = new ArrayList<>();

        for(int i = 0; i < count - 1; i++) {
            multipleChoiceButtons.add("Multiple" + i);
        }
        multipleChoiceButtons.add(word.elements().get(element.getId()));

        return multipleChoiceButtons;
    }

    private static DBScheduledReview buildDBScheduledReview(String wordId, int reviewRelationshipIndex, Instant scheduledTime) {
        return buildDBScheduledReview(wordId, reviewRelationshipIndex, scheduledTime, Duration.ofSeconds(60), false);
    }

    private static DBScheduledReview buildDBScheduledReview(String wordId, int reviewRelationshipIndex, Instant scheduledTime, Duration testDelay, boolean completed) {
        return new DBScheduledReview(UUID.randomUUID().toString(), TEST_USERNAME, TEST_LEXICON_ID, wordId, ReviewType.Review,
                TEST_LANGUAGE.getReviewTestRelationships().get(reviewRelationshipIndex).getId(), scheduledTime, testDelay, completed);
    }


}
