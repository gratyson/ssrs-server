package com.gt.ssrs.lexicon;

import com.gt.ssrs.audio.AudioService;
import com.gt.ssrs.blob.BlobDao;
import com.gt.ssrs.language.LanguageService;
import com.gt.ssrs.model.Language;
import com.gt.ssrs.model.Lexicon;
import com.gt.ssrs.model.Word;
import com.gt.ssrs.util.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@SpringBootTest
public class LexiconServiceTests {

    private static final Language TEST_LANGUAGE = TestUtils.getTestLanguage();
    private static final String TEST_USERNAME = "testuser";
    private static final Lexicon TEST_LEXICON_METADATA = new Lexicon(UUID.randomUUID().toString(), TEST_USERNAME, "Test Lexicon", "Test Lexicon", TEST_LANGUAGE.id(), "", List.of());

    private static final Word TEST_WORD_1 = new Word(
            UUID.randomUUID().toString(),
            TEST_USERNAME,
            Map.of("kana", "かな", "meaning", "kana", "kanji", "仮名"),
            "n",
            List.of());


    @Mock private LexiconDao lexiconDao;
    @Mock private BlobDao blobDao;
    @Mock private LanguageService languageService;
    @Mock private AudioService audioService;

    private LexiconService lexiconService;

    @BeforeEach
    public void setup() {
        lexiconService = new LexiconService(lexiconDao, blobDao, languageService, audioService);

        when(lexiconDao.getLexiconMetadata(TEST_LEXICON_METADATA.id())).thenReturn(TEST_LEXICON_METADATA);
        when(languageService.GetLanguageById(TEST_LANGUAGE.id())).thenReturn(TEST_LANGUAGE);

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
                "",
                Map.of("kana", "かたかな", "meaning", "katakana"),
                "n",
                List.of());

        Word expectedWord = new Word(updatedWordWithoutUsername.id(), TEST_USERNAME, updatedWordWithoutUsername.elements(),
                updatedWordWithoutUsername.attributes(), updatedWordWithoutUsername.audioFiles());

        when(lexiconDao.updateWord(expectedWord)).thenReturn(1);

        assertEquals(expectedWord, lexiconService.updateWord(updatedWordWithoutUsername, TEST_USERNAME));
    }

    @Test
    public void testUpdateWord_NotOwner() {
        Word updatedWordWithoutUsername = new Word(
                TEST_WORD_1.id(),
                "",
                Map.of("kana", "かたかな", "meaning", "katakana"),
                "n",
                List.of());

        Word expectedWord = new Word(updatedWordWithoutUsername.id(), TEST_USERNAME, updatedWordWithoutUsername.elements(),
                updatedWordWithoutUsername.attributes(), updatedWordWithoutUsername.audioFiles());

        assertNull(lexiconService.updateWord(updatedWordWithoutUsername, "different_username"));
    }

    @Test
    public void testUpdateWord_FailedToSave() {
        Word updatedWordWithoutUsername = new Word(
                TEST_WORD_1.id(),
                "",
                Map.of("kana", "かたかな", "meaning", "katakana"),
                "n",
                List.of());

        Word expectedWord = withOwner(updatedWordWithoutUsername);

        when(lexiconDao.updateWord(expectedWord)).thenReturn(0);

        assertNull(lexiconService.updateWord(updatedWordWithoutUsername, TEST_USERNAME));
    }

    @Test
    public void testSaveWords() {
        Word wordWithId = new Word(UUID.randomUUID().toString(), "", Map.of("kana", "a", "meaning", "a"),"a", List.of("a.mp3"));
        Word wordWithIdAlreadyExists = new Word(TEST_WORD_1.id(), "", Map.of("kana", "b", "meaning", "b"), "b", List.of("b.mp3"));
        Word wordWithIdDifferentOwner = new Word(UUID.randomUUID().toString(), "", Map.of("kana", "c", "meaning", "c"), "c", List.of("c.mp3"));
        when(lexiconDao.loadWord(wordWithIdDifferentOwner.id())).thenReturn(new Word(wordWithIdDifferentOwner.id(), "differentOwner", Map.of("kana", "c", "meaning", "c"), "c", List.of("c.mp3")));

        Word wordNoIdNoDuplicate = new Word(null, "", Map.of("kana", "d", "meaning", "d"), "d", List.of("d.mp3"));
        Word wordNoIdWithDuplicate = new Word("", "", Map.of("kana", "e", "meaning", "e"), "e", List.of("e.mp3"));
        Word duplicateWord = new Word(UUID.randomUUID().toString(), TEST_USERNAME, Map.of("kana", "e", "meaning", "e"), "e", List.of("e.mp3"));
        when(lexiconDao.findDuplicateWordInOtherLexicons(TEST_LANGUAGE, TEST_LEXICON_METADATA.id(), TEST_USERNAME, wordNoIdWithDuplicate)).thenReturn(duplicateWord);

        Word wordNotSaved = new Word(UUID.randomUUID().toString(), "", Map.of("kana", "f", "meaning", "f"), "f", List.of("f.mp3"));
        Word wordNotAttached = new Word(UUID.randomUUID().toString(), "", Map.of("kana", "g", "meaning", "g"), "g", List.of("g.mp3"));
        Word wordFailingValidation = new Word(UUID.randomUUID().toString(), "", Map.of("kana", "h"), "h", List.of("h.mp3"));

        when(lexiconDao.createWords(eq(TEST_LANGUAGE), eq(TEST_LEXICON_METADATA.id()), anyList())).then(args ->
                ((List<Word>)args.getArgument(2)).stream().filter(word -> !word.id().equals(wordNotSaved.id())).collect(Collectors.toList()));

        when(lexiconDao.attachWordsToLexicon(eq(TEST_LEXICON_METADATA.id()), anyList(), eq(TEST_USERNAME))).then(args ->
                ((List<Word>)args.getArgument(1)).stream().filter(word -> !word.id().equals(wordNotAttached.id())).collect(Collectors.toList()));

        List<Word> savedWords = lexiconService.saveWords(
                List.of(wordWithId, wordWithIdAlreadyExists, wordWithIdDifferentOwner, wordNoIdNoDuplicate,
                        wordNoIdWithDuplicate, wordNotSaved, wordNotAttached, wordFailingValidation),
                TEST_LEXICON_METADATA.id(), TEST_USERNAME);

        assertEquals(4, savedWords.size());
        assertEquals(List.of("a", "b", "d", "e"), savedWords.stream().map(word -> word.elements().get("kana")).sorted().toList());
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
        return new Word(word.id(), TEST_USERNAME, word.elements(), word.attributes(), word.audioFiles());
    }
}
