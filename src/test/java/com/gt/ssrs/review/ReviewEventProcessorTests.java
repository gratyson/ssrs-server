package com.gt.ssrs.review;


import com.gt.ssrs.language.LanguageService;
import com.gt.ssrs.lexicon.LexiconDao;
import com.gt.ssrs.review.model.DBLexiconReviewHistory;
import com.gt.ssrs.review.model.DBReviewEvent;
import com.gt.ssrs.review.model.DBScheduledReview;
import com.gt.ssrs.model.*;
import com.gt.ssrs.util.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static com.gt.ssrs.review.ReviewEventProcessor.QUERY_BATCH_SIZE;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@SpringBootTest
public class ReviewEventProcessorTests {

    private static final Language TEST_LANGUAGE = TestUtils.getTestLanguage();
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

    @Mock private ReviewSessionDao reviewSessionDao;
    @Mock private LexiconDao lexiconDao;
    @Mock private LanguageService languageService;

    private ReviewEventProcessor reviewEventProcessor;
    private int eventIdCounter;

    @BeforeEach
    public void initTests() {
        reviewEventProcessor = new ReviewEventProcessor(reviewSessionDao, lexiconDao, languageService,
                INITIAL_LEARNING_DELAY_SEC, CORRECT_NEAR_MISS_LEARNING_DELAY_SEC, STANDARD_INCORRECT_BOOST, NEAR_MISS_INCORRECT_BOOST);
        eventIdCounter = 0;

        when(lexiconDao.getLexiconMetadata(LEXICON_ID)).thenReturn(
                new Lexicon(LEXICON_ID, TEST_USERNAME, "Test Lexicon", "Test Lexicon", TEST_LANGUAGE.id(), "", List.of()));
        when(languageService.GetLanguageById(TEST_LANGUAGE.id())).thenReturn(TEST_LANGUAGE);

        when(lexiconDao.loadWords(Set.of(REVIEW_WORD_ID, LEARNING_WORD_ID))).thenReturn(List.of(
                buildWord(REVIEW_WORD_ID, Map.of("kana", "reviewKana", "meaning", "reviewMeaning", "kanji", "reviewKanji")),
                buildWord(LEARNING_WORD_ID, Map.of("kana", "learningKana", "meaning", "learningMeaning", "kanji", "learningKanji"))));
        when(lexiconDao.loadWords(Set.of(LEARNING_WORD_ID))).thenReturn(List.of(
                buildWord(LEARNING_WORD_ID, Map.of("kana", "learningKana", "meaning", "learningMeaning", "kanji", "learningKanji"))));
        when(lexiconDao.loadWords(Set.of(REVIEW_WORD_ID))).thenReturn(List.of(
                buildWord(REVIEW_WORD_ID, Map.of("kana", "reviewKana", "meaning", "reviewMeaning", "kanji", "reviewKanji"))));

        DBLexiconReviewHistory learningWordHistory = new DBLexiconReviewHistory(LEXICON_ID, LEARNING_WORD_ID, false, null, null, 0, null, Map.of());
        DBLexiconReviewHistory reviewWordHistory = new DBLexiconReviewHistory(LEXICON_ID, REVIEW_WORD_ID, true, Instant.now().minusSeconds(REVIEW_WORD_LAST_DELAY_SEC), Duration.ofSeconds(REVIEW_WORD_LAST_DELAY_SEC), 0, Duration.ofSeconds(0),
                Map.of(TEST_LANGUAGE.testRelationships().get(0).id(), new TestHistory(1, 1, 1)));
        when(reviewSessionDao.getLexiconReviewHistoryBatch(LEXICON_ID, TEST_USERNAME, Set.of(LEARNING_WORD_ID))).thenReturn(List.of(learningWordHistory));
        when(reviewSessionDao.getLexiconReviewHistoryBatch(LEXICON_ID, TEST_USERNAME, Set.of(REVIEW_WORD_ID))).thenReturn(List.of(reviewWordHistory));
        when(reviewSessionDao.getLexiconReviewHistoryBatch(LEXICON_ID, TEST_USERNAME, Set.of(LEARNING_WORD_ID, REVIEW_WORD_ID))).thenReturn(List.of(learningWordHistory, reviewWordHistory));

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
        verify(reviewSessionDao, times(1)).createScheduledReviewsBatch(scheduledReviewCaptor.capture());
        List<DBScheduledReview> capturedScheduledReviews = scheduledReviewCaptor.getValue();

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
                assertEquals(TEST_LANGUAGE.testRelationships().get(0).id(), capturedScheduledReview.testRelationshipId());
            } else {
                // delay should increase by ~25.9%. Check for 25% < value < %26 rather than trying to determine exact value
                assertTrue(capturedScheduledReview.testDelay().toMillis() > (REVIEW_WORD_LAST_DELAY_SEC * 1000 * 1.25));
                assertTrue(capturedScheduledReview.testDelay().toMillis() < (REVIEW_WORD_LAST_DELAY_SEC * 1000 * 1.26));
                assertEquals(reviewTestTime.plus(capturedScheduledReview.testDelay()), capturedScheduledReview.scheduledTestTime());
                assertEquals(TEST_LANGUAGE.testRelationships().get(2).id(), capturedScheduledReview.testRelationshipId());  // has history for relation 0 and just tested relation 1, so next relation should be 2
            }
        }

        ArgumentCaptor<List<DBLexiconReviewHistory>> historyCaptor = ArgumentCaptor.forClass(List.class);
        verify(reviewSessionDao, times(1)).updateLexiconReviewHistoryBatch(eq(TEST_USERNAME), historyCaptor.capture());
        List<DBLexiconReviewHistory> capturedHistories = historyCaptor.getValue();

        assertTrue(capturedHistories.get(0).wordId().equals(LEARNING_WORD_ID) || capturedHistories.get(0).wordId().equals(REVIEW_WORD_ID));
        assertTrue(capturedHistories.get(1).wordId().equals(LEARNING_WORD_ID) || capturedHistories.get(1).wordId().equals(REVIEW_WORD_ID));
        assertNotEquals(capturedHistories.get(0).wordId(), capturedHistories.get(1).wordId());
        for(DBLexiconReviewHistory wordHistory : capturedHistories) {
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
                assertEquals(Map.of(TEST_LANGUAGE.testRelationships().get(0).id(), new TestHistory(1, 1, 1),
                                    TEST_LANGUAGE.testRelationships().get(1).id(), new TestHistory(1, 1, 1)),
                        wordHistory.testHistory());
            }
        }

        verify(reviewSessionDao).markEventsAsProcessed(events);
        verify(reviewSessionDao, times(1)).markEventsAsProcessed(anyList());
    }

    @Test
    public void testProcessEvents_NotReviewOrLearn() {
        String wordId = UUID.randomUUID().toString();

        when(lexiconDao.loadWords(Set.of(wordId))).thenReturn(List.of(
                buildWord(wordId, Map.of("kana", "kanaVal", "meaning", "meaningVal"))));

        List<DBReviewEvent> events = List.of(buildDBReviewEvent(
                wordId, Instant.now(), ReviewType.None, "kana", "meaning", true, false, false));
        when(reviewSessionDao.loadUnprocessedReviewEventsForUserBatch(eq(TEST_USERNAME), eq(LEXICON_ID), anyInt(), anyInt())).thenReturn(events);

        when(reviewSessionDao.getLexiconReviewHistoryBatch(LEXICON_ID, TEST_USERNAME, Set.of(wordId))).thenReturn(
                List.of(new DBLexiconReviewHistory(LEXICON_ID, wordId, false, null, null, 0, null, Map.of())));

        reviewEventProcessor.processEvents(TEST_USERNAME, LEXICON_ID);

        verify(reviewSessionDao, times(0)).createScheduledReviewsBatch(anyList());
        verify(reviewSessionDao, times(0)).updateLexiconReviewHistoryBatch(eq(TEST_USERNAME), anyList());
        verify(reviewSessionDao).markEventsAsProcessed(events);
    }


    @Test
    public void testProcessEvents_AlreadyLearned() {
        when(reviewSessionDao.getLexiconReviewHistoryBatch(LEXICON_ID, TEST_USERNAME, Set.of(LEARNING_WORD_ID))).thenReturn(
                List.of(new DBLexiconReviewHistory(LEXICON_ID, LEARNING_WORD_ID, true, Instant.now().minusSeconds(REVIEW_WORD_LAST_DELAY_SEC),
                        Duration.ofSeconds(REVIEW_WORD_LAST_DELAY_SEC), 0, Duration.ofSeconds(0),
                        Map.of(TEST_LANGUAGE.testRelationships().get(0).id(), new TestHistory(1, 1, 1)))));

        List<DBReviewEvent> events = List.of(buildLearningEvent(Instant.now()));

        when(reviewSessionDao.loadUnprocessedReviewEventsForUserBatch(eq(TEST_USERNAME), eq(LEXICON_ID), anyInt(), anyInt())).thenReturn(events);

        reviewEventProcessor.processEvents(TEST_USERNAME, LEXICON_ID);

        verify(reviewSessionDao, times(0)).createScheduledReviewsBatch(anyList());
        verify(reviewSessionDao, times(0)).updateLexiconReviewHistoryBatch(eq(TEST_USERNAME), anyList());
        verify(reviewSessionDao).markEventsAsProcessed(events);
    }

    @Test
    public void testProcessEvents_CorrectNearMiss() {

        Instant reviewInstant = Instant.now();
        List<DBReviewEvent> events = List.of(buildReviewEvent(reviewInstant, 1, true, true, false));
        when(reviewSessionDao.loadUnprocessedReviewEventsForUserBatch(eq(TEST_USERNAME), eq(LEXICON_ID), anyInt(), anyInt())).thenReturn(events);

        reviewEventProcessor.processEvents(TEST_USERNAME, LEXICON_ID);

        ArgumentCaptor<List<DBScheduledReview>> scheduledReviewCaptor = ArgumentCaptor.forClass(List.class);
        verify(reviewSessionDao, times(1)).createScheduledReviewsBatch(scheduledReviewCaptor.capture());
        List<DBScheduledReview> capturedScheduledReviews = scheduledReviewCaptor.getValue();
        assertEquals(1, capturedScheduledReviews.size());
        DBScheduledReview capturedScheduledReview = capturedScheduledReviews.get(0);

        assertNotNull(capturedScheduledReview.id());
        assertEquals(LEXICON_ID, capturedScheduledReview.lexiconId());
        assertFalse(capturedScheduledReview.completed());
        assertEquals(Duration.ofSeconds(CORRECT_NEAR_MISS_LEARNING_DELAY_SEC), capturedScheduledReview.testDelay());
        assertEquals(reviewInstant.plusSeconds(CORRECT_NEAR_MISS_LEARNING_DELAY_SEC), capturedScheduledReview.scheduledTestTime());
        assertEquals(TEST_LANGUAGE.testRelationships().get(2).id(), capturedScheduledReview.testRelationshipId());

        ArgumentCaptor<List<DBLexiconReviewHistory>> historyCaptor = ArgumentCaptor.forClass(List.class);
        verify(reviewSessionDao, times(1)).updateLexiconReviewHistoryBatch(eq(TEST_USERNAME), historyCaptor.capture());
        List<DBLexiconReviewHistory> capturedHistories = historyCaptor.getValue();
        assertEquals(1, capturedHistories.size());
        DBLexiconReviewHistory wordHistory = capturedHistories.get(0);

        assertEquals(LEXICON_ID, wordHistory.lexiconId());
        assertTrue(wordHistory.learned());
        assertEquals(NEAR_MISS_INCORRECT_BOOST, wordHistory.currentBoost());
        assertEquals(Duration.ofSeconds(REVIEW_WORD_LAST_DELAY_SEC), wordHistory.currentBoostExpirationDelay());
        assertEquals(reviewInstant, wordHistory.mostRecentTestTime());
        assertEquals(Duration.ofSeconds(CORRECT_NEAR_MISS_LEARNING_DELAY_SEC), wordHistory.currentTestDelay());
        assertEquals(Map.of(TEST_LANGUAGE.testRelationships().get(0).id(), new TestHistory(1, 1, 1),
                            TEST_LANGUAGE.testRelationships().get(1).id(), new TestHistory(1, 1, 1)),
                wordHistory.testHistory());
    }

    @Test
    public void testProcessEvents_CorrectNearMissRecentlyLearned() {
        int lastReviewDelaySec = 14400;

        when(reviewSessionDao.getLexiconReviewHistoryBatch(LEXICON_ID, TEST_USERNAME, Set.of(REVIEW_WORD_ID))).thenReturn(
                List.of(new DBLexiconReviewHistory(LEXICON_ID, REVIEW_WORD_ID, true, Instant.now().minusSeconds(lastReviewDelaySec),
                        Duration.ofSeconds(lastReviewDelaySec), 0, Duration.ofSeconds(0),
                        Map.of(TEST_LANGUAGE.testRelationships().get(0).id(), new TestHistory(1, 1, 1)))));

        Instant reviewInstant = Instant.now();
        List<DBReviewEvent> events = List.of(buildReviewEvent(reviewInstant, 1, true, true, false));
        when(reviewSessionDao.loadUnprocessedReviewEventsForUserBatch(eq(TEST_USERNAME), eq(LEXICON_ID), anyInt(), anyInt())).thenReturn(events);

        reviewEventProcessor.processEvents(TEST_USERNAME, LEXICON_ID);

        ArgumentCaptor<List<DBScheduledReview>> scheduledReviewCaptor = ArgumentCaptor.forClass(List.class);
        verify(reviewSessionDao, times(1)).createScheduledReviewsBatch(scheduledReviewCaptor.capture());
        List<DBScheduledReview> capturedScheduledReviews = scheduledReviewCaptor.getValue();
        assertEquals(1, capturedScheduledReviews.size());
        DBScheduledReview capturedScheduledReview = capturedScheduledReviews.get(0);

        assertNotNull(capturedScheduledReview.id());
        assertEquals(LEXICON_ID, capturedScheduledReview.lexiconId());
        assertFalse(capturedScheduledReview.completed());
        System.out.println("Delay " + capturedScheduledReview.testDelay().toMillis());
        assertTrue(capturedScheduledReview.testDelay().toMillis() > (lastReviewDelaySec * 1000 * 1.25));
        assertTrue(capturedScheduledReview.testDelay().toMillis() < (lastReviewDelaySec * 1000 * 1.26));
        assertEquals(reviewInstant.plus(capturedScheduledReview.testDelay()), capturedScheduledReview.scheduledTestTime());
        assertEquals(TEST_LANGUAGE.testRelationships().get(2).id(), capturedScheduledReview.testRelationshipId());

        ArgumentCaptor<List<DBLexiconReviewHistory>> historyCaptor = ArgumentCaptor.forClass(List.class);
        verify(reviewSessionDao, times(1)).updateLexiconReviewHistoryBatch(eq(TEST_USERNAME), historyCaptor.capture());
        List<DBLexiconReviewHistory> capturedHistories = historyCaptor.getValue();
        assertEquals(1, capturedHistories.size());
        DBLexiconReviewHistory wordHistory = capturedHistories.get(0);

        assertEquals(LEXICON_ID, wordHistory.lexiconId());
        assertTrue(wordHistory.learned());
        assertEquals(0, wordHistory.currentBoost());
        assertEquals(Duration.ofSeconds(0), wordHistory.currentBoostExpirationDelay());
        assertEquals(reviewInstant, wordHistory.mostRecentTestTime());
        assertTrue(wordHistory.currentTestDelay().toMillis() > (lastReviewDelaySec * 1000 * 1.25));
        assertTrue(wordHistory.currentTestDelay().toMillis() < (lastReviewDelaySec * 1000 * 1.26));
        assertEquals(Map.of(TEST_LANGUAGE.testRelationships().get(0).id(), new TestHistory(1, 1, 1),
                            TEST_LANGUAGE.testRelationships().get(1).id(), new TestHistory(1, 1, 1)),
                wordHistory.testHistory());
    }

    @Test
    public void testProcessEvents_CorrectNearMissExistingBoost() {
        long currentBoostDurationSec = REVIEW_WORD_LAST_DELAY_SEC * 2;

        when(reviewSessionDao.getLexiconReviewHistoryBatch(LEXICON_ID, TEST_USERNAME, Set.of(REVIEW_WORD_ID))).thenReturn(
                List.of(new DBLexiconReviewHistory(LEXICON_ID, REVIEW_WORD_ID, true, Instant.now().minusSeconds(REVIEW_WORD_LAST_DELAY_SEC),
                        Duration.ofSeconds(REVIEW_WORD_LAST_DELAY_SEC), 3, Duration.ofSeconds(currentBoostDurationSec),
                        Map.of(TEST_LANGUAGE.testRelationships().get(0).id(), new TestHistory(1, 1, 1)))));

        Instant reviewInstant = Instant.now();
        List<DBReviewEvent> events = List.of(buildReviewEvent(reviewInstant, 1, true, true, false));
        when(reviewSessionDao.loadUnprocessedReviewEventsForUserBatch(eq(TEST_USERNAME), eq(LEXICON_ID), anyInt(), anyInt())).thenReturn(events);

        reviewEventProcessor.processEvents(TEST_USERNAME, LEXICON_ID);

        ArgumentCaptor<List<DBLexiconReviewHistory>> historyCaptor = ArgumentCaptor.forClass(List.class);
        verify(reviewSessionDao, times(1)).updateLexiconReviewHistoryBatch(eq(TEST_USERNAME), historyCaptor.capture());
        List<DBLexiconReviewHistory> capturedHistories = historyCaptor.getValue();
        assertEquals(1, capturedHistories.size());
        DBLexiconReviewHistory wordHistory = capturedHistories.get(0);

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
        verify(reviewSessionDao, times(1)).createScheduledReviewsBatch(scheduledReviewCaptor.capture());
        assertEquals(1, scheduledReviewCaptor.getValue().size());
        DBScheduledReview capturedScheduledReview = scheduledReviewCaptor.getValue().get(0);

        assertNotNull(capturedScheduledReview.id());
        assertEquals(LEXICON_ID, capturedScheduledReview.lexiconId());
        assertEquals(REVIEW_WORD_ID, capturedScheduledReview.wordId());
        assertEquals(TEST_LANGUAGE.testRelationships().get(1).id(), capturedScheduledReview.testRelationshipId());
        assertEquals(reviewInstant.plusSeconds(INITIAL_LEARNING_DELAY_SEC), capturedScheduledReview.scheduledTestTime());
        assertEquals(Duration.ofSeconds(INITIAL_LEARNING_DELAY_SEC), capturedScheduledReview.testDelay());

        ArgumentCaptor<List<DBLexiconReviewHistory>> historyCaptor = ArgumentCaptor.forClass(List.class);
        verify(reviewSessionDao, times(1)).updateLexiconReviewHistoryBatch(eq(TEST_USERNAME), historyCaptor.capture());
        assertEquals(1, historyCaptor.getValue().size());
        DBLexiconReviewHistory capturedHistory = historyCaptor.getValue().get(0);

        assertEquals(LEXICON_ID, capturedHistory.lexiconId());
        assertEquals(REVIEW_WORD_ID, capturedHistory.wordId());
        assertTrue(capturedHistory.learned());
        assertEquals(reviewInstant, capturedHistory.mostRecentTestTime());
        assertEquals(Duration.ofSeconds(INITIAL_LEARNING_DELAY_SEC), capturedHistory.currentTestDelay());
        assertEquals(STANDARD_INCORRECT_BOOST, capturedHistory.currentBoost());
        assertEquals(Duration.ofSeconds(REVIEW_WORD_LAST_DELAY_SEC), capturedHistory.currentBoostExpirationDelay());
        assertEquals(Map.of(TEST_LANGUAGE.testRelationships().get(0).id(), new TestHistory(2, 1, 0)), capturedHistory.testHistory());

        verify(reviewSessionDao).markEventsAsProcessed(events);
    }

    @Test
    public void testProcessEvent_IncorrectNearMiss() {
        Instant reviewInstant = Instant.now();
        List<DBReviewEvent> events = List.of(buildReviewEvent(reviewInstant,0, false, true, false));

        when(reviewSessionDao.loadUnprocessedReviewEventsForUserBatch(eq(TEST_USERNAME), eq(LEXICON_ID), anyInt(), anyInt())).thenReturn(events);

        reviewEventProcessor.processEvents(TEST_USERNAME, LEXICON_ID);

        ArgumentCaptor<List<DBScheduledReview>> scheduledReviewCaptor = ArgumentCaptor.forClass(List.class);
        verify(reviewSessionDao, times(1)).createScheduledReviewsBatch(scheduledReviewCaptor.capture());
        assertEquals(1, scheduledReviewCaptor.getValue().size());
        DBScheduledReview capturedScheduledReview = scheduledReviewCaptor.getValue().get(0);

        assertNotNull(capturedScheduledReview.id());
        assertEquals(LEXICON_ID, capturedScheduledReview.lexiconId());
        assertEquals(REVIEW_WORD_ID, capturedScheduledReview.wordId());
        assertEquals(TEST_LANGUAGE.testRelationships().get(1).id(), capturedScheduledReview.testRelationshipId());
        assertEquals(reviewInstant.plusSeconds(INITIAL_LEARNING_DELAY_SEC), capturedScheduledReview.scheduledTestTime());
        assertEquals(Duration.ofSeconds(INITIAL_LEARNING_DELAY_SEC), capturedScheduledReview.testDelay());

        ArgumentCaptor<List<DBLexiconReviewHistory>> historyCaptor = ArgumentCaptor.forClass(List.class);
        verify(reviewSessionDao, times(1)).updateLexiconReviewHistoryBatch(eq(TEST_USERNAME), historyCaptor.capture());
        assertEquals(1, historyCaptor.getValue().size());
        DBLexiconReviewHistory capturedHistory = historyCaptor.getValue().get(0);

        assertEquals(LEXICON_ID, capturedHistory.lexiconId());
        assertEquals(REVIEW_WORD_ID, capturedHistory.wordId());
        assertTrue(capturedHistory.learned());
        assertEquals(reviewInstant, capturedHistory.mostRecentTestTime());
        assertEquals(Duration.ofSeconds(INITIAL_LEARNING_DELAY_SEC), capturedHistory.currentTestDelay());
        assertEquals(NEAR_MISS_INCORRECT_BOOST, capturedHistory.currentBoost());
        assertEquals(Duration.ofSeconds(REVIEW_WORD_LAST_DELAY_SEC), capturedHistory.currentBoostExpirationDelay());
        assertEquals(Map.of(TEST_LANGUAGE.testRelationships().get(0).id(), new TestHistory(2, 1, 0)), capturedHistory.testHistory());

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
        verify(reviewSessionDao, times(1)).createScheduledReviewsBatch(scheduledReviewCaptor.capture());
        assertEquals(1, scheduledReviewCaptor.getValue().size());
        DBScheduledReview capturedScheduledReview = scheduledReviewCaptor.getValue().get(0);

        assertNotNull(capturedScheduledReview.id());
        assertEquals(LEXICON_ID, capturedScheduledReview.lexiconId());
        assertEquals(REVIEW_WORD_ID, capturedScheduledReview.wordId());
        assertEquals(TEST_LANGUAGE.testRelationships().get(1).id(), capturedScheduledReview.testRelationshipId());
        assertEquals(reviewInstantOverride.plusSeconds(INITIAL_LEARNING_DELAY_SEC), capturedScheduledReview.scheduledTestTime());
        assertEquals(Duration.ofSeconds(INITIAL_LEARNING_DELAY_SEC), capturedScheduledReview.testDelay());

        ArgumentCaptor<List<DBLexiconReviewHistory>> historyCaptor = ArgumentCaptor.forClass(List.class);
        verify(reviewSessionDao, times(1)).updateLexiconReviewHistoryBatch(eq(TEST_USERNAME), historyCaptor.capture());
        assertEquals(1, historyCaptor.getValue().size());
        DBLexiconReviewHistory capturedHistory = historyCaptor.getValue().get(0);

        assertEquals(LEXICON_ID, capturedHistory.lexiconId());
        assertEquals(REVIEW_WORD_ID, capturedHistory.wordId());
        assertTrue(capturedHistory.learned());
        assertEquals(reviewInstantOverride, capturedHistory.mostRecentTestTime());
        assertEquals(Duration.ofSeconds(INITIAL_LEARNING_DELAY_SEC), capturedHistory.currentTestDelay());
        assertEquals(STANDARD_INCORRECT_BOOST, capturedHistory.currentBoost());
        assertEquals(Duration.ofSeconds(REVIEW_WORD_LAST_DELAY_SEC), capturedHistory.currentBoostExpirationDelay());
        assertEquals(Map.of(TEST_LANGUAGE.testRelationships().get(0).id(), new TestHistory(2, 1, 0)), capturedHistory.testHistory());

        verify(reviewSessionDao).markEventsAsProcessed(events);
    }

    @Test
    public void testProcess_NoConsecutiveRelationship() {
        when(reviewSessionDao.getLexiconReviewHistoryBatch(LEXICON_ID, TEST_USERNAME, Set.of(REVIEW_WORD_ID))).thenReturn(
                List.of(new DBLexiconReviewHistory(LEXICON_ID, REVIEW_WORD_ID, true, Instant.now().minusSeconds(REVIEW_WORD_LAST_DELAY_SEC),
                        Duration.ofSeconds(REVIEW_WORD_LAST_DELAY_SEC), 0, Duration.ofSeconds(0),
                        Map.of(TEST_LANGUAGE.testRelationships().get(0).id(), new TestHistory(1, 1, 1),
                               TEST_LANGUAGE.testRelationships().get(1).id(), new TestHistory(5, 5, 5),
                               TEST_LANGUAGE.testRelationships().get(2).id(), new TestHistory(3, 3, 3)))));

        Instant reviewInstant = Instant.now();
        List<DBReviewEvent> events = List.of(
                buildReviewEvent(reviewInstant,0, true, false, false));

        when(reviewSessionDao.loadUnprocessedReviewEventsForUserBatch(eq(TEST_USERNAME), eq(LEXICON_ID), anyInt(), anyInt())).thenReturn(events);

        reviewEventProcessor.processEvents(TEST_USERNAME, LEXICON_ID);

        ArgumentCaptor<List<DBScheduledReview>> scheduledReviewCaptor = ArgumentCaptor.forClass(List.class);
        verify(reviewSessionDao, times(1)).createScheduledReviewsBatch(scheduledReviewCaptor.capture());
        assertEquals(1, scheduledReviewCaptor.getValue().size());
        DBScheduledReview capturedScheduledReview = scheduledReviewCaptor.getValue().get(0);

        assertEquals(TEST_LANGUAGE.testRelationships().get(2).id(), capturedScheduledReview.testRelationshipId());

        ArgumentCaptor<List<DBLexiconReviewHistory>> historyCaptor = ArgumentCaptor.forClass(List.class);
        verify(reviewSessionDao, times(1)).updateLexiconReviewHistoryBatch(eq(TEST_USERNAME), historyCaptor.capture());
        assertEquals(1, historyCaptor.getValue().size());
        DBLexiconReviewHistory capturedHistory = historyCaptor.getValue().get(0);

        assertEquals(Map.of(TEST_LANGUAGE.testRelationships().get(0).id(), new TestHistory(2, 2, 2),
                            TEST_LANGUAGE.testRelationships().get(1).id(), new TestHistory(5, 5, 5),
                            TEST_LANGUAGE.testRelationships().get(2).id(), new TestHistory(3, 3, 3)),
                capturedHistory.testHistory());
    }

    @Test
    public void testProcess_OnlyOneValidRelationship() {
        when(lexiconDao.loadWords(Set.of(REVIEW_WORD_ID))).thenReturn(List.of(
                buildWord(REVIEW_WORD_ID, Map.of("kana", "kanaVal", "meaning", "meaningVal"))));

        Instant reviewInstant = Instant.now();
        List<DBReviewEvent> events = List.of(
                buildReviewEvent(reviewInstant,0, true, false, false));

        when(reviewSessionDao.loadUnprocessedReviewEventsForUserBatch(eq(TEST_USERNAME), eq(LEXICON_ID), anyInt(), anyInt())).thenReturn(events);

        reviewEventProcessor.processEvents(TEST_USERNAME, LEXICON_ID);

        ArgumentCaptor<List<DBScheduledReview>> scheduledReviewCaptor = ArgumentCaptor.forClass(List.class);
        verify(reviewSessionDao, times(1)).createScheduledReviewsBatch(scheduledReviewCaptor.capture());
        assertEquals(1, scheduledReviewCaptor.getValue().size());
        DBScheduledReview capturedScheduledReview = scheduledReviewCaptor.getValue().get(0);

        assertEquals(TEST_LANGUAGE.testRelationships().get(0).id(), capturedScheduledReview.testRelationshipId());

        ArgumentCaptor<List<DBLexiconReviewHistory>> historyCaptor = ArgumentCaptor.forClass(List.class);
        verify(reviewSessionDao, times(1)).updateLexiconReviewHistoryBatch(eq(TEST_USERNAME), historyCaptor.capture());
        assertEquals(1, historyCaptor.getValue().size());
        DBLexiconReviewHistory capturedHistory = historyCaptor.getValue().get(0);

        assertEquals(Map.of(TEST_LANGUAGE.testRelationships().get(0).id(), new TestHistory(2, 2, 2)), capturedHistory.testHistory());
    }

    @Test
    public void testProcess_NormalBoosted() {
        when(reviewSessionDao.getLexiconReviewHistoryBatch(LEXICON_ID, TEST_USERNAME, Set.of(REVIEW_WORD_ID))).thenReturn(
                List.of(new DBLexiconReviewHistory(LEXICON_ID, REVIEW_WORD_ID, true, Instant.now().minusSeconds(REVIEW_WORD_LAST_DELAY_SEC),
                        Duration.ofSeconds(REVIEW_WORD_LAST_DELAY_SEC), STANDARD_INCORRECT_BOOST, Duration.ofSeconds(REVIEW_WORD_LAST_DELAY_SEC * 2),
                        Map.of(TEST_LANGUAGE.testRelationships().get(0).id(), new TestHistory(1, 1, 1)))));

        Instant reviewInstant = Instant.now();
        List<DBReviewEvent> events = List.of(
                buildReviewEvent(reviewInstant,0, true, false, false));

        when(reviewSessionDao.loadUnprocessedReviewEventsForUserBatch(eq(TEST_USERNAME), eq(LEXICON_ID), anyInt(), anyInt())).thenReturn(events);

        reviewEventProcessor.processEvents(TEST_USERNAME, LEXICON_ID);

        ArgumentCaptor<List<DBScheduledReview>> scheduledReviewCaptor = ArgumentCaptor.forClass(List.class);
        verify(reviewSessionDao, times(1)).createScheduledReviewsBatch(scheduledReviewCaptor.capture());
        assertEquals(1, scheduledReviewCaptor.getValue().size());
        DBScheduledReview capturedScheduledReview = scheduledReviewCaptor.getValue().get(0);

        // Double boost should increase delay by ~58.5%
        assertTrue(capturedScheduledReview.testDelay().toMillis() > (REVIEW_WORD_LAST_DELAY_SEC * 1000 * 1.58));
        assertTrue(capturedScheduledReview.testDelay().toMillis() < (REVIEW_WORD_LAST_DELAY_SEC * 1000 * 1.59));

        ArgumentCaptor<List<DBLexiconReviewHistory>> historyCaptor = ArgumentCaptor.forClass(List.class);
        verify(reviewSessionDao, times(1)).updateLexiconReviewHistoryBatch(eq(TEST_USERNAME), historyCaptor.capture());
        assertEquals(1, historyCaptor.getValue().size());
        DBLexiconReviewHistory capturedHistory = historyCaptor.getValue().get(0);

        assertTrue(capturedHistory.currentTestDelay().toMillis() > (REVIEW_WORD_LAST_DELAY_SEC * 1000 * 1.58));
        assertTrue(capturedHistory.currentTestDelay().toMillis() < (REVIEW_WORD_LAST_DELAY_SEC * 1000 * 1.59));
        assertEquals(STANDARD_INCORRECT_BOOST, capturedHistory.currentBoost());
        assertEquals(Duration.ofSeconds(REVIEW_WORD_LAST_DELAY_SEC * 2), capturedHistory.currentBoostExpirationDelay());
    }

    @Test
    public void testProcess_ExtraBoosted() {
        when(reviewSessionDao.getLexiconReviewHistoryBatch(LEXICON_ID, TEST_USERNAME, Set.of(REVIEW_WORD_ID))).thenReturn(
                List.of(new DBLexiconReviewHistory(LEXICON_ID, REVIEW_WORD_ID, true, Instant.now().minusSeconds(REVIEW_WORD_LAST_DELAY_SEC),
                        Duration.ofSeconds(REVIEW_WORD_LAST_DELAY_SEC), NEAR_MISS_INCORRECT_BOOST, Duration.ofSeconds(REVIEW_WORD_LAST_DELAY_SEC * 2),
                        Map.of(TEST_LANGUAGE.testRelationships().get(0).id(), new TestHistory(1, 1, 1)))));

        Instant reviewInstant = Instant.now();
        List<DBReviewEvent> events = List.of(
                buildReviewEvent(reviewInstant,0, true, false, false));

        when(reviewSessionDao.loadUnprocessedReviewEventsForUserBatch(eq(TEST_USERNAME), eq(LEXICON_ID), anyInt(), anyInt())).thenReturn(events);

        reviewEventProcessor.processEvents(TEST_USERNAME, LEXICON_ID);

        ArgumentCaptor<List<DBScheduledReview>> scheduledReviewCaptor = ArgumentCaptor.forClass(List.class);
        verify(reviewSessionDao, times(1)).createScheduledReviewsBatch(scheduledReviewCaptor.capture());
        assertEquals(1, scheduledReviewCaptor.getValue().size());
        DBScheduledReview capturedScheduledReview = scheduledReviewCaptor.getValue().get(0);

        // Triple boost should exactly double the delay
        assertEquals(Duration.ofSeconds(REVIEW_WORD_LAST_DELAY_SEC * 2), capturedScheduledReview.testDelay());

        ArgumentCaptor<List<DBLexiconReviewHistory>> historyCaptor = ArgumentCaptor.forClass(List.class);
        verify(reviewSessionDao, times(1)).updateLexiconReviewHistoryBatch(eq(TEST_USERNAME), historyCaptor.capture());
        assertEquals(1, historyCaptor.getValue().size());
        DBLexiconReviewHistory capturedHistory = historyCaptor.getValue().get(0);

        assertEquals(Duration.ofSeconds(REVIEW_WORD_LAST_DELAY_SEC * 2), capturedHistory.currentTestDelay());
        // Boost expired but exactly matches the expiration delay
        assertEquals(0, capturedHistory.currentBoost());
        assertEquals(Duration.ofSeconds(0), capturedHistory.currentBoostExpirationDelay());
    }

    @Test
    public void testProcess_BoostExpiredStandardDelayLessThanExpiration() {
        Duration boostExpirationDuration = Duration.ofSeconds((long)(REVIEW_WORD_LAST_DELAY_SEC * 1.5));

        when(reviewSessionDao.getLexiconReviewHistoryBatch(LEXICON_ID, TEST_USERNAME, Set.of(REVIEW_WORD_ID))).thenReturn(
                List.of(new DBLexiconReviewHistory(LEXICON_ID, REVIEW_WORD_ID, true, Instant.now().minusSeconds(REVIEW_WORD_LAST_DELAY_SEC),
                        Duration.ofSeconds(REVIEW_WORD_LAST_DELAY_SEC), STANDARD_INCORRECT_BOOST, boostExpirationDuration,
                        Map.of(TEST_LANGUAGE.testRelationships().get(0).id(), new TestHistory(1, 1, 1)))));

        Instant reviewInstant = Instant.now();
        List<DBReviewEvent> events = List.of(
                buildReviewEvent(reviewInstant,0, true, false, false));

        when(reviewSessionDao.loadUnprocessedReviewEventsForUserBatch(eq(TEST_USERNAME), eq(LEXICON_ID), anyInt(), anyInt())).thenReturn(events);

        reviewEventProcessor.processEvents(TEST_USERNAME, LEXICON_ID);

        ArgumentCaptor<List<DBScheduledReview>> scheduledReviewCaptor = ArgumentCaptor.forClass(List.class);
        verify(reviewSessionDao, times(1)).createScheduledReviewsBatch(scheduledReviewCaptor.capture());
        assertEquals(1, scheduledReviewCaptor.getValue().size());
        DBScheduledReview capturedScheduledReview = scheduledReviewCaptor.getValue().get(0);

        // Standard delay is ~25.9%, double boost is ~58.5%, boost expires at 50%. Expected to use the boost expiration as the new delay
        assertEquals(boostExpirationDuration, capturedScheduledReview.testDelay());

        ArgumentCaptor<List<DBLexiconReviewHistory>> historyCaptor = ArgumentCaptor.forClass(List.class);
        verify(reviewSessionDao, times(1)).updateLexiconReviewHistoryBatch(eq(TEST_USERNAME), historyCaptor.capture());
        assertEquals(1, historyCaptor.getValue().size());
        DBLexiconReviewHistory capturedHistory = historyCaptor.getValue().get(0);

        assertEquals(boostExpirationDuration, capturedHistory.currentTestDelay());
        assertEquals(0, capturedHistory.currentBoost());
        assertEquals(Duration.ofSeconds(0), capturedHistory.currentBoostExpirationDelay());
    }

    @Test
    public void testProcess_BoostExpiredStandardDelayGreaterThanExpiration() {
        when(reviewSessionDao.getLexiconReviewHistoryBatch(LEXICON_ID, TEST_USERNAME, Set.of(REVIEW_WORD_ID))).thenReturn(
                List.of(new DBLexiconReviewHistory(LEXICON_ID, REVIEW_WORD_ID, true, Instant.now().minusSeconds(REVIEW_WORD_LAST_DELAY_SEC),
                        Duration.ofSeconds(REVIEW_WORD_LAST_DELAY_SEC), STANDARD_INCORRECT_BOOST, Duration.ofSeconds((long)(REVIEW_WORD_LAST_DELAY_SEC * 1.1)),
                        Map.of(TEST_LANGUAGE.testRelationships().get(0).id(), new TestHistory(1, 1, 1)))));

        when(lexiconDao.loadWords(Set.of(REVIEW_WORD_ID))).thenReturn(List.of(
                buildWord(REVIEW_WORD_ID, Map.of("kana", "kanaVal", "meaning", "meaningVal", "kanji", "kanjiVal"))));

        Instant reviewInstant = Instant.now();
        List<DBReviewEvent> events = List.of(
                buildReviewEvent(reviewInstant,0, true, false, false));

        when(reviewSessionDao.loadUnprocessedReviewEventsForUserBatch(eq(TEST_USERNAME), eq(LEXICON_ID), anyInt(), anyInt())).thenReturn(events);

        reviewEventProcessor.processEvents(TEST_USERNAME, LEXICON_ID);

        ArgumentCaptor<List<DBScheduledReview>> scheduledReviewCaptor = ArgumentCaptor.forClass(List.class);
        verify(reviewSessionDao, times(1)).createScheduledReviewsBatch(scheduledReviewCaptor.capture());
        assertEquals(1, scheduledReviewCaptor.getValue().size());
        DBScheduledReview capturedScheduledReview = scheduledReviewCaptor.getValue().get(0);

        // Standard delay is ~25.9%, double boost is ~58.5%, boost expires at 10%. Expected to use the standard delay
        assertTrue(capturedScheduledReview.testDelay().toMillis() > (REVIEW_WORD_LAST_DELAY_SEC * 1000 * 1.25));
        assertTrue(capturedScheduledReview.testDelay().toMillis() < (REVIEW_WORD_LAST_DELAY_SEC * 1000 * 1.26));

        ArgumentCaptor<List<DBLexiconReviewHistory>> historyCaptor = ArgumentCaptor.forClass(List.class);
        verify(reviewSessionDao, times(1)).updateLexiconReviewHistoryBatch(eq(TEST_USERNAME), historyCaptor.capture());
        assertEquals(1, historyCaptor.getValue().size());
        DBLexiconReviewHistory capturedHistory = historyCaptor.getValue().get(0);

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

        verify(reviewSessionDao).updateLexiconReviewHistoryBatch(TEST_USERNAME, List.of(new DBLexiconReviewHistory(LEXICON_ID, LEARNING_WORD_ID, true, reviewInstant,
                Duration.ofSeconds(INITIAL_LEARNING_DELAY_SEC), 0, Duration.ofSeconds(0), Map.of())));
        verify(reviewSessionDao, times(1)).updateLexiconReviewHistoryBatch(eq(TEST_USERNAME), anyList());

        verify(reviewSessionDao, times(1)).createScheduledReviewsBatch(anyList());

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

        when(lexiconDao.getTotalLexiconWordCount(LEXICON_ID)).thenReturn(totalWords);
        when(reviewSessionDao.getTotalLearnedWordCount(LEXICON_ID, TEST_USERNAME)).thenReturn(learnedWords);

        when(reviewSessionDao.loadScheduledReviews(LEXICON_ID, "", Optional.of(cutoff))).thenReturn(List.of(
                buildDBScheduledReview(wordScheduledInPastId, now.minus(Duration.ofDays(1)), testDelay),
                buildDBScheduledReview(wordScheduledNowWithBoostId, now, testDelay),
                buildDBScheduledReview(wordScheduledBeforeCutoffId, now.plus(Duration.ofHours(12)), testDelay)));

        when(reviewSessionDao.getLexiconReviewHistoryBatch(LEXICON_ID, TEST_USERNAME, List.of(wordScheduledInPastId, wordScheduledNowWithBoostId,
                wordScheduledBeforeCutoffId))).thenReturn(List.of(
                    buildDBLexiconReviewHistory(wordScheduledInPastId, now.minus(Duration.ofDays(1)).minus(testDelay), testDelay, 0, Duration.ofMillis(0)),
                    buildDBLexiconReviewHistory(wordScheduledNowWithBoostId, now.minus(testDelay), testDelay, 3, Duration.ofDays(3)),
                    buildDBLexiconReviewHistory(wordScheduledBeforeCutoffId, now.plus(Duration.ofHours(12)).minus(testDelay), testDelay, 0, Duration.ofMillis(0))));

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
                TEST_LANGUAGE.testRelationships().get(TEST_LANGUAGE.testRelationships().size() - 1).testOn(),
                TEST_LANGUAGE.testRelationships().get(TEST_LANGUAGE.testRelationships().size() - 1).promptWith(),
                true, false, false);
    }

    private DBReviewEvent buildReviewEvent(Instant reviewInstant, int testRelationshipIndex,
                                                  boolean isCorrect, boolean isNearMiss, boolean override) {
        return buildDBReviewEvent(REVIEW_WORD_ID, reviewInstant, ReviewType.Review,
                TEST_LANGUAGE.testRelationships().get(testRelationshipIndex).testOn(),
                TEST_LANGUAGE.testRelationships().get(testRelationshipIndex).promptWith(),
                isCorrect, isNearMiss, override);
    }

    private DBReviewEvent buildDBReviewEvent(String wordId, Instant reviewInstant, ReviewType reviewType, String testOn,
                                             String promptWith, boolean isCorrect, boolean isNearMiss, boolean override) {
        return new DBReviewEvent(eventIdCounter++, LEXICON_ID, wordId, TEST_USERNAME, reviewInstant, reviewType,
                ReviewMode.TypingTest, testOn, promptWith, isCorrect, isNearMiss, TEST_ELAPSED_TIME, override);
    }

    private DBScheduledReview buildDBScheduledReview(String wordId, Instant scheduledInstant, Duration scheduledTestDelay) {
        return new DBScheduledReview(UUID.randomUUID().toString(), LEXICON_ID, wordId, ReviewType.Review, TEST_LANGUAGE.testRelationships().get(0).id(),
                scheduledInstant, scheduledTestDelay, false);
    }

    private DBLexiconReviewHistory buildDBLexiconReviewHistory(String wordId, Instant mostRecentTestTime, Duration currentTestDelay, double boost, Duration boostExpiration) {
        return new DBLexiconReviewHistory(LEXICON_ID, wordId, true, mostRecentTestTime, currentTestDelay, boost, boostExpiration, Map.of());
    }

    private Word buildWord(String wordId, Map<String, String> elements) {
        return new Word(wordId, TEST_USERNAME, elements, "n", List.of());
    }
}
