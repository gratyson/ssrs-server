package com.gt.ssrs.review;

import com.gt.ssrs.language.Language;
import com.gt.ssrs.language.TestRelationship;
import com.gt.ssrs.language.WordElement;
import com.gt.ssrs.lexicon.LexiconDao;
import com.gt.ssrs.lexicon.model.TestOnWordPair;
import com.gt.ssrs.model.ReviewMode;
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
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
public class WordReviewHelperTests {

    private static final Language TEST_LANGUAGE = Language.Japanese;
    private static final String LEXICON_ID = UUID.randomUUID().toString();
    private static final String TEST_USERNAME = "testUsername";
    private static final String KANA_ELEMENT_VALUE = "よゆうをかます";
    private static final String KANJI_ELEMENT_VALUE = "低";
    private static final Word WORD_1 = new Word(UUID.randomUUID().toString(), LEXICON_ID, TEST_USERNAME,
            Map.of("kana", KANA_ELEMENT_VALUE, "meaning", "test meaning", "kanji", KANJI_ELEMENT_VALUE),
            "n", List.of(), Instant.EPOCH, Instant.now());
    private static final Word WORD_2 = new Word(UUID.randomUUID().toString(), LEXICON_ID, TEST_USERNAME,
            Map.of("kana", KANA_ELEMENT_VALUE + "2", "meaning", "test meaning2", "kanji", KANJI_ELEMENT_VALUE + "2"),
            "n", List.of(), Instant.EPOCH, Instant.now());
    private static final Word WORD_3 = new Word(UUID.randomUUID().toString(), LEXICON_ID, TEST_USERNAME,
            Map.of("kana", KANA_ELEMENT_VALUE + "3", "meaning", "test meaning3", "kanji", KANJI_ELEMENT_VALUE + "3"),
            "n", List.of(), Instant.EPOCH, Instant.now());

    private static final List<String> SIMILAR_ELEMENT_VALUES = List.of("かたかな", "ことば", "おくりがな", "なりあがる", "くたくた", "ごうう", "うりとばす", "ちじょう", "ごうがん", "さしいれる", "よゆうをかます");
    private static final List<String> ALL_SIMILAR_ELEMENT_CHARACTERS = Arrays.stream("かたなことばおくりがあるごうすちじょんさしいれよゆをま".split("(?!^)")).toList();

    // How many times to repeat each test to verify the random aspects
    private static final int REPEATED_TEST_COUNT = 4;

    @Mock private LexiconDao lexiconDao;
    private final int testBaseTimeSec = 10;
    private final int testAdditionalTimePerChar = 2;
    private int minTypingTestChars = 8;
    private int minTypingTestAddlChars = 2;
    private int maxTypingTestAddlChars = 6;

    private WordReviewHelper wordReviewHelper;

    @BeforeEach
    public void setup() {
        wordReviewHelper = new WordReviewHelper(lexiconDao, testBaseTimeSec, testAdditionalTimePerChar, minTypingTestChars, minTypingTestAddlChars, maxTypingTestAddlChars);
    }

    @Test
    public void testGetWordsToLearn() {
        int wordCnt = 3;

        when(lexiconDao.getWordsToLearn(LEXICON_ID, TEST_USERNAME, wordCnt)).thenReturn(List.of(WORD_1));

        assertEquals(List.of(WORD_1), wordReviewHelper.getWordsToLearn(LEXICON_ID, TEST_USERNAME, wordCnt));
    }

    @Test
    public void testFindSimilarWordElementValues() {
        List<TestOnWordPair> words = List.of(
                new TestOnWordPair(WordElement.Kana, WORD_1),
                new TestOnWordPair(WordElement.Kanji, WORD_2),
                new TestOnWordPair(WordElement.Kana, WORD_3));

        List<String> kanaElements = new ArrayList<>();
        List<String> kanjiElements = new ArrayList<>();
        List<String> expectedSimiarKana = new ArrayList<>();
        List<String> expectedSimiarKanji = new ArrayList<>();

        for (int i = 0; i < WordReviewHelper.SIMILAR_WORD_CNT; i++) {
            expectedSimiarKana.add(KANA_ELEMENT_VALUE + "_" + i);
            kanaElements.add(KANA_ELEMENT_VALUE + "_" + i);
            kanaElements.add("not similar " + i);

            expectedSimiarKanji.add(KANJI_ELEMENT_VALUE + "_" + i);
            kanjiElements.add(KANJI_ELEMENT_VALUE + "_" + i);
            kanjiElements.add("not similar " + i);
        }

        when(lexiconDao.getUniqueElementValues(LEXICON_ID, WordElement.Kana, WordReviewHelper.MAX_VALUES_FOR_FUZZY_MATCHING)).thenReturn(kanaElements);
        when(lexiconDao.getUniqueElementValues(LEXICON_ID, WordElement.Kanji, WordReviewHelper.MAX_VALUES_FOR_FUZZY_MATCHING)).thenReturn(kanjiElements);

        Map<WordElement, Map<Word, List<String>>> result = wordReviewHelper.findSimilarWordElementValues(LEXICON_ID, words);

        assertEquals(2, result.keySet().size());
        Map<Word, List<String>> kanaWords = result.get(WordElement.Kana);
        Map<Word, List<String>> kanjiWords = result.get(WordElement.Kanji);

        assertEquals(2, kanaWords.keySet().size());
        assertTrue(List.of(WORD_1, WORD_3).containsAll(kanaWords.keySet()));
        assertEquals(WordReviewHelper.SIMILAR_WORD_CNT, kanaWords.get(WORD_1).size());
        assertTrue(kanaWords.get(WORD_1).containsAll(expectedSimiarKana));
        assertEquals(WordReviewHelper.SIMILAR_WORD_CNT, kanaWords.get(WORD_3).size());
        assertTrue(kanaWords.get(WORD_3).containsAll(expectedSimiarKana));

        assertEquals(Set.of(WORD_2), kanjiWords.keySet());
        assertEquals(WordReviewHelper.SIMILAR_WORD_CNT, kanjiWords.get(WORD_2).size());
        assertTrue(kanjiWords.get(WORD_2).containsAll(expectedSimiarKanji));

        verify(lexiconDao, times(1)).getUniqueElementValues(LEXICON_ID, WordElement.Kana, WordReviewHelper.MAX_VALUES_FOR_FUZZY_MATCHING);
        verify(lexiconDao, times(1)).getUniqueElementValues(LEXICON_ID, WordElement.Kanji, WordReviewHelper.MAX_VALUES_FOR_FUZZY_MATCHING);
        verifyNoMoreInteractions(lexiconDao);
    }

    @Test
    public void testGetWordAllowedTime() {
        assertEquals(24, wordReviewHelper.getWordAllowedTime(TEST_LANGUAGE, WORD_1, ReviewMode.TypingTest, TestRelationship.MeaningToKana));   // Base time 10 + 8 chars @ 2 each
        assertEquals(24, wordReviewHelper.getWordAllowedTime(TEST_LANGUAGE, WORD_1, ReviewMode.TypingTest, TestRelationship.MeaningToKanji));  // Base time 10 + 2 char @ 2 each, all doubled
        assertEquals(10, wordReviewHelper.getWordAllowedTime(TEST_LANGUAGE, WORD_1, ReviewMode.MultipleChoiceTest, TestRelationship.MeaningToKana));   // Base time 10, no extra per char
        assertEquals(10, wordReviewHelper.getWordAllowedTime(TEST_LANGUAGE, WORD_1, ReviewMode.MultipleChoiceTest, TestRelationship.MeaningToKanji));  // Base time 10, no extra per char
        assertEquals(0, wordReviewHelper.getWordAllowedTime(TEST_LANGUAGE, WORD_1, ReviewMode.WordOverview, null));
    }

    @Test
    public void testGetSimilarCharacterSelection() {
        List<List<String>> similarCharacterSelections = new ArrayList<>();
        for (int i = 0; i < REPEATED_TEST_COUNT; i++) {
            similarCharacterSelections.add(wordReviewHelper.getSimilarCharacterSelection(WORD_1, WordElement.Kana, SIMILAR_ELEMENT_VALUES));
        }

        for (int i = 0; i < REPEATED_TEST_COUNT; i++) {
            List<String> similarCharacterSelection = similarCharacterSelections.get(i);

            assertTrue(similarCharacterSelection.size() >= KANA_ELEMENT_VALUE.length() + minTypingTestAddlChars);
            assertTrue(similarCharacterSelection.size() <= KANA_ELEMENT_VALUE.length() + maxTypingTestAddlChars);
            verifyNoDuplicates(similarCharacterSelection);
            verifyValues(similarCharacterSelection, Arrays.stream(KANA_ELEMENT_VALUE.split("(?!^)")).toList(), ALL_SIMILAR_ELEMENT_CHARACTERS);

            for(int j = i + 1; j < REPEATED_TEST_COUNT; j++) {
                assertNotEquals(similarCharacterSelection, similarCharacterSelections.get(j));
            }
        }
    }

    @Test
    public void testGetSimilarCharacterSelection_ShortValue() {
        List<List<String>> similarCharacterSelections = new ArrayList<>();
        for (int i = 0; i < REPEATED_TEST_COUNT; i++) {
            similarCharacterSelections.add(wordReviewHelper.getSimilarCharacterSelection(WORD_1, WordElement.Kanji, SIMILAR_ELEMENT_VALUES));
        }

        for (int i = 0; i < REPEATED_TEST_COUNT; i++) {
            List<String> similarCharacterSelection = similarCharacterSelections.get(i);

            assertEquals(minTypingTestChars, similarCharacterSelection.size());
            verifyNoDuplicates(similarCharacterSelection);
            verifyValues(similarCharacterSelection, Arrays.stream(KANJI_ELEMENT_VALUE.split("(?!^)")).toList(), ALL_SIMILAR_ELEMENT_CHARACTERS);

            for(int j = i + 1; j < REPEATED_TEST_COUNT; j++) {
                assertNotEquals(similarCharacterSelection, similarCharacterSelections.get(j));
            }
        }
    }

    @Test
    public void testGetSimilarWordSelection_4() {
        testGetSimilarWordSelection(4);
    }

    @Test
    public void testGetSimilarWordSelection_6() {
        testGetSimilarWordSelection(6);
    }

    @Test
    public void testGetSimilarWordSelection_8() {
        testGetSimilarWordSelection(8);
    }

    private void testGetSimilarWordSelection(int selectionCount) {
        List<List<String>> similarWordElementSelections = new ArrayList<>();
        for (int i = 0; i < REPEATED_TEST_COUNT; i++) {
            similarWordElementSelections.add(wordReviewHelper.getSimilarWordSelection(WORD_1, WordElement.Kana, selectionCount, SIMILAR_ELEMENT_VALUES));
        }

        for (int i = 0; i < REPEATED_TEST_COUNT; i++) {
            List<String> similarWordElementSelection = similarWordElementSelections.get(i);

            assertEquals(selectionCount, similarWordElementSelection.size());
            verifyNoDuplicates(similarWordElementSelection);
            verifyValues(similarWordElementSelection, List.of(KANA_ELEMENT_VALUE), SIMILAR_ELEMENT_VALUES);

            for(int j = i + 1; j < REPEATED_TEST_COUNT; j++) {
                assertNotEquals(similarWordElementSelection, similarWordElementSelections.get(j));
            }
        }
    }

    private void verifyNoDuplicates(List<String> selections) {
        Set<String> selectionSet = selections.stream().collect(Collectors.toSet());

        assertEquals(selections.size(), selectionSet.size());
    }

    private void verifyValues(List<String> selections, List<String> requiredValues, List<String> optionalValues) {
        for(String requiredValue : requiredValues) {
            assertTrue(selections.contains(requiredValue));
        }

        for (String selection : selections) {
            assertTrue(requiredValues.contains(selection) || optionalValues.contains(selection));
        }
    }
}
