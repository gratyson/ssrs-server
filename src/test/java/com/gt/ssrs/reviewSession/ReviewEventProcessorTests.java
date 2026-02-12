package com.gt.ssrs.reviewSession;

import com.gt.ssrs.language.Language;
import com.gt.ssrs.language.TestRelationship;
import com.gt.ssrs.lexicon.LexiconService;
import com.gt.ssrs.reviewHistory.WordReviewHistoryService;
import com.gt.ssrs.reviewSession.model.DBReviewEvent;
import com.gt.ssrs.reviewSession.model.DBScheduledReview;
import com.gt.ssrs.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import com.gt.ssrs.word.WordService;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static com.gt.ssrs.reviewSession.ReviewEventProcessor.QUERY_BATCH_SIZE;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
public class ReviewEventProcessorTests {

    private static final Language TEST_LANGUAGE = Language.Japanese;
    private static final String TEST_USERNAME = "testUser";
    private static final String LEXICON_ID = UUID.randomUUID().toString();
    private static final String REVIEW_WORD_ID = UUID.randomUUID().toString();
    private static final String LEARNING_WORD_ID = UUID.randomUUID().toString();
    private static final long TEST_ELAPSED_TIME = 3000;
    private static final long REVIEW_WORD_LAST_DELAY_SEC = 172800;

    private static final int INITIAL_LEARNING_DELAY_SEC = 14400;
    private static final int CORRECT_NEAR_MISS_LEARNING_DELAY_SEC = 86400;
    private static final double STANDARD_INCORRECT_BOOST = 2;
    private static final double NEAR_MISS_INCORRECT_BOOST = 3;
    private static final String LAST_TEST_RELATIONSHIP_ID = TestRelationship.KanaToKanji.getId();

    @Mock private ReviewSessionDao reviewSessionDao;
    @Mock private LexiconService lexiconService;
    @Mock private WordService wordService;
    @Mock private WordReviewHistoryService wordReviewHistoryService;

    private ReviewEventProcessor reviewEventProcessor;
    private int eventIdCounter;

    @BeforeEach
    public void initTests() {
        reviewEventProcessor = new ReviewEventProcessor(reviewSessionDao, lexiconService, wordService, wordReviewHistoryService,
                INITIAL_LEARNING_DELAY_SEC, CORRECT_NEAR_MISS_LEARNING_DELAY_SEC, STANDARD_INCORRECT_BOOST, NEAR_MISS_INCORRECT_BOOST);
        eventIdCounter = 0;

        when(lexiconService.getLexiconMetadata(LEXICON_ID)).thenReturn(
                new LexiconMetadata(LEXICON_ID, TEST_USERNAME, "Test Lexicon", "Test Lexicon", TEST_LANGUAGE.getId(), ""));

        when(wordService.loadWords(Set.of(REVIEW_WORD_ID, LEARNING_WORD_ID))).thenReturn(List.of(
                buildWord(REVIEW_WORD_ID, Map.of("kana", "reviewKana", "meaning", "reviewMeaning", "kanji", "reviewKanji")),
                buildWord(LEARNING_WORD_ID, Map.of("kana", "learningKana", "meaning", "learningMeaning", "kanji", "learningKanji"))));
        when(wordService.loadWords(Set.of(LEARNING_WORD_ID))).thenReturn(List.of(
                buildWord(LEARNING_WORD_ID, Map.of("kana", "learningKana", "meaning", "learningMeaning", "kanji", "learningKanji"))));
        when(wordService.loadWords(Set.of(REVIEW_WORD_ID))).thenReturn(List.of(
                buildWord(REVIEW_WORD_ID, Map.of("kana", "reviewKana", "meaning", "reviewMeaning", "kanji", "reviewKanji"))));

        WordReviewHistory learningWordHistory = new WordReviewHistory(LEXICON_ID, TEST_USERNAME, LEARNING_WORD_ID, false, null, null, null, 0, null, Map.of());
        WordReviewHistory reviewWordHistory = new WordReviewHistory(LEXICON_ID, TEST_USERNAME, REVIEW_WORD_ID, true, Instant.now().minusSeconds(REVIEW_WORD_LAST_DELAY_SEC), LAST_TEST_RELATIONSHIP_ID, Duration.ofSeconds(REVIEW_WORD_LAST_DELAY_SEC), 0, Duration.ofSeconds(0),
                Map.of(TEST_LANGUAGE.getReviewTestRelationships().get(0).getId(), new TestHistory(1, 1, 1)));
        when(wordReviewHistoryService.getWordReviewHistory(LEXICON_ID, TEST_USERNAME, Set.of(LEARNING_WORD_ID))).thenReturn(List.of(learningWordHistory));
        when(wordReviewHistoryService.getWordReviewHistory(LEXICON_ID, TEST_USERNAME, Set.of(REVIEW_WORD_ID))).thenReturn(List.of(reviewWordHistory));
        when(wordReviewHistoryService.getWordReviewHistory(LEXICON_ID, TEST_USERNAME, Set.of(LEARNING_WORD_ID, REVIEW_WORD_ID))).thenReturn(List.of(learningWordHistory, reviewWordHistory));

    }

    @Test
    public void testProcessEvents() {
        Instant learningTestTime = Instant.now().minusSeconds(3600);
        Instant reviewTestTime = learningTestTime.plusSeconds(60);

        List<DBReviewEvent> learningEvents = List.of(
                buildLearningEvent(learningTestTime),
                buildLearningEvent(learningTestTime.minusSeconds(60)));
        List<DBReviewEvent> reviewEvents = List.of(
                buildReviewEvent(reviewTestTime, 1, true, false, false),
                buildReviewEvent(reviewTestTime.plusSeconds(60), 1, true, false, false));
        List<DBReviewEvent> events = new ArrayList<>(learningEvents);
        events.addAll(reviewEvents);

        when(reviewSessionDao.loadUnprocessedReviewEventsForUserBatch(eq(TEST_USERNAME), eq(LEXICON_ID), anyInt(), anyInt())).thenReturn(events);

        reviewEventProcessor.processEvents(TEST_USERNAME, LEXICON_ID);

        ArgumentCaptor<List<DBScheduledReview>> scheduledReviewCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<String> ownerCaptor = ArgumentCaptor.forClass(String.class);
        verify(reviewSessionDao, times(1)).createScheduledReviewsBatch(scheduledReviewCaptor.capture(), ownerCaptor.capture());
        List<DBScheduledReview> capturedScheduledReviews = scheduledReviewCaptor.getValue();
        String owner = ownerCaptor.getValue();

        assertEquals(TEST_USERNAME, owner);
        assertTrue(capturedScheduledReviews.get(0).wordId().equals(LEARNING_WORD_ID) || capturedScheduledReviews.get(0).wordId().equals(REVIEW_WORD_ID));
        assertTrue(capturedScheduledReviews.get(1).wordId().equals(LEARNING_WORD_ID) || capturedScheduledReviews.get(1).wordId().equals(REVIEW_WORD_ID));
        assertNotEquals(capturedScheduledReviews.get(0).wordId(), capturedScheduledReviews.get(1).wordId());
        for(DBScheduledReview capturedScheduledReview : capturedScheduledReviews) {
            assertNotNull(capturedScheduledReview.id());
            assertEquals(LEXICON_ID, capturedScheduledReview.lexiconId());
            assertFalse(capturedScheduledReview.completed());

            if (capturedScheduledReview.wordId().equals(LEARNING_WORD_ID)) {
                assertEquals(Duration.ofSeconds(INITIAL_LEARNING_DELAY_SEC), capturedScheduledReview.testDelay());
                assertEquals(learningTestTime.plusSeconds(INITIAL_LEARNING_DELAY_SEC), capturedScheduledReview.scheduledTestTime());
                assertEquals(TEST_LANGUAGE.getReviewTestRelationships().get(0).getId(), capturedScheduledReview.testRelationshipId());
            } else {
                // delay should increase by ~25.9%. Check for 25% < value < %26 rather than trying to determine exact value
                assertTrue(capturedScheduledReview.testDelay().toMillis() > (REVIEW_WORD_LAST_DELAY_SEC * 1000 * 1.25));
                assertTrue(capturedScheduledReview.testDelay().toMillis() < (REVIEW_WORD_LAST_DELAY_SEC * 1000 * 1.26));
                assertEquals(reviewTestTime.plus(capturedScheduledReview.testDelay()), capturedScheduledReview.scheduledTestTime());
                assertEquals(TEST_LANGUAGE.getReviewTestRelationships().get(2).getId(), capturedScheduledReview.testRelationshipId());  // has history for relation 0 and just tested relation 1, so next relation should be 2
            }
        }

        ArgumentCaptor<List<WordReviewHistory>> historyCaptor = ArgumentCaptor.forClass(List.class);
        verify(wordReviewHistoryService, times(1)).updateWordReviewHistoryBatch(eq(TEST_USERNAME), historyCaptor.capture());
        List<WordReviewHistory> capturedHistories = historyCaptor.getValue();

        assertTrue(capturedHistories.get(0).wordId().equals(LEARNING_WORD_ID) || capturedHistories.get(0).wordId().equals(REVIEW_WORD_ID));
        assertTrue(capturedHistories.get(1).wordId().equals(LEARNING_WORD_ID) || capturedHistories.get(1).wordId().equals(REVIEW_WORD_ID));
        assertNotEquals(capturedHistories.get(0).wordId(), capturedHistories.get(1).wordId());
        for(WordReviewHistory wordHistory : capturedHistories) {
            assertEquals(LEXICON_ID, wordHistory.lexiconId());
            assertTrue(wordHistory.learned());
            assertEquals(0, wordHistory.currentBoost());
            assertEquals(Duration.ofSeconds(0), wordHistory.currentBoostExpirationDelay());
            if (wordHistory.wordId().equals(LEARNING_WORD_ID)) {
                assertEquals(learningTestTime, wordHistory.mostRecentTestTime());
                assertEquals(Duration.ofSeconds(INITIAL_LEARNING_DELAY_SEC), wordHistory.currentTestDelay());
                assertEquals(Map.of(), wordHistory.testHistory());
            } else {
                assertEquals(reviewTestTime, wordHistory.mostRecentTestTime());
                assertTrue(wordHistory.currentTestDelay().toMillis() > (REVIEW_WORD_LAST_DELAY_SEC * 1000 * 1.25));
                assertTrue(wordHistory.currentTestDelay().toMillis() < (REVIEW_WORD_LAST_DELAY_SEC * 1000 * 1.26));
                assertEquals(Map.of(TEST_LANGUAGE.getReviewTestRelationships().get(0).getId(), new TestHistory(1, 1, 1),
                                    TEST_LANGUAGE.getReviewTestRelationships().get(1).getId(), new TestHistory(1, 1, 1)),
                        wordHistory.testHistory());
            }
        }

        verify(reviewSessionDao).markEventsAsProcessed(events);
        verify(reviewSessionDao, times(1)).markEventsAsProcessed(anyList());
    }

    @Test
    public void testProcessEvents_NotReviewOrLearn() {
        String wordId = UUID.randomUUID().toString();

        when(wordService.loadWords(Set.of(wordId))).thenReturn(List.of(
                buildWord(wordId, Map.of("kana", "kanaVal", "meaning", "meaningVal"))));

        List<DBReviewEvent> events = List.of(buildDBReviewEvent(
                wordId, Instant.now(), ReviewType.None, "kana", "meaning", true, false, false));
        when(reviewSessionDao.loadUnprocessedReviewEventsForUserBatch(eq(TEST_USERNAME), eq(LEXICON_ID), anyInt(), anyInt())).thenReturn(events);

        when(wordReviewHistoryService.getWordReviewHistory(LEXICON_ID, TEST_USERNAME, Set.of(wordId))).thenReturn(
                List.of(new WordReviewHistory(LEXICON_ID, TEST_USERNAME, wordId, false, null, null,null, 0, null, Map.of())));

        reviewEventProcessor.processEvents(TEST_USERNAME, LEXICON_ID);

        verify(reviewSessionDao, times(0)).createScheduledReviewsBatch(anyList(), eq(TEST_USERNAME));
        verify(wordReviewHistoryService, times(0)).updateWordReviewHistoryBatch(eq(TEST_USERNAME), anyList());
        verify(reviewSessionDao).markEventsAsProcessed(events);
    }


    @Test
    public void testProcessEvents_AlreadyLearned() {
        when(wordReviewHistoryService.getWordReviewHistory(LEXICON_ID, TEST_USERNAME, Set.of(LEARNING_WORD_ID))).thenReturn(
                List.of(new WordReviewHistory(LEXICON_ID, TEST_USERNAME, LEARNING_WORD_ID, true, Instant.now().minusSeconds(REVIEW_WORD_LAST_DELAY_SEC),
                        LAST_TEST_RELATIONSHIP_ID, Duration.ofSeconds(REVIEW_WORD_LAST_DELAY_SEC), 0, Duration.ofSeconds(0),
                        Map.of(TEST_LANGUAGE.getReviewTestRelationships().get(0).getId(), new TestHistory(1, 1, 1)))));

        List<DBReviewEvent> events = List.of(buildLearningEvent(Instant.now()));

        when(reviewSessionDao.loadUnprocessedReviewEventsForUserBatch(eq(TEST_USERNAME), eq(LEXICON_ID), anyInt(), anyInt())).thenReturn(events);

        reviewEventProcessor.processEvents(TEST_USERNAME, LEXICON_ID);

        verify(reviewSessionDao, times(0)).createScheduledReviewsBatch(anyList(), eq(TEST_USERNAME));
        verify(wordReviewHistoryService, times(0)).updateWordReviewHistoryBatch(eq(TEST_USERNAME), anyList());
        verify(reviewSessionDao).markEventsAsProcessed(events);
    }

    @Test
    public void testProcessEvents_CorrectNearMiss() {

        Instant reviewInstant = Instant.now();
        List<DBReviewEvent> events = List.of(buildReviewEvent(reviewInstant, 1, true, true, false));
        when(reviewSessionDao.loadUnprocessedReviewEventsForUserBatch(eq(TEST_USERNAME), eq(LEXICON_ID), anyInt(), anyInt())).thenReturn(events);

        reviewEventProcessor.processEvents(TEST_USERNAME, LEXICON_ID);

        ArgumentCaptor<List<DBScheduledReview>> scheduledReviewCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<String> ownerCaptor = ArgumentCaptor.forClass(String.class);
        verify(reviewSessionDao, times(1)).createScheduledReviewsBatch(scheduledReviewCaptor.capture(), ownerCaptor.capture());
        assertEquals(TEST_USERNAME, ownerCaptor.getValue());
        List<DBScheduledReview> capturedScheduledReviews = scheduledReviewCaptor.getValue();
        assertEquals(1, capturedScheduledReviews.size());
        DBScheduledReview capturedScheduledReview = capturedScheduledReviews.get(0);

        assertNotNull(capturedScheduledReview.id());
        assertEquals(LEXICON_ID, capturedScheduledReview.lexiconId());
        assertFalse(capturedScheduledReview.completed());
        assertEquals(Duration.ofSeconds(CORRECT_NEAR_MISS_LEARNING_DELAY_SEC), capturedScheduledReview.testDelay());
        assertEquals(reviewInstant.plusSeconds(CORRECT_NEAR_MISS_LEARNING_DELAY_SEC), capturedScheduledReview.scheduledTestTime());
        assertEquals(TEST_LANGUAGE.getReviewTestRelationships().get(2).getId(), capturedScheduledReview.testRelationshipId());

        ArgumentCaptor<List<WordReviewHistory>> historyCaptor = ArgumentCaptor.forClass(List.class);
        verify(wordReviewHistoryService, times(1)).updateWordReviewHistoryBatch(eq(TEST_USERNAME), historyCaptor.capture());
        List<WordReviewHistory> capturedHistories = historyCaptor.getValue();
        assertEquals(1, capturedHistories.size());
        WordReviewHistory wordHistory = capturedHistories.get(0);

        assertEquals(LEXICON_ID, wordHistory.lexiconId());
        assertTrue(wordHistory.learned());
        assertEquals(NEAR_MISS_INCORRECT_BOOST, wordHistory.currentBoost());
        assertEquals(Duration.ofSeconds(REVIEW_WORD_LAST_DELAY_SEC), wordHistory.currentBoostExpirationDelay());
        assertEquals(reviewInstant, wordHistory.mostRecentTestTime());
        assertEquals(Duration.ofSeconds(CORRECT_NEAR_MISS_LEARNING_DELAY_SEC), wordHistory.currentTestDelay());
        assertEquals(Map.of(TEST_LANGUAGE.getReviewTestRelationships().get(0).getId(), new TestHistory(1, 1, 1),
                            TEST_LANGUAGE.getReviewTestRelationships().get(1).getId(), new TestHistory(1, 1, 1)),
                wordHistory.testHistory());
    }

    @Test
    public void testProcessEvents_CorrectNearMissRecentlyLearned() {
        int lastReviewDelaySec = 14400;

        when(wordReviewHistoryService.getWordReviewHistory(LEXICON_ID, TEST_USERNAME, Set.of(REVIEW_WORD_ID))).thenReturn(
                List.of(new WordReviewHistory(LEXICON_ID, TEST_USERNAME, REVIEW_WORD_ID, true, Instant.now().minusSeconds(lastReviewDelaySec),
                        LAST_TEST_RELATIONSHIP_ID, Duration.ofSeconds(lastReviewDelaySec), 0, Duration.ofSeconds(0),
                        Map.of(TEST_LANGUAGE.getReviewTestRelationships().get(0).getId(), new TestHistory(1, 1, 1)))));

        Instant reviewInstant = Instant.now();
        List<DBReviewEvent> events = List.of(buildReviewEvent(reviewInstant, 1, true, true, false));
        when(reviewSessionDao.loadUnprocessedReviewEventsForUserBatch(eq(TEST_USERNAME), eq(LEXICON_ID), anyInt(), anyInt())).thenReturn(events);

        reviewEventProcessor.processEvents(TEST_USERNAME, LEXICON_ID);

        ArgumentCaptor<List<DBScheduledReview>> scheduledReviewCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<String> ownerCaptor = ArgumentCaptor.forClass(String.class);
        verify(reviewSessionDao, times(1)).createScheduledReviewsBatch(scheduledReviewCaptor.capture(), ownerCaptor.capture());
        assertEquals(TEST_USERNAME, ownerCaptor.getValue());
        List<DBScheduledReview> capturedScheduledReviews = scheduledReviewCaptor.getValue();
        assertEquals(1, capturedScheduledReviews.size());
        DBScheduledReview capturedScheduledReview = capturedScheduledReviews.get(0);

        assertNotNull(capturedScheduledReview.id());
        assertEquals(LEXICON_ID, capturedScheduledReview.lexiconId());
        assertFalse(capturedScheduledReview.completed());
        assertTrue(capturedScheduledReview.testDelay().toMillis() > (lastReviewDelaySec * 1000 * 1.25));
        assertTrue(capturedScheduledReview.testDelay().toMillis() < (lastReviewDelaySec * 1000 * 1.26));
        assertEquals(reviewInstant.plus(capturedScheduledReview.testDelay()), capturedScheduledReview.scheduledTestTime());
        assertEquals(TEST_LANGUAGE.getReviewTestRelationships().get(2).getId(), capturedScheduledReview.testRelationshipId());

        ArgumentCaptor<List<WordReviewHistory>> historyCaptor = ArgumentCaptor.forClass(List.class);
        verify(wordReviewHistoryService, times(1)).updateWordReviewHistoryBatch(eq(TEST_USERNAME), historyCaptor.capture());
        List<WordReviewHistory> capturedHistories = historyCaptor.getValue();
        assertEquals(1, capturedHistories.size());
        WordReviewHistory wordHistory = capturedHistories.get(0);

        assertEquals(LEXICON_ID, wordHistory.lexiconId());
        assertTrue(wordHistory.learned());
        assertEquals(0, wordHistory.currentBoost());
        assertEquals(Duration.ofSeconds(0), wordHistory.currentBoostExpirationDelay());
        assertEquals(reviewInstant, wordHistory.mostRecentTestTime());
        assertTrue(wordHistory.currentTestDelay().toMillis() > (lastReviewDelaySec * 1000 * 1.25));
        assertTrue(wordHistory.currentTestDelay().toMillis() < (lastReviewDelaySec * 1000 * 1.26));
        assertEquals(Map.of(TEST_LANGUAGE.getReviewTestRelationships().get(0).getId(), new TestHistory(1, 1, 1),
                            TEST_LANGUAGE.getReviewTestRelationships().get(1).getId(), new TestHistory(1, 1, 1)),
                wordHistory.testHistory());
    }

    @Test
    public void testProcessEvents_CorrectNearMissExistingBoost() {
        long currentBoostDurationSec = REVIEW_WORD_LAST_DELAY_SEC * 2;

        when(wordReviewHistoryService.getWordReviewHistory(LEXICON_ID, TEST_USERNAME, Set.of(REVIEW_WORD_ID))).thenReturn(
                List.of(new WordReviewHistory(LEXICON_ID, TEST_USERNAME, REVIEW_WORD_ID, true, Instant.now().minusSeconds(REVIEW_WORD_LAST_DELAY_SEC),
                        LAST_TEST_RELATIONSHIP_ID, Duration.ofSeconds(REVIEW_WORD_LAST_DELAY_SEC), 3, Duration.ofSeconds(currentBoostDurationSec),
                        Map.of(TEST_LANGUAGE.getReviewTestRelationships().get(0).getId(), new TestHistory(1, 1, 1)))));

        Instant reviewInstant = Instant.now();
        List<DBReviewEvent> events = List.of(buildReviewEvent(reviewInstant, 1, true, true, false));
        when(reviewSessionDao.loadUnprocessedReviewEventsForUserBatch(eq(TEST_USERNAME), eq(LEXICON_ID), anyInt(), anyInt())).thenReturn(events);

        reviewEventProcessor.processEvents(TEST_USERNAME, LEXICON_ID);

        ArgumentCaptor<List<WordReviewHistory>> historyCaptor = ArgumentCaptor.forClass(List.class);
        verify(wordReviewHistoryService, times(1)).updateWordReviewHistoryBatch(eq(TEST_USERNAME), historyCaptor.capture());
        List<WordReviewHistory> capturedHistories = historyCaptor.getValue();
        assertEquals(1, capturedHistories.size());
        WordReviewHistory wordHistory = capturedHistories.get(0);

        assertEquals(NEAR_MISS_INCORRECT_BOOST, wordHistory.currentBoost());
        assertEquals(Duration.ofSeconds(currentBoostDurationSec), wordHistory.currentBoostExpirationDelay());
    }

    @Test
    public void testProcessEvent_Incorrect() {
        Instant reviewInstant = Instant.now();
        List<DBReviewEvent> events = List.of(buildReviewEvent(reviewInstant,0, false, false, false));

        when(reviewSessionDao.loadUnprocessedReviewEventsForUserBatch(eq(TEST_USERNAME), eq(LEXICON_ID), anyInt(), anyInt())).thenReturn(events);

        reviewEventProcessor.processEvents(TEST_USERNAME, LEXICON_ID);

        ArgumentCaptor<List<DBScheduledReview>> scheduledReviewCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<String> ownerCaptor = ArgumentCaptor.forClass(String.class);
        verify(reviewSessionDao, times(1)).createScheduledReviewsBatch(scheduledReviewCaptor.capture(), ownerCaptor.capture());
        assertEquals(TEST_USERNAME, ownerCaptor.getValue());
        assertEquals(1, scheduledReviewCaptor.getValue().size());
        DBScheduledReview capturedScheduledReview = scheduledReviewCaptor.getValue().get(0);

        assertNotNull(capturedScheduledReview.id());
        assertEquals(LEXICON_ID, capturedScheduledReview.lexiconId());
        assertEquals(REVIEW_WORD_ID, capturedScheduledReview.wordId());
        assertEquals(TEST_LANGUAGE.getReviewTestRelationships().get(1).getId(), capturedScheduledReview.testRelationshipId());
        assertEquals(reviewInstant.plusSeconds(INITIAL_LEARNING_DELAY_SEC), capturedScheduledReview.scheduledTestTime());
        assertEquals(Duration.ofSeconds(INITIAL_LEARNING_DELAY_SEC), capturedScheduledReview.testDelay());

        ArgumentCaptor<List<WordReviewHistory>> historyCaptor = ArgumentCaptor.forClass(List.class);
        verify(wordReviewHistoryService, times(1)).updateWordReviewHistoryBatch(eq(TEST_USERNAME), historyCaptor.capture());
        assertEquals(1, historyCaptor.getValue().size());
        WordReviewHistory capturedHistory = historyCaptor.getValue().get(0);

        assertEquals(LEXICON_ID, capturedHistory.lexiconId());
        assertEquals(REVIEW_WORD_ID, capturedHistory.wordId());
        assertTrue(capturedHistory.learned());
        assertEquals(reviewInstant, capturedHistory.mostRecentTestTime());
        assertEquals(Duration.ofSeconds(INITIAL_LEARNING_DELAY_SEC), capturedHistory.currentTestDelay());
        assertEquals(STANDARD_INCORRECT_BOOST, capturedHistory.currentBoost());
        assertEquals(Duration.ofSeconds(REVIEW_WORD_LAST_DELAY_SEC), capturedHistory.currentBoostExpirationDelay());
        assertEquals(Map.of(TEST_LANGUAGE.getReviewTestRelationships().get(0).getId(), new TestHistory(2, 1, 0)), capturedHistory.testHistory());

        verify(reviewSessionDao).markEventsAsProcessed(events);
    }

    @Test
    public void testProcessEvent_IncorrectNearMiss() {
        Instant reviewInstant = Instant.now();
        List<DBReviewEvent> events = List.of(buildReviewEvent(reviewInstant,0, false, true, false));

        when(reviewSessionDao.loadUnprocessedReviewEventsForUserBatch(eq(TEST_USERNAME), eq(LEXICON_ID), anyInt(), anyInt())).thenReturn(events);

        reviewEventProcessor.processEvents(TEST_USERNAME, LEXICON_ID);

        ArgumentCaptor<List<DBScheduledReview>> scheduledReviewCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<String> ownerCaptor = ArgumentCaptor.forClass(String.class);
        verify(reviewSessionDao, times(1)).createScheduledReviewsBatch(scheduledReviewCaptor.capture(), ownerCaptor.capture());
        assertEquals(TEST_USERNAME, ownerCaptor.getValue());
        assertEquals(1, scheduledReviewCaptor.getValue().size());
        DBScheduledReview capturedScheduledReview = scheduledReviewCaptor.getValue().get(0);

        assertNotNull(capturedScheduledReview.id());
        assertEquals(LEXICON_ID, capturedScheduledReview.lexiconId());
        assertEquals(REVIEW_WORD_ID, capturedScheduledReview.wordId());
        assertEquals(TEST_LANGUAGE.getReviewTestRelationships().get(1).getId(), capturedScheduledReview.testRelationshipId());
        assertEquals(reviewInstant.plusSeconds(INITIAL_LEARNING_DELAY_SEC), capturedScheduledReview.scheduledTestTime());
        assertEquals(Duration.ofSeconds(INITIAL_LEARNING_DELAY_SEC), capturedScheduledReview.testDelay());

        ArgumentCaptor<List<WordReviewHistory>> historyCaptor = ArgumentCaptor.forClass(List.class);
        verify(wordReviewHistoryService, times(1)).updateWordReviewHistoryBatch(eq(TEST_USERNAME), historyCaptor.capture());
        assertEquals(1, historyCaptor.getValue().size());
        WordReviewHistory capturedHistory = historyCaptor.getValue().get(0);

        assertEquals(LEXICON_ID, capturedHistory.lexiconId());
        assertEquals(REVIEW_WORD_ID, capturedHistory.wordId());
        assertTrue(capturedHistory.learned());
        assertEquals(reviewInstant, capturedHistory.mostRecentTestTime());
        assertEquals(Duration.ofSeconds(INITIAL_LEARNING_DELAY_SEC), capturedHistory.currentTestDelay());
        assertEquals(NEAR_MISS_INCORRECT_BOOST, capturedHistory.currentBoost());
        assertEquals(Duration.ofSeconds(REVIEW_WORD_LAST_DELAY_SEC), capturedHistory.currentBoostExpirationDelay());
        assertEquals(Map.of(TEST_LANGUAGE.getReviewTestRelationships().get(0).getId(), new TestHistory(2, 1, 0)), capturedHistory.testHistory());

        verify(reviewSessionDao).markEventsAsProcessed(events);
    }

    @Test
    public void testProcessEvent_Override() {
        // Override a correct answer with an incorrect
        Instant reviewInstantOverride = Instant.now();
        List<DBReviewEvent> events = List.of(
                buildReviewEvent(reviewInstantOverride.minusSeconds(60),0, true, false, false),
                buildReviewEvent(reviewInstantOverride,0, false, false, true));

        when(reviewSessionDao.loadUnprocessedReviewEventsForUserBatch(eq(TEST_USERNAME), eq(LEXICON_ID), anyInt(), anyInt())).thenReturn(events);

        reviewEventProcessor.processEvents(TEST_USERNAME, LEXICON_ID);

        ArgumentCaptor<List<DBScheduledReview>> scheduledReviewCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<String> ownerCaptor = ArgumentCaptor.forClass(String.class);
        verify(reviewSessionDao, times(1)).createScheduledReviewsBatch(scheduledReviewCaptor.capture(), ownerCaptor.capture());
        assertEquals(TEST_USERNAME, ownerCaptor.getValue());
        assertEquals(1, scheduledReviewCaptor.getValue().size());
        DBScheduledReview capturedScheduledReview = scheduledReviewCaptor.getValue().get(0);

        assertNotNull(capturedScheduledReview.id());
        assertEquals(LEXICON_ID, capturedScheduledReview.lexiconId());
        assertEquals(REVIEW_WORD_ID, capturedScheduledReview.wordId());
        assertEquals(TEST_LANGUAGE.getReviewTestRelationships().get(1).getId(), capturedScheduledReview.testRelationshipId());
        assertEquals(reviewInstantOverride.plusSeconds(INITIAL_LEARNING_DELAY_SEC), capturedScheduledReview.scheduledTestTime());
        assertEquals(Duration.ofSeconds(INITIAL_LEARNING_DELAY_SEC), capturedScheduledReview.testDelay());

        ArgumentCaptor<List<WordReviewHistory>> historyCaptor = ArgumentCaptor.forClass(List.class);
        verify(wordReviewHistoryService, times(1)).updateWordReviewHistoryBatch(eq(TEST_USERNAME), historyCaptor.capture());
        assertEquals(1, historyCaptor.getValue().size());
        WordReviewHistory capturedHistory = historyCaptor.getValue().get(0);

        assertEquals(LEXICON_ID, capturedHistory.lexiconId());
        assertEquals(REVIEW_WORD_ID, capturedHistory.wordId());
        assertTrue(capturedHistory.learned());
        assertEquals(reviewInstantOverride, capturedHistory.mostRecentTestTime());
        assertEquals(Duration.ofSeconds(INITIAL_LEARNING_DELAY_SEC), capturedHistory.currentTestDelay());
        assertEquals(STANDARD_INCORRECT_BOOST, capturedHistory.currentBoost());
        assertEquals(Duration.ofSeconds(REVIEW_WORD_LAST_DELAY_SEC), capturedHistory.currentBoostExpirationDelay());
        assertEquals(Map.of(TEST_LANGUAGE.getReviewTestRelationships().get(0).getId(), new TestHistory(2, 1, 0)), capturedHistory.testHistory());

        verify(reviewSessionDao).markEventsAsProcessed(events);
    }

    @Test
    public void testProcess_NoConsecutiveRelationship() {
        when(wordReviewHistoryService.getWordReviewHistory(LEXICON_ID, TEST_USERNAME, Set.of(REVIEW_WORD_ID))).thenReturn(
                List.of(new WordReviewHistory(LEXICON_ID, TEST_USERNAME, REVIEW_WORD_ID, true, Instant.now().minusSeconds(REVIEW_WORD_LAST_DELAY_SEC),
                        LAST_TEST_RELATIONSHIP_ID, Duration.ofSeconds(REVIEW_WORD_LAST_DELAY_SEC), 0, Duration.ofSeconds(0),
                        Map.of(TEST_LANGUAGE.getReviewTestRelationships().get(0).getId(), new TestHistory(1, 1, 1),
                               TEST_LANGUAGE.getReviewTestRelationships().get(1).getId(), new TestHistory(5, 5, 5),
                               TEST_LANGUAGE.getReviewTestRelationships().get(2).getId(), new TestHistory(3, 3, 3)))));

        Instant reviewInstant = Instant.now();
        List<DBReviewEvent> events = List.of(
                buildReviewEvent(reviewInstant,0, true, false, false));

        when(reviewSessionDao.loadUnprocessedReviewEventsForUserBatch(eq(TEST_USERNAME), eq(LEXICON_ID), anyInt(), anyInt())).thenReturn(events);

        reviewEventProcessor.processEvents(TEST_USERNAME, LEXICON_ID);

        ArgumentCaptor<List<DBScheduledReview>> scheduledReviewCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<String> ownerCaptor = ArgumentCaptor.forClass(String.class);
        verify(reviewSessionDao, times(1)).createScheduledReviewsBatch(scheduledReviewCaptor.capture(), ownerCaptor.capture());
        assertEquals(TEST_USERNAME, ownerCaptor.getValue());
        assertEquals(1, scheduledReviewCaptor.getValue().size());
        DBScheduledReview capturedScheduledReview = scheduledReviewCaptor.getValue().get(0);

        assertEquals(TEST_LANGUAGE.getReviewTestRelationships().get(2).getId(), capturedScheduledReview.testRelationshipId());

        ArgumentCaptor<List<WordReviewHistory>> historyCaptor = ArgumentCaptor.forClass(List.class);
        verify(wordReviewHistoryService, times(1)).updateWordReviewHistoryBatch(eq(TEST_USERNAME), historyCaptor.capture());
        assertEquals(1, historyCaptor.getValue().size());
        WordReviewHistory capturedHistory = historyCaptor.getValue().get(0);

        assertEquals(Map.of(TEST_LANGUAGE.getReviewTestRelationships().get(0).getId(), new TestHistory(2, 2, 2),
                            TEST_LANGUAGE.getReviewTestRelationships().get(1).getId(), new TestHistory(5, 5, 5),
                            TEST_LANGUAGE.getReviewTestRelationships().get(2).getId(), new TestHistory(3, 3, 3)),
                capturedHistory.testHistory());
    }

    @Test
    public void testProcess_OnlyOneValidRelationship() {
        when(wordService.loadWords(Set.of(REVIEW_WORD_ID))).thenReturn(List.of(
                buildWord(REVIEW_WORD_ID, Map.of("kana", "kanaVal", "meaning", "meaningVal"))));

        Instant reviewInstant = Instant.now();
        List<DBReviewEvent> events = List.of(
                buildReviewEvent(reviewInstant,0, true, false, false));

        when(reviewSessionDao.loadUnprocessedReviewEventsForUserBatch(eq(TEST_USERNAME), eq(LEXICON_ID), anyInt(), anyInt())).thenReturn(events);

        reviewEventProcessor.processEvents(TEST_USERNAME, LEXICON_ID);

        ArgumentCaptor<List<DBScheduledReview>> scheduledReviewCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<String> ownerCaptor = ArgumentCaptor.forClass(String.class);
        verify(reviewSessionDao, times(1)).createScheduledReviewsBatch(scheduledReviewCaptor.capture(), ownerCaptor.capture());
        assertEquals(TEST_USERNAME, ownerCaptor.getValue());
        assertEquals(1, scheduledReviewCaptor.getValue().size());
        DBScheduledReview capturedScheduledReview = scheduledReviewCaptor.getValue().get(0);

        assertEquals(TEST_LANGUAGE.getReviewTestRelationships().get(0).getId(), capturedScheduledReview.testRelationshipId());

        ArgumentCaptor<List<WordReviewHistory>> historyCaptor = ArgumentCaptor.forClass(List.class);
        verify(wordReviewHistoryService, times(1)).updateWordReviewHistoryBatch(eq(TEST_USERNAME), historyCaptor.capture());
        assertEquals(1, historyCaptor.getValue().size());
        WordReviewHistory capturedHistory = historyCaptor.getValue().get(0);

        assertEquals(Map.of(TEST_LANGUAGE.getReviewTestRelationships().get(0).getId(), new TestHistory(2, 2, 2)), capturedHistory.testHistory());
    }

    @Test
    public void testProcess_NormalBoosted() {
        when(wordReviewHistoryService.getWordReviewHistory(LEXICON_ID, TEST_USERNAME, Set.of(REVIEW_WORD_ID))).thenReturn(
                List.of(new WordReviewHistory(LEXICON_ID, TEST_USERNAME, REVIEW_WORD_ID, true, Instant.now().minusSeconds(REVIEW_WORD_LAST_DELAY_SEC),
                        LAST_TEST_RELATIONSHIP_ID, Duration.ofSeconds(REVIEW_WORD_LAST_DELAY_SEC), STANDARD_INCORRECT_BOOST, Duration.ofSeconds(REVIEW_WORD_LAST_DELAY_SEC * 2),
                        Map.of(TEST_LANGUAGE.getReviewTestRelationships().get(0).getId(), new TestHistory(1, 1, 1)))));

        Instant reviewInstant = Instant.now();
        List<DBReviewEvent> events = List.of(
                buildReviewEvent(reviewInstant,0, true, false, false));

        when(reviewSessionDao.loadUnprocessedReviewEventsForUserBatch(eq(TEST_USERNAME), eq(LEXICON_ID), anyInt(), anyInt())).thenReturn(events);

        reviewEventProcessor.processEvents(TEST_USERNAME, LEXICON_ID);

        ArgumentCaptor<List<DBScheduledReview>> scheduledReviewCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<String> ownerCaptor = ArgumentCaptor.forClass(String.class);
        verify(reviewSessionDao, times(1)).createScheduledReviewsBatch(scheduledReviewCaptor.capture(), ownerCaptor.capture());
        assertEquals(TEST_USERNAME, ownerCaptor.getValue());
        assertEquals(1, scheduledReviewCaptor.getValue().size());
        DBScheduledReview capturedScheduledReview = scheduledReviewCaptor.getValue().get(0);

        // Double boost should increase delay by ~58.5%
        assertTrue(capturedScheduledReview.testDelay().toMillis() > (REVIEW_WORD_LAST_DELAY_SEC * 1000 * 1.58));
        assertTrue(capturedScheduledReview.testDelay().toMillis() < (REVIEW_WORD_LAST_DELAY_SEC * 1000 * 1.59));

        ArgumentCaptor<List<WordReviewHistory>> historyCaptor = ArgumentCaptor.forClass(List.class);
        verify(wordReviewHistoryService, times(1)).updateWordReviewHistoryBatch(eq(TEST_USERNAME), historyCaptor.capture());
        assertEquals(1, historyCaptor.getValue().size());
        WordReviewHistory capturedHistory = historyCaptor.getValue().get(0);

        assertTrue(capturedHistory.currentTestDelay().toMillis() > (REVIEW_WORD_LAST_DELAY_SEC * 1000 * 1.58));
        assertTrue(capturedHistory.currentTestDelay().toMillis() < (REVIEW_WORD_LAST_DELAY_SEC * 1000 * 1.59));
        assertEquals(STANDARD_INCORRECT_BOOST, capturedHistory.currentBoost());
        assertEquals(Duration.ofSeconds(REVIEW_WORD_LAST_DELAY_SEC * 2), capturedHistory.currentBoostExpirationDelay());
    }

    @Test
    public void testProcess_ExtraBoosted() {
        when(wordReviewHistoryService.getWordReviewHistory(LEXICON_ID, TEST_USERNAME, Set.of(REVIEW_WORD_ID))).thenReturn(
                List.of(new WordReviewHistory(LEXICON_ID, TEST_USERNAME, REVIEW_WORD_ID, true, Instant.now().minusSeconds(REVIEW_WORD_LAST_DELAY_SEC),
                        LAST_TEST_RELATIONSHIP_ID, Duration.ofSeconds(REVIEW_WORD_LAST_DELAY_SEC), NEAR_MISS_INCORRECT_BOOST, Duration.ofSeconds(REVIEW_WORD_LAST_DELAY_SEC * 2),
                        Map.of(TEST_LANGUAGE.getReviewTestRelationships().get(0).getId(), new TestHistory(1, 1, 1)))));

        Instant reviewInstant = Instant.now();
        List<DBReviewEvent> events = List.of(
                buildReviewEvent(reviewInstant,0, true, false, false));

        when(reviewSessionDao.loadUnprocessedReviewEventsForUserBatch(eq(TEST_USERNAME), eq(LEXICON_ID), anyInt(), anyInt())).thenReturn(events);

        reviewEventProcessor.processEvents(TEST_USERNAME, LEXICON_ID);

        ArgumentCaptor<List<DBScheduledReview>> scheduledReviewCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<String> ownerCaptor = ArgumentCaptor.forClass(String.class);
        verify(reviewSessionDao, times(1)).createScheduledReviewsBatch(scheduledReviewCaptor.capture(), ownerCaptor.capture());
        assertEquals(TEST_USERNAME, ownerCaptor.getValue());
        assertEquals(1, scheduledReviewCaptor.getValue().size());
        DBScheduledReview capturedScheduledReview = scheduledReviewCaptor.getValue().get(0);

        // Triple boost should exactly double the delay
        assertEquals(Duration.ofSeconds(REVIEW_WORD_LAST_DELAY_SEC * 2), capturedScheduledReview.testDelay());

        ArgumentCaptor<List<WordReviewHistory>> historyCaptor = ArgumentCaptor.forClass(List.class);
        verify(wordReviewHistoryService, times(1)).updateWordReviewHistoryBatch(eq(TEST_USERNAME), historyCaptor.capture());
        assertEquals(1, historyCaptor.getValue().size());
        WordReviewHistory capturedHistory = historyCaptor.getValue().get(0);

        assertEquals(Duration.ofSeconds(REVIEW_WORD_LAST_DELAY_SEC * 2), capturedHistory.currentTestDelay());
        // Boost expired but exactly matches the expiration delay
        assertEquals(0, capturedHistory.currentBoost());
        assertEquals(Duration.ofSeconds(0), capturedHistory.currentBoostExpirationDelay());
    }

    @Test
    public void testProcess_BoostExpiredStandardDelayLessThanExpiration() {
        Duration boostExpirationDuration = Duration.ofSeconds((long)(REVIEW_WORD_LAST_DELAY_SEC * 1.5));

        when(wordReviewHistoryService.getWordReviewHistory(LEXICON_ID, TEST_USERNAME, Set.of(REVIEW_WORD_ID))).thenReturn(
                List.of(new WordReviewHistory(LEXICON_ID, TEST_USERNAME, REVIEW_WORD_ID, true, Instant.now().minusSeconds(REVIEW_WORD_LAST_DELAY_SEC),
                        LAST_TEST_RELATIONSHIP_ID, Duration.ofSeconds(REVIEW_WORD_LAST_DELAY_SEC), STANDARD_INCORRECT_BOOST, boostExpirationDuration,
                        Map.of(TEST_LANGUAGE.getReviewTestRelationships().get(0).getId(), new TestHistory(1, 1, 1)))));

        Instant reviewInstant = Instant.now();
        List<DBReviewEvent> events = List.of(
                buildReviewEvent(reviewInstant,0, true, false, false));

        when(reviewSessionDao.loadUnprocessedReviewEventsForUserBatch(eq(TEST_USERNAME), eq(LEXICON_ID), anyInt(), anyInt())).thenReturn(events);

        reviewEventProcessor.processEvents(TEST_USERNAME, LEXICON_ID);

        ArgumentCaptor<List<DBScheduledReview>> scheduledReviewCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<String> ownerCaptor = ArgumentCaptor.forClass(String.class);
        verify(reviewSessionDao, times(1)).createScheduledReviewsBatch(scheduledReviewCaptor.capture(), ownerCaptor.capture());
        assertEquals(TEST_USERNAME, ownerCaptor.getValue());
        assertEquals(1, scheduledReviewCaptor.getValue().size());
        DBScheduledReview capturedScheduledReview = scheduledReviewCaptor.getValue().get(0);

        // Standard delay is ~25.9%, double boost is ~58.5%, boost expires at 50%. Expected to use the boost expiration as the new delay
        assertEquals(boostExpirationDuration, capturedScheduledReview.testDelay());

        ArgumentCaptor<List<WordReviewHistory>> historyCaptor = ArgumentCaptor.forClass(List.class);
        verify(wordReviewHistoryService, times(1)).updateWordReviewHistoryBatch(eq(TEST_USERNAME), historyCaptor.capture());
        assertEquals(1, historyCaptor.getValue().size());
        WordReviewHistory capturedHistory = historyCaptor.getValue().get(0);

        assertEquals(boostExpirationDuration, capturedHistory.currentTestDelay());
        assertEquals(0, capturedHistory.currentBoost());
        assertEquals(Duration.ofSeconds(0), capturedHistory.currentBoostExpirationDelay());
    }

    @Test
    public void testProcess_BoostExpiredStandardDelayGreaterThanExpiration() {
        when(wordReviewHistoryService.getWordReviewHistory(LEXICON_ID, TEST_USERNAME, Set.of(REVIEW_WORD_ID))).thenReturn(
                List.of(new WordReviewHistory(LEXICON_ID, TEST_USERNAME, REVIEW_WORD_ID, true, Instant.now().minusSeconds(REVIEW_WORD_LAST_DELAY_SEC),
                        LAST_TEST_RELATIONSHIP_ID, Duration.ofSeconds(REVIEW_WORD_LAST_DELAY_SEC), STANDARD_INCORRECT_BOOST, Duration.ofSeconds((long)(REVIEW_WORD_LAST_DELAY_SEC * 1.1)),
                        Map.of(TEST_LANGUAGE.getReviewTestRelationships().get(0).getId(), new TestHistory(1, 1, 1)))));

        when(wordService.loadWords(Set.of(REVIEW_WORD_ID))).thenReturn(List.of(
                buildWord(REVIEW_WORD_ID, Map.of("kana", "kanaVal", "meaning", "meaningVal", "kanji", "kanjiVal"))));

        Instant reviewInstant = Instant.now();
        List<DBReviewEvent> events = List.of(
                buildReviewEvent(reviewInstant,0, true, false, false));

        when(reviewSessionDao.loadUnprocessedReviewEventsForUserBatch(eq(TEST_USERNAME), eq(LEXICON_ID), anyInt(), anyInt())).thenReturn(events);

        reviewEventProcessor.processEvents(TEST_USERNAME, LEXICON_ID);

        ArgumentCaptor<List<DBScheduledReview>> scheduledReviewCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<String> ownerCaptor = ArgumentCaptor.forClass(String.class);
        verify(reviewSessionDao, times(1)).createScheduledReviewsBatch(scheduledReviewCaptor.capture(), ownerCaptor.capture());
        assertEquals(TEST_USERNAME, ownerCaptor.getValue());
        assertEquals(1, scheduledReviewCaptor.getValue().size());
        DBScheduledReview capturedScheduledReview = scheduledReviewCaptor.getValue().get(0);

        // Standard delay is ~25.9%, double boost is ~58.5%, boost expires at 10%. Expected to use the standard delay
        assertTrue(capturedScheduledReview.testDelay().toMillis() > (REVIEW_WORD_LAST_DELAY_SEC * 1000 * 1.25));
        assertTrue(capturedScheduledReview.testDelay().toMillis() < (REVIEW_WORD_LAST_DELAY_SEC * 1000 * 1.26));

        ArgumentCaptor<List<WordReviewHistory>> historyCaptor = ArgumentCaptor.forClass(List.class);
        verify(wordReviewHistoryService, times(1)).updateWordReviewHistoryBatch(eq(TEST_USERNAME), historyCaptor.capture());
        assertEquals(1, historyCaptor.getValue().size());
        WordReviewHistory capturedHistory = historyCaptor.getValue().get(0);

        assertTrue(capturedHistory.currentTestDelay().toMillis() > (REVIEW_WORD_LAST_DELAY_SEC * 1000 * 1.25));
        assertTrue(capturedHistory.currentTestDelay().toMillis() < (REVIEW_WORD_LAST_DELAY_SEC * 1000 * 1.26));
        assertEquals(0, capturedHistory.currentBoost());
        assertEquals(Duration.ofSeconds(0), capturedHistory.currentBoostExpirationDelay());
    }

    @Test
    public void testProcess_MultipleBatches() {
        Instant reviewInstant = Instant.now();

        List<DBReviewEvent> eventsBatch1 = new ArrayList<>();
        for(int i = 0; i < QUERY_BATCH_SIZE; i++) {
            eventsBatch1.add(buildLearningEvent(reviewInstant.minusSeconds(2 + i)));
        }
        List<DBReviewEvent> eventsBatch2 = List.of(
                buildLearningEvent(reviewInstant.minusSeconds(1)),
                buildLearningEvent(reviewInstant));

        when(reviewSessionDao.loadUnprocessedReviewEventsForUserBatch(TEST_USERNAME, LEXICON_ID, Integer.MIN_VALUE, QUERY_BATCH_SIZE)).thenReturn(eventsBatch1);
        when(reviewSessionDao.loadUnprocessedReviewEventsForUserBatch(TEST_USERNAME, LEXICON_ID, 999, QUERY_BATCH_SIZE)).thenReturn(eventsBatch2);

        reviewEventProcessor.processEvents(TEST_USERNAME, LEXICON_ID);

        verify(wordReviewHistoryService).updateWordReviewHistoryBatch(TEST_USERNAME, List.of(new WordReviewHistory(LEXICON_ID, TEST_USERNAME, LEARNING_WORD_ID, true, reviewInstant,
                "", Duration.ofSeconds(INITIAL_LEARNING_DELAY_SEC), 0, Duration.ofSeconds(0), Map.of())));
        verify(wordReviewHistoryService, times(1)).updateWordReviewHistoryBatch(eq(TEST_USERNAME), anyList());

        verify(reviewSessionDao, times(1)).createScheduledReviewsBatch(anyList(), eq(TEST_USERNAME));

        List<DBReviewEvent> allEvents = new ArrayList<>(eventsBatch1);
        allEvents.addAll(eventsBatch2);
        verify(reviewSessionDao).markEventsAsProcessed(allEvents);
    }

    @Test
    public void tesGetFutureReviewEvents() {
        String wordScheduledInPastId = UUID.randomUUID().toString();
        String wordScheduledNowWithBoostId = UUID.randomUUID().toString();
        String wordScheduledBeforeCutoffId = UUID.randomUUID().toString();

        Instant now = Instant.now();
        Instant cutoff = now.plus(Duration.ofDays(1));
        Duration testDelay = Duration.ofHours(4);
        int totalWords = 101;
        int learnedWords = 65;

        when(wordService.getTotalLexiconWordCount(LEXICON_ID)).thenReturn(totalWords);
        when(wordReviewHistoryService.getTotalLearnedWordCount(LEXICON_ID, TEST_USERNAME)).thenReturn(learnedWords);

        when(reviewSessionDao.loadScheduledReviews(TEST_USERNAME, LEXICON_ID, "", Optional.of(cutoff))).thenReturn(List.of(
                buildDBScheduledReview(wordScheduledInPastId, now.minus(Duration.ofDays(1)), testDelay),
                buildDBScheduledReview(wordScheduledNowWithBoostId, now, testDelay),
                buildDBScheduledReview(wordScheduledBeforeCutoffId, now.plus(Duration.ofHours(12)), testDelay)));

        when(wordReviewHistoryService.getWordReviewHistory(LEXICON_ID, TEST_USERNAME, List.of(wordScheduledInPastId, wordScheduledNowWithBoostId,
                wordScheduledBeforeCutoffId))).thenReturn(List.of(
                    buildDBLexiconReviewHistory(wordScheduledInPastId, now.minus(Duration.ofDays(1)).minus(testDelay), LAST_TEST_RELATIONSHIP_ID, testDelay, 0, Duration.ofMillis(0)),
                    buildDBLexiconReviewHistory(wordScheduledNowWithBoostId, now.minus(testDelay), LAST_TEST_RELATIONSHIP_ID, testDelay, 3, Duration.ofDays(3)),
                    buildDBLexiconReviewHistory(wordScheduledBeforeCutoffId, now.plus(Duration.ofHours(12)).minus(testDelay), LAST_TEST_RELATIONSHIP_ID, testDelay, 0, Duration.ofMillis(0))));

        LexiconReviewSummary lexiconReviewSummary = reviewEventProcessor.getLexiconReviewSummary(LEXICON_ID, TEST_USERNAME, cutoff);

        assertEquals(totalWords, lexiconReviewSummary.totalWords());
        assertEquals(learnedWords, lexiconReviewSummary.learnedWords());

        List<FutureReviewEvent> futureReviewEvents = lexiconReviewSummary.futureReviewEvents();
        Map<String, List<FutureReviewEvent>> futureReviewEventsByWordId = futureReviewEvents.stream().collect(Collectors.groupingBy(futureReviewEvent -> futureReviewEvent.wordId()));
        assertEquals(3, futureReviewEventsByWordId.keySet().size());

        List<FutureReviewEvent> wordScheduledInPastFutureEvents = futureReviewEventsByWordId.get(wordScheduledInPastId)
                .stream().sorted(Comparator.comparing(FutureReviewEvent::reviewInstant)).toList();
        assertEquals(4, wordScheduledInPastFutureEvents.size());
        assertFalse(wordScheduledInPastFutureEvents.get(0).reviewInstant().isBefore(now));
        assertFalse(wordScheduledInPastFutureEvents.get(0).inferred());
        for (int idx = 1; idx < wordScheduledInPastFutureEvents.size(); idx++) {
            assertTrue(wordScheduledInPastFutureEvents.get(idx).reviewInstant().isBefore(cutoff));
            assertTrue(wordScheduledInPastFutureEvents.get(idx).inferred());
        }

        List<FutureReviewEvent> wordScheduledNowWithBoostFutureEvents = futureReviewEventsByWordId.get(wordScheduledNowWithBoostId)
                .stream().sorted(Comparator.comparing(FutureReviewEvent::reviewInstant)).toList();
        assertEquals(2, wordScheduledNowWithBoostFutureEvents.size());
        assertFalse(wordScheduledNowWithBoostFutureEvents.get(0).reviewInstant().isBefore(now));
        assertFalse(wordScheduledNowWithBoostFutureEvents.get(0).inferred());
        for (int idx = 1; idx < wordScheduledNowWithBoostFutureEvents.size(); idx++) {
            assertTrue(wordScheduledNowWithBoostFutureEvents.get(idx).reviewInstant().isBefore(cutoff));
            assertTrue(wordScheduledNowWithBoostFutureEvents.get(idx).inferred());
        }

        List<FutureReviewEvent> wordScheduledBeforeCutoffFutureEvents = futureReviewEventsByWordId.get(wordScheduledBeforeCutoffId)
                .stream().sorted(Comparator.comparing(FutureReviewEvent::reviewInstant)).toList();
        assertEquals(3, wordScheduledBeforeCutoffFutureEvents.size());
        assertEquals(now.plus(Duration.ofHours(12)), wordScheduledBeforeCutoffFutureEvents.get(0).reviewInstant());
        assertFalse(wordScheduledBeforeCutoffFutureEvents.get(0).inferred());
        for (int idx = 1; idx < wordScheduledBeforeCutoffFutureEvents.size(); idx++) {
            assertTrue(wordScheduledBeforeCutoffFutureEvents.get(idx).reviewInstant().isBefore(cutoff));
            assertTrue(wordScheduledBeforeCutoffFutureEvents.get(idx).inferred());
        }
    }

    private DBReviewEvent buildLearningEvent(Instant reviewInstant) {
        return buildDBReviewEvent(LEARNING_WORD_ID, reviewInstant, ReviewType.Learn,
                TEST_LANGUAGE.getReviewTestRelationships().get(TEST_LANGUAGE.getReviewTestRelationships().size() - 1).getTestOn().getId(),
                TEST_LANGUAGE.getReviewTestRelationships().get(TEST_LANGUAGE.getReviewTestRelationships().size() - 1).getPromptWith().getId(),
                true, false, false);
    }

    private DBReviewEvent buildReviewEvent(Instant reviewInstant, int testRelationshipIndex,
                                                  boolean isCorrect, boolean isNearMiss, boolean override) {
        return buildDBReviewEvent(REVIEW_WORD_ID, reviewInstant, ReviewType.Review,
                TEST_LANGUAGE.getReviewTestRelationships().get(testRelationshipIndex).getTestOn().getId(),
                TEST_LANGUAGE.getReviewTestRelationships().get(testRelationshipIndex).getPromptWith().getId(),
                isCorrect, isNearMiss, override);
    }

    private DBReviewEvent buildDBReviewEvent(String wordId, Instant reviewInstant, ReviewType reviewType, String testOn,
                                             String promptWith, boolean isCorrect, boolean isNearMiss, boolean override) {
        return new DBReviewEvent(eventIdCounter++, LEXICON_ID, wordId, TEST_USERNAME, reviewInstant, reviewType,
                ReviewMode.TypingTest, testOn, promptWith, isCorrect, isNearMiss, TEST_ELAPSED_TIME, override);
    }

    private DBScheduledReview buildDBScheduledReview(String wordId, Instant scheduledInstant, Duration scheduledTestDelay) {
        return new DBScheduledReview(UUID.randomUUID().toString(), TEST_USERNAME, LEXICON_ID, wordId, ReviewType.Review, TEST_LANGUAGE.getReviewTestRelationships().get(0).getId(),
                scheduledInstant, scheduledTestDelay, false);
    }

    private WordReviewHistory buildDBLexiconReviewHistory(String wordId, Instant mostRecentTestTime, String mostRecentTestRelationshipId, Duration currentTestDelay, double boost, Duration boostExpiration) {
        return new WordReviewHistory(LEXICON_ID, TEST_USERNAME, wordId, true, mostRecentTestTime, mostRecentTestRelationshipId, currentTestDelay, boost, boostExpiration, Map.of());
    }

    private Word buildWord(String wordId, Map<String, String> elements) {
        return new Word(wordId, LEXICON_ID, TEST_USERNAME, elements, "n", List.of(), Instant.EPOCH, Instant.now());
    }
}
