package com.gt.ssrs.word.aws;

import com.amazonaws.services.dynamodbv2.xspec.L;
import com.gt.ssrs.language.Language;
import com.gt.ssrs.language.WordElement;
import com.gt.ssrs.model.Word;
import com.gt.ssrs.model.WordFilterOptions;
import com.gt.ssrs.util.DDBTestServer;
import com.gt.ssrs.word.WordDao;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
public class WordDaoDDBTests {

    private static final int MAX_READ_BATCH_SIZE = 50;
    private static final int MAX_WRITE_BATCH_SIZE = 10;

    private static final Language TEST_LANGUAGE = Language.Japanese;
    private static final String TEST_LEXICON_ID = UUID.randomUUID().toString();
    private static final String TEST_OWNER = "testOwner";

    private static Instant SOURCE_INSTANT = Instant.now().minusSeconds(1);

    private static Word WORD_1 = buildWord("one", SOURCE_INSTANT.minusMillis(10000), SOURCE_INSTANT.minusMillis(1000));
    private static Word WORD_2 = buildWord("two", SOURCE_INSTANT.minusMillis(9999), SOURCE_INSTANT.minusMillis(999));
    private static Word WORD_3 = buildWord("three", SOURCE_INSTANT.minusMillis(9998), SOURCE_INSTANT.minusMillis(998));

    private DDBTestServer<DDBWord> ddbTestServer;

    private WordDao wordDao;

    @BeforeEach
    public void setup() {
        ddbTestServer = DDBTestServer.withTable(DDBWord.TABLE_NAME, DDBWord.class);

        wordDao = new WordDaoDDB(
                ddbTestServer.dynamoDbEnhancedClient(),
                MAX_READ_BATCH_SIZE,
                MAX_WRITE_BATCH_SIZE);
    }

    @AfterEach
    public void teardown() throws Exception {
        ddbTestServer.close();
    }

    @Test
    public void testSaveAndLoadWords() {
        wordDao.createWord(TEST_LANGUAGE, TEST_LEXICON_ID, WORD_1);
        wordDao.createWords(TEST_LANGUAGE, TEST_LEXICON_ID, List.of(WORD_2, WORD_3));

        verifyWordEquals(WORD_1, wordDao.loadWord(WORD_1.id()));
        verifyWordEquals(WORD_2, wordDao.loadWord(WORD_2.id()));
        verifyWordEquals(WORD_3, wordDao.loadWord(WORD_3.id()));

        verifyWordsEquals(List.of(WORD_1, WORD_3), wordDao.loadWords(List.of(WORD_1.id(), WORD_3.id())));

        Word updatedWord = buildWord(WORD_2.id(), "update", WORD_2.createInstant(), WORD_2.updateInstant(), false);
        wordDao.updateWord(TEST_LANGUAGE, updatedWord);
        verifyWordEquals(updatedWord, wordDao.loadWord(WORD_2.id()));
    }

    @Test
    public void testSaveAndLoadWords_MultipleBatches() {
        List<Word> originalWords = new ArrayList<>();
        Instant createInstant = Instant.now();

        for (int i = 0; i < 125; i++) {
            originalWords.add(buildWord("" + i, createInstant, createInstant));
        }

        wordDao.createWords(TEST_LANGUAGE, TEST_LEXICON_ID, originalWords);
        List<Word> loadedWords = wordDao.loadWords(originalWords.stream().map(word -> word.id()).collect(Collectors.toList()));

        assertEquals(
                originalWords.stream().map(Word::id).sorted().toList(),
                loadedWords.stream().map(Word::id).sorted().toList());
    }

    @Test
    public void testFindDuplicateWords() {
        Map<String, String> origElements = new HashMap<>();
        Map<String, String> nonDupElements = new HashMap<>();
        Map<String, String> dupElements = new HashMap<>();


        for (WordElement wordElement : Language.Japanese.getValidElements()) {
            if (Language.Japanese.getDedupeElements().contains(wordElement)) {
                origElements.put(wordElement.getId(), wordElement.getId() + "-dup");
                dupElements.put(wordElement.getId(), wordElement.getId() + "-dup");
            } else {
                origElements.put(wordElement.getId(), wordElement.getId() + "-orig");
                dupElements.put(wordElement.getId(), wordElement.getId() + "-different");
            }
            nonDupElements.put(wordElement.getId(), wordElement.getId() + "-nonDup");
        }

        Word origWord = new Word(UUID.randomUUID().toString(), TEST_LEXICON_ID, TEST_OWNER, origElements, "n", List.of(), Instant.now(), Instant.now());
        Word nonDupWord = new Word(UUID.randomUUID().toString(), TEST_LEXICON_ID, TEST_OWNER, nonDupElements, "n", List.of(), Instant.now(), Instant.now());
        Word dupWord = new Word(UUID.randomUUID().toString(), TEST_LEXICON_ID, TEST_OWNER, dupElements, "v1", List.of(), Instant.now(), Instant.now());

        wordDao.createWord(TEST_LANGUAGE, TEST_LEXICON_ID, origWord);

        // Make sure it doesn't give a false positive
        assertEquals(null, wordDao.findDuplicateWords(TEST_LANGUAGE, List.of(TEST_LEXICON_ID), TEST_OWNER, nonDupWord));

        // Make sure it only finds the duplicate
        wordDao.createWord(TEST_LANGUAGE, TEST_LEXICON_ID, nonDupWord);
        Word foundDupWord = wordDao.findDuplicateWords(TEST_LANGUAGE, List.of(TEST_LEXICON_ID), TEST_OWNER, dupWord);
        verifyWordEquals(origWord, foundDupWord);
    }

    @Test
    public void testDeleteWords() {
        wordDao.createWords(TEST_LANGUAGE, TEST_LEXICON_ID, List.of(WORD_1, WORD_2, WORD_3));
        assertEquals(3, wordDao.loadWords(List.of(WORD_1.id(), WORD_2.id(), WORD_3.id())).size());

        wordDao.deleteWords(List.of(WORD_1.id(), WORD_3.id()));

        List<Word> remainingWords = wordDao.loadWords(List.of(WORD_1.id(), WORD_2.id(), WORD_3.id()));
        assertEquals(1, remainingWords.size());
        verifyWordEquals(WORD_2, remainingWords.get(0));
    }

    @Test
    public void testDeleteAllLexiconWords() {
        wordDao.createWords(TEST_LANGUAGE, TEST_LEXICON_ID, List.of(WORD_1, WORD_2, WORD_3));
        assertEquals(3, wordDao.loadWords(List.of(WORD_1.id(), WORD_2.id(), WORD_3.id())).size());
        assertEquals(3, wordDao.getLexiconWordsBatch(TEST_LEXICON_ID, TEST_OWNER, 5, 0, null).size());

        wordDao.deleteAllLexiconWords(TEST_LEXICON_ID);
        assertEquals(List.of(), wordDao.loadWords(List.of(WORD_1.id(), WORD_2.id(), WORD_3.id())));
        assertEquals(List.of(), wordDao.getLexiconWordsBatch(TEST_LEXICON_ID, TEST_OWNER, 5, 0, null));
    }

    @Test
    public void testGetLexiconWordsBatch() {
        wordDao.createWords(TEST_LANGUAGE, TEST_LEXICON_ID, List.of(WORD_1, WORD_2, WORD_3));

        assertEquals(
                List.of(WORD_3.id(), WORD_2.id()),
                wordDao.getLexiconWordsBatch(TEST_LEXICON_ID, TEST_OWNER, 2, 0, null).stream().map(Word::id).toList());

        assertEquals(
                List.of(WORD_1.id()),
                wordDao.getLexiconWordsBatch(TEST_LEXICON_ID, TEST_OWNER, 2, 0, WORD_2).stream().map(Word::id).toList());
    }

    @Test
    public void getTestLexiconWordsBathWithFilter() {
        Word wordWithAudio = new Word(UUID.randomUUID().toString(), TEST_LEXICON_ID, TEST_OWNER, Map.of(WordElement.Kana.getId(), "kana-audio", WordElement.Meaning.getId(), "meaning-with-audio"), "n",  List.of("AudioFile.mp3"), Instant.now(), Instant.now());
        wordDao.createWords(TEST_LANGUAGE, TEST_LEXICON_ID, List.of(WORD_1, WORD_2, WORD_3, wordWithAudio));

        // Test a single element filter
        assertEquals(
                List.of(WORD_2.id()),
                wordDao.getLexiconWordsBatchWithFilter(TEST_LEXICON_ID, TEST_OWNER, 2, 0, null, new WordFilterOptions(Map.of(WordElement.Kana.getId(), "かなtwo"), null, null, null)).stream().map(Word::id).toList());

        // Test multiple element filters where both elements exist on the same word
        assertEquals(
                List.of(WORD_2.id()),
                wordDao.getLexiconWordsBatchWithFilter(TEST_LEXICON_ID, TEST_OWNER, 2, 0, null, new WordFilterOptions(Map.of(WordElement.Kana.getId(), "かなtwo", WordElement.Kanji.getId(), "漢字two"), null, null, null)).stream().map(Word::id).toList());

        // Test multiple element filters where both elements exist but are on different words
        assertEquals(
                List.of(),
                wordDao.getLexiconWordsBatchWithFilter(TEST_LEXICON_ID, TEST_OWNER, 2, 0, null, new WordFilterOptions(Map.of(WordElement.Kana.getId(), "かなtwo", WordElement.Kanji.getId(), "漢字one"), null, null, null)).stream().map(Word::id).toList());

        // Test attribute filter
        assertEquals(
                List.of(WORD_2.id()),
                wordDao.getLexiconWordsBatchWithFilter(TEST_LEXICON_ID, TEST_OWNER, 2, 0, null, new WordFilterOptions(null, "ntwo", null, null)).stream().map(Word::id).toList());

        // Test audio filter=true
        assertEquals(
                List.of(wordWithAudio.id()),
                wordDao.getLexiconWordsBatchWithFilter(TEST_LEXICON_ID, TEST_OWNER, 2, 0, null, new WordFilterOptions(null, null, null, true)).stream().map(Word::id).toList());

        // Test audio filter=false
        assertEquals(
                List.of(WORD_3.id(), WORD_2.id(), WORD_1.id()),
                wordDao.getLexiconWordsBatchWithFilter(TEST_LEXICON_ID, TEST_OWNER, 2, 0, null, new WordFilterOptions(null, null, null, false)).stream().map(Word::id).toList());

        // Test element + attribute
        assertEquals(
                List.of(),
                wordDao.getLexiconWordsBatchWithFilter(TEST_LEXICON_ID, TEST_OWNER, 2, 0, null, new WordFilterOptions(Map.of(WordElement.Kana.getId(), "かなtwo", WordElement.Kanji.getId(), "漢字one"), "ntwo", null, null)).stream().map(Word::id).toList());

        // Test element + attribute + audio
        assertEquals(
                List.of(wordWithAudio.id()),
                wordDao.getLexiconWordsBatchWithFilter(TEST_LEXICON_ID, TEST_OWNER, 2, 0, null, new WordFilterOptions(Map.of(WordElement.Kana.getId(), "kana-audio"), "n", null, true)).stream().map(Word::id).toList());

        // Test learned filter is ignored (since it's handled at a different level
        assertEquals(
                List.of(WORD_2.id()),
                wordDao.getLexiconWordsBatchWithFilter(TEST_LEXICON_ID, TEST_OWNER, 2, 0, null, new WordFilterOptions(null, "ntwo", true, null)).stream().map(Word::id).toList());
        assertEquals(
                List.of(WORD_2.id()),
                wordDao.getLexiconWordsBatchWithFilter(TEST_LEXICON_ID, TEST_OWNER, 2, 0, null, new WordFilterOptions(null, "ntwo", false, null)).stream().map(Word::id).toList());
    }

    @Test
    public void testGetAudioFileNamesForWord() {
        Word wordWithAudio = buildWord(UUID.randomUUID().toString(), "withAudio", Instant.now(), Instant.now(), true);
        wordDao.createWords(TEST_LANGUAGE, TEST_LEXICON_ID, List.of(WORD_1, WORD_2, WORD_3, wordWithAudio));

        assertEquals(List.of(), wordDao.getAudioFileNamesForWord(WORD_1.id()));
        assertEquals(List.of("audiowithAudio.mp3"), wordDao.getAudioFileNamesForWord(wordWithAudio.id()));
        assertEquals(
                Map.of(WORD_1.id(), List.of(),
                       WORD_2.id(), List.of(),
                       WORD_3.id(), List.of(),
                       wordWithAudio.id(), List.of("audiowithAudio.mp3")),
                wordDao.getAudioFileNamesForWordBatch(List.of(WORD_1.id(), WORD_2.id(), WORD_3.id(), wordWithAudio.id())));
    }

    @Test
    public void testSetAudioFileNameForWord() {
        Word wordWithAudio = buildWord(UUID.randomUUID().toString(), "withAudio", Instant.now(), Instant.now(), true);
        wordDao.createWords(TEST_LANGUAGE, TEST_LEXICON_ID, List.of(WORD_1, WORD_2, WORD_3, wordWithAudio));

        assertEquals(1, wordDao.setAudioFileNameForWord(WORD_2.id(), "word2Audio_0.mp3"));
        assertEquals(0, wordDao.setAudioFileNameForWord("fakeWordId", "fake.mp3"));

        assertEquals(
                Map.of(WORD_1.id(), List.of(),
                       WORD_2.id(), List.of("word2Audio_0.mp3"),
                       WORD_3.id(), List.of(),
                       wordWithAudio.id(), List.of("audiowithAudio.mp3")),
                wordDao.getAudioFileNamesForWordBatch(List.of(WORD_1.id(), WORD_2.id(), WORD_3.id(), wordWithAudio.id())));

        wordDao.setAudioFileNameForWords(Map.of(WORD_1.id(), List.of("word1Audio.mp3"), WORD_3.id(), List.of("word3Audio_0.mp3", "word3Audio_1.mp3")));
        assertEquals(1, wordDao.setAudioFileNameForWord(WORD_2.id(), "word2Audio_1.mp3"));

        assertEquals(
                Map.of(WORD_1.id(), List.of("word1Audio.mp3"),
                       WORD_2.id(), List.of("word2Audio_0.mp3", "word2Audio_1.mp3"),
                       WORD_3.id(), List.of("word3Audio_0.mp3", "word3Audio_1.mp3"),
                       wordWithAudio.id(), List.of("audiowithAudio.mp3")),
                wordDao.getAudioFileNamesForWordBatch(List.of(WORD_1.id(), WORD_2.id(), WORD_3.id(), wordWithAudio.id())));

        wordDao.deleteAudioFileName(WORD_3.id(), "word3Audio_0.mp3");
        assertEquals(
                Map.of(WORD_1.id(), List.of("word1Audio.mp3"),
                       WORD_2.id(), List.of("word2Audio_0.mp3", "word2Audio_1.mp3"),
                       WORD_3.id(), List.of(  "word3Audio_1.mp3"),
                       wordWithAudio.id(), List.of("audiowithAudio.mp3")),
                wordDao.getAudioFileNamesForWordBatch(List.of(WORD_1.id(), WORD_2.id(), WORD_3.id(), wordWithAudio.id())));
    }

    @Test
    public void testGetWordsUniqueToLexicon() {
        String lexiconId2 = UUID.randomUUID().toString();
        Word lexicond2Word = new Word(UUID.randomUUID().toString(), lexiconId2, TEST_OWNER, WORD_3.elements(), WORD_3.attributes(), List.of(), Instant.now(), Instant.now());

        wordDao.createWords(TEST_LANGUAGE, TEST_LEXICON_ID, List.of(WORD_1, WORD_2));
        wordDao.createWords(TEST_LANGUAGE, lexiconId2, List.of(lexicond2Word));

        assertEquals(Set.of(WORD_1.id(), WORD_2.id()), Set.copyOf(wordDao.getWordsUniqueToLexicon(TEST_LEXICON_ID)));
        assertEquals(Set.of(lexicond2Word.id()), Set.copyOf(wordDao.getWordsUniqueToLexicon(lexiconId2)));
    }

    @Test
    public void testGetTotalLexiconWordCount() {
        String lexiconId2 = UUID.randomUUID().toString();
        Word lexicond2Word = new Word(UUID.randomUUID().toString(), lexiconId2, TEST_OWNER, WORD_3.elements(), WORD_3.attributes(), List.of(), Instant.now(), Instant.now());

        wordDao.createWords(TEST_LANGUAGE, TEST_LEXICON_ID, List.of(WORD_1, WORD_2));
        wordDao.createWords(TEST_LANGUAGE, lexiconId2, List.of(lexicond2Word));

        assertEquals(2, wordDao.getTotalLexiconWordCount(TEST_LEXICON_ID));
         assertEquals(1, wordDao.getTotalLexiconWordCount(lexiconId2));
    }

    @Test
    public void testGetUniqueElementValues() {
        String lexiconId2 = UUID.randomUUID().toString();
        Word lexicond2Word = new Word(UUID.randomUUID().toString(), lexiconId2, TEST_OWNER, WORD_3.elements(), WORD_3.attributes(), List.of(), Instant.now(), Instant.now());

        wordDao.createWords(TEST_LANGUAGE, TEST_LEXICON_ID, List.of(WORD_1, WORD_2));
        wordDao.createWords(TEST_LANGUAGE, lexiconId2, List.of(lexicond2Word));

        assertEquals(
                Set.of(WORD_1.elements().get(WordElement.Kana.getId()), WORD_2.elements().get(WordElement.Kana.getId())),
                Set.copyOf(wordDao.getUniqueElementValues(TEST_LEXICON_ID, WordElement.Kana, 5)));
        assertEquals(
                Set.of(WORD_1.elements().get(WordElement.Kanji.getId()), WORD_2.elements().get(WordElement.Kanji.getId())),
                Set.copyOf(wordDao.getUniqueElementValues(TEST_LEXICON_ID, WordElement.Kanji, 5)));
        assertEquals(
                Set.of(WORD_3.elements().get(WordElement.Kana.getId())),
                Set.copyOf(wordDao.getUniqueElementValues(lexiconId2 , WordElement.Kana, 5)));
    }

    private static Word buildWord(String elementSuffix, Instant createInstant, Instant updateInstant) {
        return buildWord(UUID.randomUUID().toString(), elementSuffix, createInstant, updateInstant, false);
    }

    private static Word buildWord(String id, String elementSuffix, Instant createInstant, Instant updateInstant, boolean withAudio) {
        return new Word(
                id,
                TEST_LEXICON_ID,
                TEST_OWNER,
                Map.of(WordElement.Kana.getId(), "かな" + elementSuffix,
                       WordElement.Meaning.getId(), "meaning" + elementSuffix,
                       WordElement.Kanji.getId(), "漢字" + elementSuffix,
                       WordElement.AlternateKanji.getId(), "代わり漢字" + elementSuffix,
                       WordElement.Accent.getId(), "0"),
                "n" + elementSuffix,
                withAudio ? List.of("audio" + elementSuffix + ".mp3") : List.of(),
                createInstant,
                updateInstant);
    }

    private static void verifyWordEquals(Word originalWord, Word loadedWord) {
        assertEquals(originalWord.id(), loadedWord.id());
        assertEquals(originalWord.lexiconId(), loadedWord.lexiconId());
        assertEquals(originalWord.owner(), loadedWord.owner());
        assertEquals(originalWord.elements(), loadedWord.elements());
        assertEquals(originalWord.audioFiles(), loadedWord.audioFiles());
        assertEquals(originalWord.createInstant(), loadedWord.createInstant());

        // Expects to update the update instant during the save
        assertTrue(loadedWord.updateInstant().isAfter(SOURCE_INSTANT));
    }

    private static void verifyWordsEquals(Collection<Word> originalWords, Collection<Word> loadedWords) {
        assertEquals(originalWords.size(), loadedWords.size());

        Map<String, Word> loadedWordById = loadedWords.stream().collect(Collectors.toMap(word -> word.id(), word -> word));
        for (Word originalWord : originalWords) {
            Word loadedWord = loadedWordById.get(originalWord.id());
            assertNotNull(loadedWord);
            verifyWordEquals(originalWord, loadedWord);
        }
    }
}
