package com.gt.ssrs.review;

import com.gt.ssrs.language.LanguageService;
import com.gt.ssrs.lexicon.LexiconService;
import com.gt.ssrs.lexicon.model.TestOnWordPair;
import com.gt.ssrs.review.model.DBReviewEvent;
import com.gt.ssrs.review.model.DBScheduledReview;
import com.gt.ssrs.model.*;
import com.gt.ssrs.util.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
public class ReviewSessionServiceTests {

    private static final String TEST_USERNAME = "testUser";
    private static final String TEST_LEXICON_ID = UUID.randomUUID().toString();
    private static final String TEST_WORD_1_ID = UUID.randomUUID().toString();
    private static final String TEST_WORD_2_ID = UUID.randomUUID().toString();
    private static final String TEST_WORD_3_ID = UUID.randomUUID().toString();
    private static final Language TEST_LANGUAGE = TestUtils.getTestLanguage();
    private static final Lexicon TEST_LEXICON_METADATA = new Lexicon(TEST_LEXICON_ID, TEST_USERNAME, "Test Lexicon title",
            "Test Lexicon description", TEST_LANGUAGE.id(), "", List.of());
    private static final List<String> SIMILAR_ELEMENT_VALUES = List.of("A", "B", "C");

    private static final double FUTURE_EVENT_ALLOWED_RATIO = .8;

    @Mock private ReviewSessionDao reviewSessionDao;
    @Mock private LexiconService lexiconService;
    @Mock private WordReviewHelper wordReviewHelper;
    @Mock private LanguageService languageService;

    private ReviewSessionService reviewSessionService;

    private List<LanguageSequenceValue> learningSequence;

    @BeforeEach
    public void setup() {
        reviewSessionService = new ReviewSessionService(reviewSessionDao, lexiconService, wordReviewHelper, languageService, FUTURE_EVENT_ALLOWED_RATIO);

        //when(languageService.GetAllLanguages()).thenReturn(List.of(TEST_LANGUAGE));
        when(languageService.GetLanguageById(TEST_LANGUAGE.id())).thenReturn(TEST_LANGUAGE);

        when(lexiconService.getLexiconMetadata(TEST_LEXICON_ID)).thenReturn(TEST_LEXICON_METADATA);

        learningSequence = new ArrayList<>();
        learningSequence.add(new LanguageSequenceValue(ReviewMode.WordOverview, 0, false, null));
        for(int index = 0; index< 3; index++) {
            TestRelationship reviewRelationship = TEST_LANGUAGE.testRelationships().get(index);
            learningSequence.add(new LanguageSequenceValue(ReviewMode.MultipleChoiceTest, index + 4,
                    false, reviewRelationship.id()));
            learningSequence.add(new LanguageSequenceValue(ReviewMode.TypingTest, 0,
                    index == 2, reviewRelationship.id()));
        }
        when(languageService.getLanguageSequence(TEST_LANGUAGE.id(), ReviewType.Learn)).thenReturn(learningSequence);

        when(wordReviewHelper.getWordAllowedTime(eq(TEST_LANGUAGE), any(Word.class), any(ReviewMode.class), anyString()))
                .then(invoc -> calcAllowedTime(invoc.getArgument(1), invoc.getArgument(2), invoc.getArgument(3)));

        when(wordReviewHelper.findSimilarWordElementValuesBatch(eq(TEST_LEXICON_ID), anyCollection())).then(
                v -> {
                    Collection<TestOnWordPair> testOnWordPairs = v.getArgument(1);
                    return testOnWordPairs
                            .stream()
                            .collect(Collectors.groupingBy(testOnWordPair -> testOnWordPair.testOn()))
                            .entrySet().stream()
                            .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue()
                                    .stream()
                                    .collect(Collectors.toMap(testOnWordPair -> testOnWordPair.word().id(), testOnWordPair -> SIMILAR_ELEMENT_VALUES))));
                });

        when(wordReviewHelper.getSimilarCharacterSelection(any(Word.class), anyString(), eq(SIMILAR_ELEMENT_VALUES)))
                .then(invoc -> calcTypingTestButtons(invoc.getArgument(0), invoc.getArgument(1)));

        when(wordReviewHelper.getSimilarWordSelection(any(Word.class), anyString(), anyInt(), eq(SIMILAR_ELEMENT_VALUES)))
                .then(invoc -> calcMultipleChoiceButtons(invoc.getArgument(0), invoc.getArgument(1), invoc.getArgument(2)));
    }

    @Test
    public void testSaveReviewEvent() {
        String eventId = UUID.randomUUID().toString();
        Instant eventInstant = Instant.now();

        ReviewEvent reviewEvent = new ReviewEvent(eventId, TEST_LEXICON_ID, TEST_WORD_1_ID, ReviewType.Review, ReviewMode.TypingTest,
                "kana", "meaning", true, false, 3000, false);

        reviewSessionService.saveReviewEvent(reviewEvent, TEST_USERNAME, eventInstant);

        verify(reviewSessionDao).saveReviewEvent(new DBReviewEvent(null, TEST_LEXICON_ID, TEST_WORD_1_ID, TEST_USERNAME, eventInstant, ReviewType.Review,
                ReviewMode.TypingTest, "kana", "meaning", true, false, 3000, false), eventId);
    }

    @Test
    public void testGetScheduledReviewCounts() {
        when(reviewSessionDao.loadScheduledReviews(TEST_USERNAME, TEST_LEXICON_ID, "", Optional.empty())).thenReturn(List.of(
                buildDBScheduledReview(TEST_WORD_1_ID, 2, Instant.now().minusSeconds(60), Duration.ofSeconds(60), false),
                buildDBScheduledReview(TEST_WORD_2_ID, 1, Instant.now().minusSeconds(60), Duration.ofSeconds(60), false),
                buildDBScheduledReview(TEST_WORD_3_ID, 2, Instant.now().minusSeconds(60), Duration.ofSeconds(60), false)));

        Map<String, Integer> scheduledReviewCount = reviewSessionService.getScheduledReviewCounts(TEST_USERNAME, TEST_LEXICON_ID, Optional.empty());

        assertEquals(Map.of(TEST_LANGUAGE.testRelationships().get(1).id(),1,
                            TEST_LANGUAGE.testRelationships().get(2).id(),2),
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
            boolean isWordOnlyRequiredElements = word.elements().size() == TEST_LANGUAGE.requiredElements().size();

            List<WordReview> wordReviews = learningSession.get(wordIndex);
            assertEquals(7, wordReviews.size());

            int wordReviewIndex = 0;
            verifyWordReview(wordReviews.get(wordReviewIndex++), word, null, ReviewMode.WordOverview, ReviewType.Learn, false, 0);
            for(int relationshipIndex = 0; relationshipIndex < 3; relationshipIndex++) {
                verifyWordReview(wordReviews.get(wordReviewIndex++), word, TEST_LANGUAGE.testRelationships().get(isWordOnlyRequiredElements ? 0 : relationshipIndex), ReviewMode.MultipleChoiceTest, ReviewType.Learn, false, relationshipIndex + 4);
                verifyWordReview(wordReviews.get(wordReviewIndex++), word, TEST_LANGUAGE.testRelationships().get(isWordOnlyRequiredElements ? 0 : relationshipIndex), ReviewMode.TypingTest, ReviewType.Learn, relationshipIndex == 2, 0);
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

        int wordReviewIndex = 0;
        verifyWordReview(wordReviews.get(wordReviewIndex++), testWord, null, ReviewMode.WordOverview, ReviewType.Learn, false, 0);
        for(int relationshipIndex = 0; relationshipIndex < 3; relationshipIndex++) {
            verifyWordReview(wordReviews.get(wordReviewIndex++), testWord, TEST_LANGUAGE.testRelationships().get(relationshipIndex), ReviewMode.MultipleChoiceTest, ReviewType.Learn, false, relationshipIndex + 4);
            verifyWordReview(wordReviews.get(wordReviewIndex++), testWord, TEST_LANGUAGE.testRelationships().get(relationshipIndex), ReviewMode.TypingTest, ReviewType.Learn, relationshipIndex == 2, 0);
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
            TestRelationship relationship = TEST_LANGUAGE.testRelationships().get((index + 1) % 3);

            assertEquals(TEST_LANGUAGE.id(), review.languageId());
            assertEquals(word, review.word());
            assertNotNull(review.scheduledEventId());
            assertEquals(relationship.testOn(), review.testOn());
            assertEquals(relationship.promptWith(), review.promptWith());
            assertEquals(relationship.showAfterTest(), review.showAfterTest());
            assertEquals(ReviewMode.TypingTest, review.reviewMode());
            assertEquals(ReviewType.Review, review.reviewType());
            assertTrue(review.recordResult());
            assertEquals(calcAllowedTime(word, ReviewMode.TypingTest, relationship.testOn()), review.allowedTimeSec());
            assertEquals(calcTypingTestButtons(word, relationship.testOn()), review.typingTestButtons());
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
        TestRelationship relationship = TEST_LANGUAGE.testRelationships().get(1);

        assertEquals(TEST_LANGUAGE.id(), review.languageId());
        assertEquals(word, review.word());
        assertNotNull(review.scheduledEventId());
        assertEquals(relationship.testOn(), review.testOn());
        assertEquals(relationship.promptWith(), review.promptWith());
        assertEquals(relationship.showAfterTest(), review.showAfterTest());
        assertEquals(ReviewMode.TypingTest, review.reviewMode());
        assertEquals(ReviewType.Review, review.reviewType());
        assertTrue(review.recordResult());
        assertEquals(calcAllowedTime(word, ReviewMode.TypingTest, relationship.testOn()), review.allowedTimeSec());
        assertEquals(calcTypingTestButtons(word, relationship.testOn()), review.typingTestButtons());
        assertEquals(List.of(), review.multipleChoiceButtons());
    }

    @Test
    public void testGenerateReviewSession_SpecifyRelationship() {
        Instant scheduledInstant = Instant.now().minusSeconds(60);
        List<DBScheduledReview> scheduledReviews = List.of(
                buildDBScheduledReview(TEST_WORD_1_ID, 1, scheduledInstant));
        when(reviewSessionDao.loadScheduledReviews(TEST_USERNAME, TEST_LEXICON_ID, TEST_LANGUAGE.testRelationships().get(1).id(), Optional.empty())).thenReturn(scheduledReviews);

        Word word = buildWord(TEST_WORD_1_ID, false);
        List<Word> wordsToReview = List.of(word);
        when(lexiconService.loadWords(List.of(TEST_WORD_1_ID))).thenReturn(wordsToReview);

        List<WordReview> wordReviews = reviewSessionService.generateReviewSession(TEST_LEXICON_ID, Optional.of(TEST_LANGUAGE.testRelationships().get(1).id()), Optional.empty(), 0, TEST_USERNAME);

        assertEquals(1, wordReviews.size());
        WordReview review = wordReviews.get(0);
        TestRelationship relationship = TEST_LANGUAGE.testRelationships().get(1);

        assertEquals(TEST_LANGUAGE.id(), review.languageId());
        assertEquals(word, review.word());
        assertNotNull(review.scheduledEventId());
        assertEquals(relationship.testOn(), review.testOn());
        assertEquals(relationship.promptWith(), review.promptWith());
        assertEquals(relationship.showAfterTest(), review.showAfterTest());
        assertEquals(ReviewMode.TypingTest, review.reviewMode());
        assertEquals(ReviewType.Review, review.reviewType());
        assertTrue(review.recordResult());
        assertEquals(calcAllowedTime(word, ReviewMode.TypingTest, relationship.testOn()), review.allowedTimeSec());
        assertEquals(calcTypingTestButtons(word, relationship.testOn()), review.typingTestButtons());
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
            TestRelationship relationship = TEST_LANGUAGE.testRelationships().get((index + 1) % 3);

            assertEquals(TEST_LANGUAGE.id(), review.languageId());
            assertEquals(word, review.word());
            assertNotNull(review.scheduledEventId());
            assertEquals(relationship.testOn(), review.testOn());
            assertEquals(relationship.promptWith(), review.promptWith());
            assertEquals(relationship.showAfterTest(), review.showAfterTest());
            assertEquals(ReviewMode.TypingTest, review.reviewMode());
            assertEquals(ReviewType.Review, review.reviewType());
            assertTrue(review.recordResult());
            assertEquals(calcAllowedTime(word, ReviewMode.TypingTest, relationship.testOn()), review.allowedTimeSec());
            assertEquals(calcTypingTestButtons(word, relationship.testOn()), review.typingTestButtons());
            assertEquals(List.of(), review.multipleChoiceButtons());
        }
    }


    private Word buildWord(String id, boolean requiredElementsOnly) {
        Map<String, String> wordElements = new HashMap<>();
        List<WordElement> elementsToUse = requiredElementsOnly ? TEST_LANGUAGE.requiredElements() : TEST_LANGUAGE.coreElements();

        for (WordElement element : elementsToUse) {
            wordElements.put(element.id(), element.id() + "_" + id);
        }

        return new Word(id, TEST_USERNAME, wordElements, "n", List.of());
    }

    private static void verifyWordReview(WordReview wordReview, Word word, TestRelationship reviewRelationship, ReviewMode reviewMode, ReviewType reviewType, boolean recordResult, int multipleChoiceCnt) {
        assertEquals(TEST_LANGUAGE.id(), wordReview.languageId());
        assertEquals(word, wordReview.word());
        assertNull(wordReview.scheduledEventId());
        assertEquals(reviewRelationship != null ? reviewRelationship.testOn() : null, wordReview.testOn());
        assertEquals(reviewRelationship != null ? reviewRelationship.promptWith() : null, wordReview.promptWith());
        assertEquals(reviewRelationship != null ? reviewRelationship.showAfterTest() : null, wordReview.showAfterTest());
        assertEquals(reviewMode, wordReview.reviewMode());
        assertEquals(reviewType, wordReview.reviewType());
        assertEquals(recordResult, wordReview.recordResult());
        assertEquals(calcAllowedTime(word, reviewMode, wordReview.testOn()), wordReview.allowedTimeSec());
        assertEquals(reviewMode == ReviewMode.TypingTest ? calcTypingTestButtons(word, wordReview.testOn()) : List.of(), wordReview.typingTestButtons());
        assertEquals(reviewMode == ReviewMode.MultipleChoiceTest ? calcMultipleChoiceButtons(word, wordReview.testOn(), multipleChoiceCnt) : List.of(), wordReview.multipleChoiceButtons());
    }

    // generate a unique value that can be verified to be set correctly
    private static int calcAllowedTime(Word word, ReviewMode mode, String testOn) {
        return mode == ReviewMode.WordOverview ? 0 : (word.hashCode() + mode.getReviewModeId() + testOn.hashCode()) % 30;
    }

    private static List<String> calcTypingTestButtons(Word word, String element) {
        return Arrays.stream(("Typing" + word.elements().get(element)).split("(?!^)")).toList();
    }

    private static List<String> calcMultipleChoiceButtons(Word word, String element, int count) {
        List<String> multipleChoiceButtons = new ArrayList<>();

        for(int i = 0; i < count - 1; i++) {
            multipleChoiceButtons.add("Multiple" + i);
        }
        multipleChoiceButtons.add(word.elements().get(element));

        return multipleChoiceButtons;
    }

    private static DBScheduledReview buildDBScheduledReview(String wordId, int reviewRelationshipIndex, Instant scheduledTime) {
        return buildDBScheduledReview(wordId, reviewRelationshipIndex, scheduledTime, Duration.ofSeconds(60), false);
    }

    private static DBScheduledReview buildDBScheduledReview(String wordId, int reviewRelationshipIndex, Instant scheduledTime, Duration testDelay, boolean completed) {
        return new DBScheduledReview(UUID.randomUUID().toString(), TEST_USERNAME, TEST_LEXICON_ID, wordId, ReviewType.Review,
                TEST_LANGUAGE.testRelationships().get(reviewRelationshipIndex).id(), scheduledTime, testDelay, completed);
    }


}
