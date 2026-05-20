package com.gt.ssrs.word.aws;

import com.gt.ssrs.language.Language;
import com.gt.ssrs.language.WordElement;
import com.gt.ssrs.model.Word;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
public class DDBWordConverterTests {

    private static final Language TEST_LANGUAGE = Language.Japanese;
    private static final String TEST_LEXICON_ID = UUID.randomUUID().toString();
    private static final String TEST_OWNER = "testOwner";
    private static final Instant CREATE_INSTANT = Instant.now().minusSeconds(60);

    private Instant testStartInstant;

    @BeforeEach
    public void setup() {
        testStartInstant = Instant.now();
    }

    private static final Word TEST_WORD_1 = new Word(
            UUID.randomUUID().toString(),
            TEST_LEXICON_ID,
            TEST_OWNER,
            Map.of(WordElement.Kana.getId(), "kana1",
                    WordElement.Meaning.getId(), "meaning1",
                    WordElement.Kanji.getId(), "kanji1",
                    WordElement.AlternateKanji.getId(), "altKanji1",
                    WordElement.Accent.getId(), "1"),
            "n",
            List.of("audio1_0.mp3", "audio1_1.mp3"),
            CREATE_INSTANT,
            CREATE_INSTANT);
    private static final Word TEST_WORD_2 = new Word(
            UUID.randomUUID().toString(),
            TEST_LEXICON_ID,
            TEST_OWNER,
            Map.of(WordElement.Kana.getId(), "kana1",
                    WordElement.Meaning.getId(), "meaning1"),
            "v1",
            List.of("audio2_0.mp3"),
            CREATE_INSTANT,
            CREATE_INSTANT);

    @Test
    public void testConvertWord() {
        DDBWord ddbWord = DDBWordConverter.convertWord(TEST_LANGUAGE, TEST_WORD_1);

        assertWordEquals(TEST_WORD_1, ddbWord, false);

        Word originalWord = DDBWordConverter.convertDDBWord(ddbWord);
        assertWordEquals(originalWord, ddbWord, true);
    }

    @Test
    public void testConvertWordBatch() {
        List<DDBWord> ddbWords = DDBWordConverter.convertWordBatch(TEST_LANGUAGE, List.of(TEST_WORD_1, TEST_WORD_2));

        assertEquals(2, ddbWords.size());
        assertWordEquals(TEST_WORD_1, ddbWords.get(0), false);
        assertWordEquals(TEST_WORD_2, ddbWords.get(1), false);

        assertTrue(ddbWords.get(0).updateInstant().isBefore(ddbWords.get(1).updateInstant()));
    }


    @Test
    public void testComputeDepupeHash() {
        assertEquals(
                DDBWordConverter.computeDepupeHash(TEST_LANGUAGE, TEST_WORD_1),
                DDBWordConverter.computeDepupeHash(TEST_LANGUAGE, TEST_WORD_1));

        assertNotEquals(
                DDBWordConverter.computeDepupeHash(TEST_LANGUAGE, TEST_WORD_1),
                DDBWordConverter.computeDepupeHash(TEST_LANGUAGE, TEST_WORD_2));

        Map<String, String> dupeElements = new HashMap<>();
        for (WordElement wordElement : TEST_LANGUAGE.getValidElements()) {
            if (TEST_LANGUAGE.getDedupeElements().contains(wordElement)) {
                dupeElements.put(wordElement.getId(), TEST_WORD_1.elements().get(wordElement.getId()));
            } else {
                dupeElements.put(wordElement.getId(), "notADupe");
            }
        }

        Word dupeWord = new Word(
                UUID.randomUUID().toString(),
                TEST_LEXICON_ID,
                TEST_OWNER,
                dupeElements,
                "n",
                List.of(),
                CREATE_INSTANT,
                CREATE_INSTANT);

        assertEquals(
                DDBWordConverter.computeDepupeHash(TEST_LANGUAGE, TEST_WORD_1),
                DDBWordConverter.computeDepupeHash(TEST_LANGUAGE, dupeWord));
    }

    private void assertWordEquals(Word word, DDBWord ddbWord, boolean updateInstantShouldBeEqual) {
        assertEquals(word.id(), ddbWord.id());
        assertEquals(word.lexiconId(), ddbWord.lexiconId());
        assertEquals(word.owner(), ddbWord.owner());
        assertEquals(word.elements(), ddbWord.elements());
        assertEquals(word.attributes(), ddbWord.attributes());
        assertEquals(word.audioFiles(), ddbWord.audioFiles());
        assertEquals(word.createInstant(), ddbWord.createInstant());

        if (updateInstantShouldBeEqual) {
            assertEquals(word.updateInstant(), ddbWord.updateInstant());
        } else {
            assertTrue(!ddbWord.updateInstant().isBefore(testStartInstant));
        }

        assertEquals(DDBWordConverter.computeDepupeHash(TEST_LANGUAGE, word), ddbWord.dedupeHash());
    }
}