package com.gt.ssrs.lexicon;

import com.gt.ssrs.audio.AudioService;
import com.gt.ssrs.blob.BlobDao;
import com.gt.ssrs.language.Language;
import com.gt.ssrs.model.LexiconMetadata;
import com.gt.ssrs.model.Word;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
public class LexiconServiceTests {

    private static final Language TEST_LANGUAGE = Language.Japanese;
    private static final String TEST_USERNAME = "testuser";
    private static final LexiconMetadata TEST_LEXICON_METADATA = new LexiconMetadata(UUID.randomUUID().toString(), TEST_USERNAME, "Test Lexicon", "Test Lexicon", TEST_LANGUAGE.getId(), "");

    private static final Word TEST_WORD_1 = new Word(
            UUID.randomUUID().toString(),
            TEST_LEXICON_METADATA.id(),
            TEST_USERNAME,
            Map.of("kana", "かな", "meaning", "kana", "kanji", "仮名"),
            "n",
            List.of(),
            Instant.EPOCH,
            Instant.now());


    @Mock private LexiconDao lexiconDao;
    @Mock private BlobDao blobDao;
    @Mock private AudioService audioService;

    private LexiconService lexiconService;

    @BeforeEach
    public void setup() {
        lexiconService = new LexiconService(lexiconDao, blobDao, audioService);

        when(lexiconDao.getLexiconMetadata(TEST_LEXICON_METADATA.id())).thenReturn(TEST_LEXICON_METADATA);

        when(lexiconDao.loadWord(TEST_WORD_1.id())).thenReturn(TEST_WORD_1);
        when(lexiconDao.loadWords(List.of(TEST_WORD_1.id()))).thenReturn(List.of(TEST_WORD_1));
    }

    @Test
    public void testLoadWord() {
        assertEquals(TEST_WORD_1, lexiconDao.loadWord(TEST_WORD_1.id()));
    }

    @Test
    public void testLoadWords() {
        assertEquals(List.of(TEST_WORD_1), lexiconDao.loadWords(List.of(TEST_WORD_1.id())));
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

        when(lexiconDao.updateWord(expectedWord)).thenReturn(1);

        assertEquals(expectedWord, lexiconService.updateWord(updatedWordWithoutUsername, TEST_USERNAME));
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

        assertNull(lexiconService.updateWord(updatedWordWithoutUsername, "different_username"));
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

        when(lexiconDao.updateWord(expectedWord)).thenReturn(0);

        assertNull(lexiconService.updateWord(updatedWordWithoutUsername, TEST_USERNAME));
    }

    @Test
    public void testSaveWords() {
        Word wordWithId = new Word(UUID.randomUUID().toString(), TEST_LEXICON_METADATA.id(), "", Map.of("kana", "a", "meaning", "a"),"a", List.of("a.mp3"), null, null);
        Word wordWithIdAlreadyExists = new Word(TEST_WORD_1.id(), TEST_LEXICON_METADATA.id(), "", Map.of("kana", "b", "meaning", "b"), "b", List.of("b.mp3"), Instant.EPOCH, Instant.now());
        Word wordWithIdDifferentOwner = new Word(UUID.randomUUID().toString(), TEST_LEXICON_METADATA.id(), "", Map.of("kana", "c", "meaning", "c"), "c", List.of("c.mp3"), Instant.EPOCH, Instant.now());
        when(lexiconDao.loadWord(wordWithIdDifferentOwner.id())).thenReturn(new Word(wordWithIdDifferentOwner.id(), TEST_LEXICON_METADATA.id(), "differentOwner", Map.of("kana", "c", "meaning", "c"), "c", List.of("c.mp3"), Instant.EPOCH, Instant.now()));

        Word wordNoIdNoDuplicate = new Word(null, TEST_LEXICON_METADATA.id(), "", Map.of("kana", "d", "meaning", "d"), "d", List.of("d.mp3"), Instant.EPOCH, Instant.now());
        Word wordNoIdWithDuplicate = new Word("", TEST_LEXICON_METADATA.id(), "", Map.of("kana", "e", "meaning", "e"), "e", List.of("e.mp3"), Instant.EPOCH, Instant.now());
        Word duplicateWord = new Word(UUID.randomUUID().toString(), TEST_LEXICON_METADATA.id(), TEST_USERNAME, Map.of("kana", "e", "meaning", "e"), "e", List.of("e.mp3"), Instant.EPOCH, Instant.now());
        when(lexiconDao.findDuplicateWordInOtherLexicons(TEST_LANGUAGE, TEST_LEXICON_METADATA.id(), TEST_USERNAME, wordNoIdWithDuplicate)).thenReturn(duplicateWord);

        Word wordNotSaved = new Word(UUID.randomUUID().toString(), TEST_LEXICON_METADATA.id(), "", Map.of("kana", "f", "meaning", "f"), "f", List.of("f.mp3"), null, null);
        Word wordFailingValidation = new Word(UUID.randomUUID().toString(), TEST_LEXICON_METADATA.id(), "", Map.of("kana", "h"), "h", List.of("h.mp3"), null, null);

        when(lexiconDao.createWords(eq(TEST_LANGUAGE), eq(TEST_LEXICON_METADATA.id()), anyList())).then(args ->
                ((List<Word>)args.getArgument(2)).stream().filter(word -> !word.id().equals(wordNotSaved.id())).collect(Collectors.toList()));

        List<Word> savedWords = lexiconService.saveWords(
                List.of(wordWithId, wordWithIdAlreadyExists, wordWithIdDifferentOwner, wordNoIdNoDuplicate,
                        wordNoIdWithDuplicate, wordNotSaved, wordFailingValidation),
                TEST_LEXICON_METADATA.id(), TEST_USERNAME);

        assertEquals(3, savedWords.size());
        assertEquals(List.of("a", "b", "d"), savedWords.stream().map(word -> word.elements().get("kana")).sorted().toList());
        for(Word word : savedWords) {
            assertTrue(word.id() != null && !word.id().isBlank());
            assertEquals(TEST_USERNAME, word.owner());

            String kana = word.elements().get("kana");
            assertEquals(kana, word.attributes());
            if (TEST_WORD_1.id().equals(word.id())) {
                assertEquals(TEST_WORD_1.audioFiles(), word.audioFiles());
            } else {
                assertEquals(List.of(kana + ".mp3"), word.audioFiles());
            }

            if (kana.equals("a")) {
                assertEquals(wordWithId.id(), word.id());
            } else if (kana.equals("b")) {
                assertEquals(wordWithIdAlreadyExists.id(), word.id());
            }
        }
    }

    private static Word withOwner(Word word) {
        return new Word(word.id(), TEST_LEXICON_METADATA.id(), TEST_USERNAME, word.elements(), word.attributes(), word.audioFiles(), word.createInstant(), word.updateInstant());
    }
}
