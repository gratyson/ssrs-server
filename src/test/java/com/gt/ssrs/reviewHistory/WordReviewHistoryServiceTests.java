package com.gt.ssrs.reviewHistory;

import com.gt.ssrs.language.TestRelationship;
import com.gt.ssrs.model.TestHistory;
import com.gt.ssrs.model.Word;
import com.gt.ssrs.model.WordReviewHistory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
public class WordReviewHistoryServiceTests {

    private static final String TEST_USERNAME = "test_user";
    private static final String TEST_WORD_OWNER = "test_user";
    private static final String TEST_LEXICON_ID = UUID.randomUUID().toString();

    private static final Word WORD_1 = new Word(UUID.randomUUID().toString(), TEST_LEXICON_ID, TEST_WORD_OWNER, null, "", null, Instant.now(), Instant.now());
    private static final Word WORD_2 = new Word(UUID.randomUUID().toString(), TEST_LEXICON_ID, TEST_WORD_OWNER, null, "", null, Instant.now(), Instant.now());

    private static final WordReviewHistory WORD_REVIEW_HISTORY_1 =
            new WordReviewHistory(
                    WORD_1.id(),
                    WORD_1.lexiconId(),
                    TEST_USERNAME,
                    true,
                    Instant.now(),
                    TestRelationship.MeaningToKana.getId(),
                    Duration.ofDays(4),
                    2,
                    Duration.ofDays(8),
                    Map.of(TestRelationship.KanjiToMeaning.getId(), new TestHistory(2,2,2),
                           TestRelationship.MeaningToKanji.getId(), new TestHistory(2, 1, 1)));
    private static final WordReviewHistory WORD_REVIEW_HISTORY_2 =
            new WordReviewHistory(
                    WORD_2.id(),
                    WORD_2.lexiconId(),
                    TEST_USERNAME,
                    false,
                    null,
                    null,
                    null,
                    0,
                    null,
                    null);

    @Mock private WordReviewHistoryDao wordReviewHistoryDao;

    private WordReviewHistoryService wordReviewHistoryService;

    @BeforeEach
    public void before() {
        wordReviewHistoryService = new WordReviewHistoryService(wordReviewHistoryDao);
    }

    @Test
    public void testCreateEmptyWordReviewHistoryForWords() {
        List<Word> wordsToSave = List.of(WORD_1, WORD_2);

        wordReviewHistoryService.createEmptyWordReviewHistoryForWords(TEST_USERNAME, wordsToSave);

        ArgumentCaptor<List<WordReviewHistory>> wordReviewHistoryCaptor = ArgumentCaptor.forClass(List.class);
        verify(wordReviewHistoryDao, times(1)).createWordReviewHistory(eq(TEST_USERNAME), wordReviewHistoryCaptor.capture());
        List<WordReviewHistory> savedWordReviewHistories = wordReviewHistoryCaptor.getValue();

        assertEquals(2, savedWordReviewHistories.size());
        for (int i = 0; i < 2; i++) {
            Word savedWord = wordsToSave.get(i);
            WordReviewHistory savedWordReviewHistory = savedWordReviewHistories.get(i);

            assertEquals(savedWord.id(), savedWordReviewHistory.wordId());
            assertEquals(savedWord.lexiconId(), savedWordReviewHistory.lexiconId());
            assertEquals(TEST_USERNAME, savedWordReviewHistory.username());
            assertFalse(savedWordReviewHistory.learned());

            assertNull(savedWordReviewHistory.mostRecentTestTime());
            assertNull(savedWordReviewHistory.mostRecentTestRelationshipId());
            assertNull(savedWordReviewHistory.currentTestDelay());
            assertEquals(0, savedWordReviewHistory.currentBoost());
            assertNull(savedWordReviewHistory.currentBoostExpirationDelay());
            assertNull(savedWordReviewHistory.testHistory());
        }
    }

    @Test
    public void testGetWordReviewHistory() {
        when(wordReviewHistoryDao.getWordReviewHistory(TEST_LEXICON_ID, TEST_USERNAME, List.of(WORD_1.id(), WORD_2.id()))).thenReturn(List.of(WORD_REVIEW_HISTORY_1, WORD_REVIEW_HISTORY_2));

        List<WordReviewHistory> wordReviewHistories = wordReviewHistoryService.getWordReviewHistory(TEST_LEXICON_ID, TEST_USERNAME, List.of(WORD_1.id(), WORD_2.id()));

        assertEquals(List.of(WORD_REVIEW_HISTORY_1, WORD_REVIEW_HISTORY_2), wordReviewHistories);
    }

    @Test
    public void testUpdateWordReviewHistoryBatch() {
        when(wordReviewHistoryDao.updateWordReviewHistory(TEST_USERNAME, List.of(WORD_REVIEW_HISTORY_1, WORD_REVIEW_HISTORY_2))).thenReturn(List.of(WORD_REVIEW_HISTORY_2));

        assertEquals(List.of(WORD_REVIEW_HISTORY_2), wordReviewHistoryService.updateWordReviewHistoryBatch(TEST_USERNAME, List.of(WORD_REVIEW_HISTORY_1, WORD_REVIEW_HISTORY_2)));
    }

    @Test
    public void testGetIdsForWordsToLearn() {
        int requestedWordCnt = 8;
        when(wordReviewHistoryDao.getIdsForWordsToLearn(TEST_LEXICON_ID, TEST_USERNAME, requestedWordCnt)).thenReturn(List.of(WORD_1.id(), WORD_2.id()));

        assertEquals(List.of(WORD_1.id(), WORD_2.id()), wordReviewHistoryService.getIdsForWordsToLearn(TEST_LEXICON_ID, TEST_USERNAME, requestedWordCnt));
    }

    @Test
    public void testDeleteUserWordReviewHistories() {
        wordReviewHistoryService.deleteUserWordReviewHistories(TEST_LEXICON_ID, TEST_USERNAME, List.of(WORD_1.id(), WORD_2.id()));

        verify(wordReviewHistoryDao, times(1)).deleteUserWordReviewHistories(TEST_LEXICON_ID, TEST_USERNAME, List.of(WORD_1.id(), WORD_2.id()));
    }

    @Test
    public void testDeleteWordReviewHistory() {
        wordReviewHistoryService.deleteWordReviewHistories(TEST_LEXICON_ID, List.of(WORD_1.id(), WORD_2.id()));

        verify(wordReviewHistoryDao, times(1)).deleteWordReviewHistories(TEST_LEXICON_ID, List.of(WORD_1.id(), WORD_2.id()));
    }

    @Test
    public void testDeleteLexiconWordReviewHistoryForUser() {
        wordReviewHistoryService.deleteLexiconWordReviewHistoryForUser(TEST_LEXICON_ID, TEST_USERNAME);

        verify(wordReviewHistoryDao, times(1)).deleteLexiconWordReviewHistoryForUser(TEST_LEXICON_ID, TEST_USERNAME);
    }

    @Test
    public void testDeleteLexiconWordReviewHistory() {
        wordReviewHistoryService.deleteLexiconWordReviewHistory(TEST_LEXICON_ID);

        verify(wordReviewHistoryDao, times(1)).deleteLexiconWordReviewHistory(TEST_LEXICON_ID);
    }
}
