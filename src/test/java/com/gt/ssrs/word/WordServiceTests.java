package com.gt.ssrs.word;

import com.gt.ssrs.audio.AudioService;
import com.gt.ssrs.blob.BlobDao;
import com.gt.ssrs.exception.UserAccessException;
import com.gt.ssrs.language.Language;
import com.gt.ssrs.lexicon.LexiconService;
import com.gt.ssrs.model.LexiconMetadata;
import com.gt.ssrs.model.Word;
import com.gt.ssrs.model.WordFilterOptions;
import com.gt.ssrs.reviewHistory.WordReviewHistoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
public class WordServiceTests {

    private static final Language TEST_LANGUAGE = Language.Japanese;
    private static final String TEST_USERNAME = "testuser";
    private static final LexiconMetadata TEST_LEXICON_METADATA = new LexiconMetadata(UUID.randomUUID().toString(), TEST_USERNAME, "Test Lexicon", "Test Lexicon", TEST_LANGUAGE.getId(), "");

    private static final int INSTANT_NOW_ALLOWED_MARGIN_MILLIS = 50;

    private static final Word TEST_WORD_1 = new Word(
            UUID.randomUUID().toString(),
            TEST_LEXICON_METADATA.id(),
            TEST_USERNAME,
            Map.of("kana", "かな", "meaning", "kana", "kanji", "仮名"),
            "n",
            List.of(),
            Instant.EPOCH,
            Instant.now());
    private static final Word TEST_WORD_2 = new Word(
            UUID.randomUUID().toString(),
            TEST_LEXICON_METADATA.id(),
            TEST_USERNAME,
            Map.of("kana", "かな2", "meaning", "kana2", "kanji", "仮名2"),
            "n",
            List.of(),
            Instant.EPOCH,
            Instant.now());
    private static final Word TEST_WORD_3 = new Word(
            UUID.randomUUID().toString(),
            TEST_LEXICON_METADATA.id(),
            TEST_USERNAME,
            Map.of("kana", "かな3", "meaning", "kana3", "kanji", "仮名3"),
            "n",
            List.of(),
            Instant.EPOCH,
            Instant.now());

    @Mock private LexiconService lexiconService;
    @Mock private WordReviewHistoryService wordReviewHistoryService;
    @Mock private AudioService audioService;
    @Mock private WordDao wordDao;
    @Mock private BlobDao blobDao;

    private WordService wordService;

    @BeforeEach
    public void setup() {
        wordService = new WordService(lexiconService, wordReviewHistoryService, audioService, wordDao, blobDao);

        when(lexiconService.getLexiconMetadata(TEST_LEXICON_METADATA.id())).thenReturn(TEST_LEXICON_METADATA);

        when(wordDao.loadWord(TEST_WORD_1.id())).thenReturn(TEST_WORD_1);
        when(wordDao.loadWords(List.of(TEST_WORD_1.id()))).thenReturn(List.of(TEST_WORD_1));
    }

    @Test
    public void testLoadWord() {
        assertEquals(TEST_WORD_1, wordService.loadWord(TEST_WORD_1.id()));
    }

    @Test
    public void testLoadWords() {
        assertEquals(List.of(TEST_WORD_1), wordService.loadWords(List.of(TEST_WORD_1.id())));
    }

    @Test
    public void testUpdateWord() {
        Word updatedWordWithoutUsername = new Word(
                TEST_WORD_1.id(),
                TEST_LEXICON_METADATA.id(),
                "",
                Map.of("kana", "かたかな", "meaning", "katakana"),
                "n",
                List.of(),
                TEST_WORD_1.createInstant(),
                TEST_WORD_1.updateInstant());

        Word expectedWord = new Word(updatedWordWithoutUsername.id(), TEST_LEXICON_METADATA.id(), TEST_USERNAME,
                updatedWordWithoutUsername.elements(), updatedWordWithoutUsername.attributes(),
                updatedWordWithoutUsername.audioFiles(), TEST_WORD_1.createInstant(), TEST_WORD_1.updateInstant());

        when(wordDao.updateWord(expectedWord)).thenReturn(1);

        assertEquals(expectedWord, wordService.updateWord(updatedWordWithoutUsername, TEST_USERNAME));
    }

    @Test
    public void testUpdateWord_NotOwner() {
        Word updatedWordWithoutUsername = new Word(
                TEST_WORD_1.id(),
                TEST_LEXICON_METADATA.id(),
                "",
                Map.of("kana", "かたかな", "meaning", "katakana"),
                "n",
                List.of(),
                TEST_WORD_1.createInstant(),
                TEST_WORD_1.updateInstant());

        assertNull(wordService.updateWord(updatedWordWithoutUsername, "different_username"));
    }

    @Test
    public void testUpdateWord_FailedToSave() {
        Word updatedWordWithoutUsername = new Word(
                TEST_WORD_1.id(),
                TEST_LEXICON_METADATA.id(),
                "",
                Map.of("kana", "かたかな", "meaning", "katakana"),
                "n",
                List.of(),
                TEST_WORD_1.createInstant(),
                TEST_WORD_1.updateInstant());

        Word expectedWord = withOwner(updatedWordWithoutUsername);

        when(wordDao.updateWord(expectedWord)).thenReturn(0);

        assertNull(wordService.updateWord(updatedWordWithoutUsername, TEST_USERNAME));
    }

    @Test
    public void testSaveWords() {
        Instant testStartInstant = Instant.now();

        Word wordWithId = new Word(UUID.randomUUID().toString(), TEST_LEXICON_METADATA.id(), "", Map.of("kana", " a", "meaning", "a"),"a", List.of("a.mp3"), null, null);

        Word wordWithIdAlreadyExists = new Word(TEST_WORD_1.id(), TEST_LEXICON_METADATA.id(), "", Map.of("kana", "b", "meaning", "b "), "b", List.of(), null, null);
        Word existingWord = new Word(TEST_WORD_1.id(), TEST_LEXICON_METADATA.id(), TEST_USERNAME, Map.of("kana", "bb", "meaning", "bb"), "c", List.of("b.mp3"), Instant.EPOCH, Instant.now());
        when(wordDao.loadWord(wordWithIdAlreadyExists.id())).thenReturn(existingWord);

        Word wordWithIdDifferentOwner = new Word(UUID.randomUUID().toString(), TEST_LEXICON_METADATA.id(), "", Map.of("kana", "c", "meaning", "c"), "c", List.of("c.mp3"), Instant.EPOCH, Instant.now());
        when(wordDao.loadWord(wordWithIdDifferentOwner.id())).thenReturn(new Word(wordWithIdDifferentOwner.id(), TEST_LEXICON_METADATA.id(), "differentOwner", Map.of("kana", "c", "meaning", "c"), "c", List.of("c.mp3"), Instant.EPOCH, Instant.now()));

        Word wordNoIdNoDuplicate = new Word(null, TEST_LEXICON_METADATA.id(), "", Map.of("kana", "d", "meaning", "d"), "d", List.of("d.mp3"), Instant.EPOCH, Instant.now());
        Word wordNoIdWithDuplicate = new Word("", TEST_LEXICON_METADATA.id(), "", Map.of("kana", "e", "meaning", "e"), "e", List.of("e.mp3"), Instant.EPOCH, Instant.now());
        Word duplicateWord = new Word(UUID.randomUUID().toString(), TEST_LEXICON_METADATA.id(), TEST_USERNAME, Map.of("kana", "e", "meaning", "e"), "e", List.of("e.mp3"), Instant.EPOCH, Instant.now());
        when(wordDao.findDuplicateWordInOtherLexicons(TEST_LANGUAGE, TEST_LEXICON_METADATA.id(), TEST_USERNAME, wordNoIdWithDuplicate)).thenReturn(duplicateWord);

        Word wordNotSaved = new Word(UUID.randomUUID().toString(), TEST_LEXICON_METADATA.id(), "", Map.of("kana", "f", "meaning", "f"), "f", List.of("f.mp3"), null, null);
        Word wordFailingValidation = new Word(UUID.randomUUID().toString(), TEST_LEXICON_METADATA.id(), "", Map.of("kana", "h"), "h", List.of("h.mp3"), null, null);

        Word wordNotInLexiconWord = new Word(UUID.randomUUID().toString(), TEST_LEXICON_METADATA.id(), "", Map.of("kana", "i", "meaning", "i"), "i", null, null, null);
        when(wordDao.loadWord(wordNotInLexiconWord.id())).thenReturn(new Word(wordNotInLexiconWord.id(), UUID.randomUUID().toString(), "", Map.of("kana", "i", "meaning", "i"), "i", null, Instant.EPOCH, Instant.now()));

        when(wordDao.createWords(eq(TEST_LANGUAGE), eq(TEST_LEXICON_METADATA.id()), anyList())).then(args ->
                ((List<Word>)args.getArgument(2)).stream().filter(word -> !word.id().equals(wordNotSaved.id())).collect(Collectors.toList()));

        List<Word> savedWords = wordService.saveWords(
                List.of(wordWithId, wordWithIdAlreadyExists, wordWithIdDifferentOwner, wordNoIdNoDuplicate,
                        wordNoIdWithDuplicate, wordNotSaved, wordFailingValidation, wordNotInLexiconWord),
                TEST_LEXICON_METADATA.id(), TEST_USERNAME);

        assertEquals(3, savedWords.size());
        for(Word savedWord : savedWords) {
            assertTrue(savedWord.id() != null && !savedWord.id().isBlank());

            Word originalWord;
            if(savedWord.id().equals(wordWithId.id())) {
                originalWord = wordWithId;
            } else if (savedWord.id().equals(wordWithIdAlreadyExists.id())) {
                originalWord = wordWithIdAlreadyExists;
            } else {
                originalWord = wordNoIdNoDuplicate;
            }

            assertEquals(originalWord.lexiconId(), savedWord.lexiconId());
            assertEquals(TEST_USERNAME, savedWord.owner());
            assertEquals(trimElements(originalWord.elements()), savedWord.elements());
            assertEquals(originalWord.attributes().trim(), savedWord.attributes());
            assertInstantNow(testStartInstant, savedWord.updateInstant());
            if (originalWord == wordWithIdAlreadyExists) {
                assertEquals(existingWord.audioFiles(), savedWord.audioFiles());
                assertEquals(existingWord.createInstant(), savedWord.createInstant());
            } else {
                assertEquals(originalWord.audioFiles(), savedWord.audioFiles());
                assertInstantNow(testStartInstant, savedWord.createInstant());
            }
        }

        // Expecting wordWithId and wordNoIdNoDuplicate to require new word history. Attributes are set up to be unique in this test so use to filter
        List<Word> expectedNewHistoryWords = savedWords.stream()
                .filter(word -> word.attributes().equals(wordWithId.attributes()) || word.attributes().equals(wordNoIdNoDuplicate.attributes()))
                .collect(Collectors.toUnmodifiableList());
        verify(wordReviewHistoryService).createEmptyWordReviewHistoryForWords(TEST_USERNAME, expectedNewHistoryWords);
    }

    @Test
    public void testSaveWords_NotOwnedLexicon() {
        try {
            wordService.saveWords(
                    List.of(new Word(UUID.randomUUID().toString(), TEST_LEXICON_METADATA.id(), "", Map.of("kana", " a", "meaning", "a"),"a", List.of("a.mp3"), null, null)),
                    TEST_LEXICON_METADATA.id(),
                    "notTheOwningUsername");
        } catch (UserAccessException ex) {
            return;
        }

        fail("Expected UserAccessException");
    }

    @Test
    public void testDeleteWords() {
        Word deletedWord = new Word(UUID.randomUUID().toString(), TEST_LEXICON_METADATA.id(), TEST_USERNAME, Map.of("kana", " a", "meaning", "a"),"a", List.of("a.mp3"), null, null);
        Word deletedWordNoAudio = new Word(UUID.randomUUID().toString(), TEST_LEXICON_METADATA.id(), TEST_USERNAME, Map.of("kana", " b", "meaning", "b"),"b", List.of(), null, null);
        Word notOwnedWord = new Word(UUID.randomUUID().toString(), TEST_LEXICON_METADATA.id(), "differentOwner", Map.of("kana", " c", "meaning", "c"),"c", List.of("c.mp3"), null, null);
        Word notInLexiconWord = new Word(UUID.randomUUID().toString(), UUID.randomUUID().toString(), TEST_USERNAME, Map.of("kana", " d", "meaning", "d"),"d", List.of("d.mp3"), null, null);
        String nonExistantId = UUID.randomUUID().toString();

        when(wordDao.loadWords(List.of(deletedWord.id(), deletedWordNoAudio.id(), notOwnedWord.id(), notInLexiconWord.id(), nonExistantId))).thenReturn(List.of(deletedWord, deletedWordNoAudio, notOwnedWord, notInLexiconWord));

        wordService.deleteWords(TEST_LEXICON_METADATA.id(), List.of(deletedWord.id(), deletedWordNoAudio.id(), notOwnedWord.id(), notInLexiconWord.id(), nonExistantId), TEST_USERNAME);

        verify(blobDao).deleteAudioFiles(deletedWord.audioFiles());
        verifyNoMoreInteractions(blobDao);

        verify(wordDao).loadWords(List.of(deletedWord.id(), deletedWordNoAudio.id(), notOwnedWord.id(), notInLexiconWord.id(), nonExistantId));
        verify(wordDao).deleteWords(List.of(deletedWord.id(), deletedWordNoAudio.id()));
        verifyNoMoreInteractions(wordDao);
    }

    @Test
    public void testGetLexiconWordsBatch_NullFilter() {
        List<Word> wordsBatch = List.of(TEST_WORD_2, TEST_WORD_3);
        when(wordDao.getLexiconWordsBatch(TEST_LEXICON_METADATA.id(), TEST_USERNAME, 100, 0, TEST_WORD_1)).thenReturn(wordsBatch);

        assertEquals(wordsBatch, wordService.getLexiconWordsBatch(TEST_LEXICON_METADATA.id(), TEST_USERNAME, 100, 0, TEST_WORD_1, null));

        verify(wordDao).getLexiconWordsBatch(TEST_LEXICON_METADATA.id(), TEST_USERNAME, 100, 0, TEST_WORD_1);
        verifyNoMoreInteractions(wordDao);
    }

    @Test
    public void testGetLexiconWordsBatch_EmptyFilter() {
        List<Word> wordsBatch = List.of(TEST_WORD_2, TEST_WORD_3);
        when(wordDao.getLexiconWordsBatch(TEST_LEXICON_METADATA.id(), TEST_USERNAME, 100, 0, TEST_WORD_1)).thenReturn(wordsBatch);

        assertEquals(wordsBatch, wordService.getLexiconWordsBatch(TEST_LEXICON_METADATA.id(), TEST_USERNAME, 100, 0, TEST_WORD_1, WordFilterOptions.EMPTY_WORD_FILTERS));

        verify(wordDao).getLexiconWordsBatch(TEST_LEXICON_METADATA.id(), TEST_USERNAME, 100, 0, TEST_WORD_1);
        verifyNoMoreInteractions(wordDao);
    }

    @Test
    public void testGetLexiconWordsBatch_ElementFilter() {
        List<Word> wordsBatch = List.of(TEST_WORD_2, TEST_WORD_3);
        WordFilterOptions wordFilterOptions = new WordFilterOptions(Map.of("kana", "a"), null, null, null);
        when(wordDao.getLexiconWordsBatchWithFilter(TEST_LEXICON_METADATA.id(), TEST_USERNAME, 100, 0, TEST_WORD_1, wordFilterOptions)).thenReturn(wordsBatch);

        assertEquals(wordsBatch, wordService.getLexiconWordsBatch(TEST_LEXICON_METADATA.id(), TEST_USERNAME, 100, 0, TEST_WORD_1, wordFilterOptions));

        verify(wordDao).getLexiconWordsBatchWithFilter(TEST_LEXICON_METADATA.id(), TEST_USERNAME, 100, 0, TEST_WORD_1, wordFilterOptions);
        verifyNoMoreInteractions(wordDao);
    }

    @Test
    public void testGetLexiconWordsBatch_AttributeFilter() {
        List<Word> wordsBatch = List.of(TEST_WORD_2, TEST_WORD_3);
        WordFilterOptions wordFilterOptions = new WordFilterOptions(null, "a", null, null);
        when(wordDao.getLexiconWordsBatchWithFilter(TEST_LEXICON_METADATA.id(), TEST_USERNAME, 100, 0, TEST_WORD_1, wordFilterOptions)).thenReturn(wordsBatch);

        assertEquals(wordsBatch, wordService.getLexiconWordsBatch(TEST_LEXICON_METADATA.id(), TEST_USERNAME, 100, 0, TEST_WORD_1, wordFilterOptions));

        verify(wordDao).getLexiconWordsBatchWithFilter(TEST_LEXICON_METADATA.id(), TEST_USERNAME, 100, 0, TEST_WORD_1, wordFilterOptions);
        verifyNoMoreInteractions(wordDao);
    }

    @Test
    public void testGetLexiconWordsBatch_LearnedFilter() {
        List<Word> wordsBatch = List.of(TEST_WORD_2, TEST_WORD_3);
        WordFilterOptions wordFilterOptions = new WordFilterOptions(null, null, true, null);
        when(wordDao.getLexiconWordsBatchWithFilter(TEST_LEXICON_METADATA.id(), TEST_USERNAME, 100, 0, TEST_WORD_1, wordFilterOptions)).thenReturn(wordsBatch);

        assertEquals(wordsBatch, wordService.getLexiconWordsBatch(TEST_LEXICON_METADATA.id(), TEST_USERNAME, 100, 0, TEST_WORD_1, wordFilterOptions));

        verify(wordDao).getLexiconWordsBatchWithFilter(TEST_LEXICON_METADATA.id(), TEST_USERNAME, 100, 0, TEST_WORD_1, wordFilterOptions);
        verifyNoMoreInteractions(wordDao);
    }

    @Test
    public void testGetLexiconWordsBatch_AudioFilter() {
        List<Word> wordsBatch = List.of(TEST_WORD_2, TEST_WORD_3);
        WordFilterOptions wordFilterOptions = new WordFilterOptions(null, null, null, true);
        when(wordDao.getLexiconWordsBatchWithFilter(TEST_LEXICON_METADATA.id(), TEST_USERNAME, 100, 0, TEST_WORD_1, wordFilterOptions)).thenReturn(wordsBatch);

        assertEquals(wordsBatch, wordService.getLexiconWordsBatch(TEST_LEXICON_METADATA.id(), TEST_USERNAME, 100, 0, TEST_WORD_1, wordFilterOptions));

        verify(wordDao).getLexiconWordsBatchWithFilter(TEST_LEXICON_METADATA.id(), TEST_USERNAME, 100, 0, TEST_WORD_1, wordFilterOptions);
        verifyNoMoreInteractions(wordDao);
    }

    private static Word withOwner(Word word) {
        return new Word(word.id(), TEST_LEXICON_METADATA.id(), TEST_USERNAME, word.elements(), word.attributes(), word.audioFiles(), word.createInstant(), word.updateInstant());
    }

    private static Map<String, String> trimElements(Map<String, String> elements) {
        return elements.entrySet()
                .stream()
                .collect(Collectors.toMap(entry -> entry.getKey(), entry -> entry.getValue().trim()));
    }

    private static void assertInstantNow(Instant testStartInstant, Instant assertInstant) {
        // Verify that the value was set to Instant.now(). The exact value won't be known, so verify that Start <= Instant < Start+50ms
        assertTrue(!testStartInstant.isAfter(assertInstant));
        assertTrue(testStartInstant.plusMillis(INSTANT_NOW_ALLOWED_MARGIN_MILLIS).isAfter(assertInstant));
    }
}
