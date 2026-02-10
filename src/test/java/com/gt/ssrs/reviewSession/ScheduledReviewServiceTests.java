package com.gt.ssrs.reviewSession;

import com.gt.ssrs.exception.UserAccessException;
import com.gt.ssrs.language.Language;
import com.gt.ssrs.language.TestRelationship;
import com.gt.ssrs.lexicon.LexiconService;
import com.gt.ssrs.model.*;
import com.gt.ssrs.reviewSession.model.DBScheduledReview;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Fail.fail;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
public class ScheduledReviewServiceTests {

    private static final Language TEST_LANGUAGE = Language.Japanese;
    private static final String TEST_USERNAME = "testUser";
    private static final String TEST_LEXICON_ID = UUID.randomUUID().toString();

    private static final LexiconMetadata TEST_LEXICON_METADATA = new LexiconMetadata(TEST_LEXICON_ID, TEST_USERNAME, "Test Lexicon title",
            "Test Lexicon description", TEST_LANGUAGE.getId(), "");
    private static final Word TEST_WORD_1 = new Word(
            UUID.randomUUID().toString(),
            TEST_LEXICON_ID,
            TEST_USERNAME,
            Map.of("kana", "かな", "meaning", "kana", "kanji", "仮名"),
            "n",
            List.of(),
            Instant.EPOCH,
            Instant.now());
    private static final Word TEST_WORD_2 = new Word(
            UUID.randomUUID().toString(),
            TEST_LEXICON_ID,
            TEST_USERNAME,
            Map.of("kana", "かな2", "meaning", "kana2", "kanji", "仮名2"),
            "n",
            List.of(),
            Instant.EPOCH,
            Instant.now());
    private static final Word TEST_WORD_3 = new Word(
            UUID.randomUUID().toString(),
            TEST_LEXICON_ID,
            TEST_USERNAME,
            Map.of("kana", "かな3", "meaning", "kana3", "kanji", "仮名3"),
            "n",
            List.of(),
            Instant.EPOCH,
            Instant.now());

    private static final double FUTURE_EVENT_ALLOWED_RATIO = .8;

    private ScheduledReviewService scheduledReviewService;

    @Mock private ReviewSessionDao reviewSessionDao;
    @Mock private LexiconService lexiconService;

    @BeforeEach
    public void setup() {
        scheduledReviewService = new ScheduledReviewService(lexiconService, reviewSessionDao, FUTURE_EVENT_ALLOWED_RATIO);

        when(lexiconService.getLexiconLanguageId(TEST_LEXICON_ID)).thenReturn(TEST_LANGUAGE.getId());
        when(lexiconService.getLexiconMetadata(TEST_LEXICON_ID)).thenReturn(TEST_LEXICON_METADATA);
    }

    @Test
    public void testScheduleReviewsForHistory() {
        Instant reviewInstant = Instant.now();
        String word3ScheduledReviewId = UUID.randomUUID().toString();

        List<WordReviewHistory> wordReviewHistories = List.of(
                new WordReviewHistory(TEST_LEXICON_ID, TEST_USERNAME, TEST_WORD_1.id(), false, null, "", null, 0, null, null),
                new WordReviewHistory(TEST_LEXICON_ID, TEST_USERNAME, TEST_WORD_2.id(), true, reviewInstant, "", Duration.ofHours(4), 0, null, null),
                new WordReviewHistory(TEST_LEXICON_ID, TEST_USERNAME, TEST_WORD_3.id(), true, reviewInstant, TestRelationship.MeaningToKanji.getId(), Duration.ofHours(24), 0, null,
                        Map.of(TestRelationship.MeaningToKana.getId(), new TestHistory(1, 1, 1),
                               TestRelationship.MeaningToKanji.getId(), new TestHistory(1, 1, 1))));

        when(reviewSessionDao.loadScheduledReviewsForWords(TEST_LEXICON_ID, TEST_USERNAME, List.of(TEST_WORD_1.id(), TEST_WORD_2.id(), TEST_WORD_3.id())))
                .thenReturn(List.of(new DBScheduledReview(word3ScheduledReviewId, TEST_USERNAME, TEST_LEXICON_ID, TEST_WORD_3.id(), ReviewType.Review, TestRelationship.MeaningToKanji.getId(), Instant.now(), Duration.ofHours(4), false)));

        int updateCnt = scheduledReviewService.scheduleReviewsForHistory(TEST_USERNAME, wordReviewHistories);

        assertEquals(2, updateCnt);

        ArgumentCaptor<List<DBScheduledReview>> dbScheduledReviewCaptor = ArgumentCaptor.forClass(List.class);
        verify(reviewSessionDao).createScheduledReviewsBatch(dbScheduledReviewCaptor.capture(), eq(TEST_USERNAME));
        List<DBScheduledReview> captureDbScheduledReview = dbScheduledReviewCaptor.getValue();

        assertEquals(2, captureDbScheduledReview.size());
        for(DBScheduledReview dbScheduledReview : captureDbScheduledReview) {

            assertEquals(TEST_USERNAME, dbScheduledReview.owner());
            assertEquals(TEST_LEXICON_ID, dbScheduledReview.lexiconId());
            assertEquals(ReviewType.Review, dbScheduledReview.reviewType());
            assertEquals(false, dbScheduledReview.completed());

            if (dbScheduledReview.wordId().equals(TEST_WORD_2.id())) {
                assertEquals(TestRelationship.MeaningToKana.getId(), dbScheduledReview.testRelationshipId());
                assertEquals(reviewInstant.plus(wordReviewHistories.get(1).currentTestDelay()), dbScheduledReview.scheduledTestTime());
                assertEquals(wordReviewHistories.get(1).currentTestDelay(), dbScheduledReview.testDelay());
            } else if (dbScheduledReview.wordId().equals(TEST_WORD_3.id())) {
                assertEquals(TestRelationship.KanjiToKana.getId(), dbScheduledReview.testRelationshipId());
                assertEquals(reviewInstant.plus(wordReviewHistories.get(2).currentTestDelay()), dbScheduledReview.scheduledTestTime());
                assertEquals(wordReviewHistories.get(2).currentTestDelay(), dbScheduledReview.testDelay());
            } else {
                fail("History should not be saved for word " + dbScheduledReview.wordId());
            }
        }
    }

    @Test
    public void testScheduleReviewsForHistory_NewScheduledReview() {

    }

    @Test
    public void testGetScheduledReviewCounts() {
        when(reviewSessionDao.loadScheduledReviews(TEST_USERNAME, TEST_LEXICON_ID, "", Optional.empty())).thenReturn(List.of(
                buildDBScheduledReview(TEST_WORD_1.id(), 2, Instant.now().minusSeconds(60), Duration.ofSeconds(60), false),
                buildDBScheduledReview(TEST_WORD_2.id(), 1, Instant.now().minusSeconds(60), Duration.ofSeconds(60), false),
                buildDBScheduledReview(TEST_WORD_3.id(), 2, Instant.now().minusSeconds(60), Duration.ofSeconds(60), false)));

        Map<String, Integer> scheduledReviewCount = scheduledReviewService.getScheduledReviewCounts(TEST_USERNAME, TEST_LEXICON_ID, Optional.empty());

        assertEquals(Map.of(TEST_LANGUAGE.getReviewTestRelationships().get(1).getId(),1,
                        TEST_LANGUAGE.getReviewTestRelationships().get(2).getId(),2),
                scheduledReviewCount);
    }

    @Test
    public void testLoadEarliestScheduledReview() {
        String earliestReviewId = UUID.randomUUID().toString();
        Instant earliestTestTime = Instant.now().plusSeconds(60);

        when(reviewSessionDao.loadScheduledReviewsForWords(TEST_USERNAME, TEST_LEXICON_ID, List.of(TEST_WORD_1.id()))).thenReturn(List.of(
                new DBScheduledReview(earliestReviewId, TEST_USERNAME, TEST_LEXICON_ID, TEST_WORD_1.id(), ReviewType.Review, TestRelationship.MeaningToKana.getId(), earliestTestTime, Duration.ofDays(1), false),
                new DBScheduledReview(UUID.randomUUID().toString(), TEST_USERNAME, TEST_LEXICON_ID, TEST_WORD_1.id(), ReviewType.Review, TestRelationship.MeaningToKanji.getId(), earliestTestTime.plusSeconds(60), Duration.ofDays(1), false),
                new DBScheduledReview(UUID.randomUUID().toString(), TEST_USERNAME, TEST_LEXICON_ID, TEST_WORD_1.id(), ReviewType.Learn, TestRelationship.KanjiToKana.getId(), earliestTestTime.minusSeconds(60), Duration.ofDays(1), false)));

        Optional<ScheduledWordReview> scheduledWordReviewOptional = scheduledReviewService.loadEarliestScheduledReview(TEST_LEXICON_ID, TEST_USERNAME, TEST_WORD_1.id());
        assertTrue(scheduledWordReviewOptional.isPresent());

        ScheduledWordReview scheduledWordReview = scheduledWordReviewOptional.get();
        assertEquals(earliestReviewId, scheduledWordReview.reviewId());
        assertEquals(TEST_WORD_1.id(), scheduledWordReview.wordId());
        assertEquals(ReviewType.Review, scheduledWordReview.reviewType());
        assertEquals(TestRelationship.MeaningToKana.getId(), scheduledWordReview.reviewRelationShip());
    }

    @Test
    public void testLoadEarliestScheduledReview_NoReviews() {
        when(reviewSessionDao.loadScheduledReviewsForWords(TEST_USERNAME, TEST_LEXICON_ID, List.of(TEST_WORD_1.id()))).thenReturn(List.of());

        assertTrue(scheduledReviewService.loadEarliestScheduledReview(TEST_LEXICON_ID, TEST_USERNAME, TEST_WORD_1.id()).isEmpty());
    }

    @Test
    public void testAdjustNextReviewTimes() {
        Duration duration = Duration.ofDays(1);

        scheduledReviewService.adjustNextReviewTimes(TEST_LEXICON_ID, duration, TEST_USERNAME);

        verify(reviewSessionDao).adjustNextReviewTimes(TEST_LEXICON_ID, duration);
    }

    @Test
    public void testAdjustNextReviewTimes_AccessNotAllowed() {
        try {
            scheduledReviewService.adjustNextReviewTimes(TEST_LEXICON_ID, Duration.ofDays(1), "NotTheOwningUser");
        } catch (UserAccessException ex) {
            return;
        }

        fail("Expected UserAccessException");
    }

    @Test
    public void testAdjustNextReviewTimes_InvalidLexicon() {
        try {
            scheduledReviewService.adjustNextReviewTimes("NotALexiconId", Duration.ofDays(1), TEST_USERNAME);
        } catch (IllegalArgumentException ex) {
            return;
        }

        fail("Expected IllegalArgumentException");
    }

    @Test
    public void testDeleteScheduledReviewsForWords() {
        List<String> wordIds = List.of(TEST_WORD_1.id(), TEST_WORD_2.id(), TEST_WORD_3.id());

        scheduledReviewService.deleteScheduledReviewsForWords(TEST_LEXICON_ID, wordIds, TEST_USERNAME);

        verify(reviewSessionDao).deleteWordReviewEvents(TEST_LEXICON_ID, wordIds);
    }

    @Test
    public void testDeleteAllLexiconReviewEvents() {
        scheduledReviewService.deleteAllLexiconReviewEvents(TEST_LEXICON_ID, TEST_USERNAME);

        verify(reviewSessionDao).deleteAllLexiconReviewEvents(TEST_LEXICON_ID);
    }

    @Test
    public void testGetCurrentScheduledReviewForLexicon() {
        List<DBScheduledReview> dbScheduledReviews = List.of(
                buildDBScheduledReview(TEST_WORD_1.id(), 0, Instant.now().minusSeconds(30)),
                buildDBScheduledReview(TEST_WORD_2.id(), 1, Instant.now().minusSeconds(60)),
                buildDBScheduledReview(TEST_WORD_3.id(), 2, Instant.now().minusSeconds(90)));

        when(reviewSessionDao.loadScheduledReviews(TEST_USERNAME, TEST_LEXICON_ID, "", Optional.empty())).thenReturn(dbScheduledReviews);

        List<ScheduledWordReview> scheduledWordReviews = scheduledReviewService.getCurrentScheduledReviewForLexicon(TEST_LEXICON_ID, TEST_USERNAME, Optional.empty(), Optional.empty());

        assertEquals(dbScheduledReviews.size(), scheduledWordReviews.size());
        for(int i = 0; i < dbScheduledReviews.size(); i++) {
            verifyScheduledWordReview(dbScheduledReviews.get(i), scheduledWordReviews.get(i));
        }
    }

    @Test
    public void testGetCurrentScheduledReviewForLexicon_WithRelationship() {
        String reviewRelationshipId = TEST_LANGUAGE.getReviewTestRelationships().get(0).getId();
        List<DBScheduledReview> dbScheduledReviews = List.of(
                buildDBScheduledReview(TEST_WORD_1.id(), 0, Instant.now().minusSeconds(30)),
                buildDBScheduledReview(TEST_WORD_2.id(), 0, Instant.now().minusSeconds(60)));

        when(reviewSessionDao.loadScheduledReviews(TEST_USERNAME, TEST_LEXICON_ID, reviewRelationshipId, Optional.empty())).thenReturn(dbScheduledReviews);

        List<ScheduledWordReview> scheduledWordReviews = scheduledReviewService.getCurrentScheduledReviewForLexicon(TEST_LEXICON_ID, TEST_USERNAME, Optional.of(reviewRelationshipId), Optional.empty());

        assertEquals(dbScheduledReviews.size(), scheduledWordReviews.size());
        for(int i = 0; i < dbScheduledReviews.size(); i++) {
            verifyScheduledWordReview(dbScheduledReviews.get(i), scheduledWordReviews.get(i));
        }
    }

    @Test
    public void testGetCurrentScheduledReviewForLexicon_WithCutoff() {
        Instant cutoff = Instant.now().plus(1, ChronoUnit.HOURS);

        List<DBScheduledReview> dbScheduledReviews = List.of(
                buildDBScheduledReview(TEST_WORD_1.id(), 0, cutoff.minus(5, ChronoUnit.HOURS), Duration.ofHours(4), false),
                buildDBScheduledReview(TEST_WORD_2.id(), 1, cutoff.minus(1, ChronoUnit.MINUTES), Duration.ofHours(4), false),
                buildDBScheduledReview(TEST_WORD_2.id(), 2, cutoff.minus(59, ChronoUnit.MINUTES), Duration.ofHours(4), false));

        when(reviewSessionDao.loadScheduledReviews(TEST_USERNAME, TEST_LEXICON_ID, "", Optional.of(cutoff))).thenReturn(dbScheduledReviews);

        List<ScheduledWordReview> scheduledWordReviews = scheduledReviewService.getCurrentScheduledReviewForLexicon(TEST_LEXICON_ID, TEST_USERNAME, Optional.empty(), Optional.of(cutoff));

        // The third scheduled review is expected to not meet the elapsed time ratio and should be excluded, so only the first and third events should be returned
        assertEquals(2, scheduledWordReviews.size());
        verifyScheduledWordReview(dbScheduledReviews.get(0), scheduledWordReviews.get(0));
        verifyScheduledWordReview(dbScheduledReviews.get(2), scheduledWordReviews.get(1));
    }

    @Test
    public void testGetCurrentScheduledReviewForLexicon_WithRelationshipAndCutoff() {
        Instant cutoff = Instant.now().plus(1, ChronoUnit.HOURS);
        String reviewRelationshipId = TEST_LANGUAGE.getReviewTestRelationships().get(0).getId();

        List<DBScheduledReview> dbScheduledReviews = List.of(
                buildDBScheduledReview(TEST_WORD_1.id(), 0, cutoff.minus(5, ChronoUnit.HOURS), Duration.ofHours(4), false),
                buildDBScheduledReview(TEST_WORD_2.id(), 0, cutoff.minus(1, ChronoUnit.MINUTES), Duration.ofHours(4), false),
                buildDBScheduledReview(TEST_WORD_2.id(), 0, cutoff.minus(59, ChronoUnit.MINUTES), Duration.ofHours(4), false));

        when(reviewSessionDao.loadScheduledReviews(TEST_USERNAME, TEST_LEXICON_ID, reviewRelationshipId, Optional.of(cutoff))).thenReturn(dbScheduledReviews);

        List<ScheduledWordReview> scheduledWordReviews = scheduledReviewService.getCurrentScheduledReviewForLexicon(TEST_LEXICON_ID, TEST_USERNAME, Optional.of(reviewRelationshipId), Optional.of(cutoff));

        // The third scheduled review is expected to not meet the elapsed time ratio and should be excluded, so only the first and third events should be returned
        assertEquals(2, scheduledWordReviews.size());
        verifyScheduledWordReview(dbScheduledReviews.get(0), scheduledWordReviews.get(0));
        verifyScheduledWordReview(dbScheduledReviews.get(2), scheduledWordReviews.get(1));
    }

    private static DBScheduledReview buildDBScheduledReview(String wordId, int reviewRelationshipIndex, Instant scheduledTime) {
        return buildDBScheduledReview(wordId, reviewRelationshipIndex, scheduledTime, Duration.ofSeconds(60), false);
    }

    private static DBScheduledReview buildDBScheduledReview(String wordId, int reviewRelationshipIndex, Instant scheduledTime, Duration testDelay, boolean completed) {
        return new DBScheduledReview(UUID.randomUUID().toString(), TEST_USERNAME, TEST_LEXICON_ID, wordId, ReviewType.Review,
                TEST_LANGUAGE.getReviewTestRelationships().get(reviewRelationshipIndex).getId(), scheduledTime, testDelay, completed);
    }

    private void verifyScheduledWordReview(DBScheduledReview source, ScheduledWordReview scheduledWordReview) {
        assertEquals(source.id(), scheduledWordReview.reviewId());
        assertEquals(source.wordId(), scheduledWordReview.wordId());
        assertEquals(source.testRelationshipId(), scheduledWordReview.reviewRelationShip());
        assertEquals(ReviewType.Review, scheduledWordReview.reviewType());
    }
}
