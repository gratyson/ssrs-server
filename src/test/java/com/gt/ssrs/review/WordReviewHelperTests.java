package com.gt.ssrs.review;

import com.gt.ssrs.lexicon.LexiconDao;
import com.gt.ssrs.model.Language;
import com.gt.ssrs.model.ReviewMode;
import com.gt.ssrs.model.Word;
import com.gt.ssrs.util.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest
public class WordReviewHelperTests {

    private static final Language TEST_LANGUAGE = TestUtils.getTestLanguage();
    private static final String LEXICON_ID = UUID.randomUUID().toString();
    private static final String TEST_USERNAME = "testUsername";
    private static final String KANA_ELEMENT_VALUE = "よゆうをかます";
    private static final String KANJI_ELEMENT_VALUE = "低";
    private static final Word WORD = new Word(UUID.randomUUID().toString(), TEST_USERNAME,
            Map.of("kana", KANA_ELEMENT_VALUE, "meaning", "test meaning", "kanji", KANJI_ELEMENT_VALUE), "n", List.of());


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

        when(lexiconDao.getWordsToLearn(LEXICON_ID, TEST_USERNAME, wordCnt)).thenReturn(List.of(WORD));

        assertEquals(List.of(WORD), wordReviewHelper.getWordsToLearn(LEXICON_ID, TEST_USERNAME, wordCnt));
    }

    @Test
    public void testGetSimilarWordElementValues() {
        when(lexiconDao.findSimilarWordElementValues(LEXICON_ID, "kana", KANA_ELEMENT_VALUE, WordReviewHelper.MAX_DISTANCE, WordReviewHelper.SIMILAR_WORD_CNT))
                .thenReturn(SIMILAR_ELEMENT_VALUES);

        assertEquals(SIMILAR_ELEMENT_VALUES, wordReviewHelper.getSimilarWordElementValues(LEXICON_ID, WORD, "kana"));
    }

    @Test
    public void testGetSimilarWordElementValues_BlankValue() {
        assertEquals(List.of(), wordReviewHelper.getSimilarWordElementValues(LEXICON_ID, WORD, "addlKanji"));

        verifyNoInteractions(lexiconDao);
    }

    @Test
    public void testGetWordAllowedTime() {
        assertEquals(24, wordReviewHelper.getWordAllowedTime(TEST_LANGUAGE, WORD, ReviewMode.TypingTest, "kana"));   // Base time 10 + 7 chars @ 2 each
        assertEquals(24, wordReviewHelper.getWordAllowedTime(TEST_LANGUAGE, WORD, ReviewMode.TypingTest, "kanji"));  // Base time 10 + 1 char @ 2 each, all doubled
        assertEquals(10, wordReviewHelper.getWordAllowedTime(TEST_LANGUAGE, WORD, ReviewMode.MultipleChoiceTest, "kana"));   // Base time 10, no extra per char
        assertEquals(10, wordReviewHelper.getWordAllowedTime(TEST_LANGUAGE, WORD, ReviewMode.MultipleChoiceTest, "kanji"));  // Base time 10, no extra per char
        assertEquals(0, wordReviewHelper.getWordAllowedTime(TEST_LANGUAGE, WORD, ReviewMode.WordOverview, null));
    }

    @Test
    public void testGetSimilarCharacterSelection() {
        List<List<String>> similarCharacterSelections = new ArrayList<>();
        for (int i = 0; i < REPEATED_TEST_COUNT; i++) {
            similarCharacterSelections.add(wordReviewHelper.getSimilarCharacterSelection(WORD, "kana", SIMILAR_ELEMENT_VALUES));
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
            similarCharacterSelections.add(wordReviewHelper.getSimilarCharacterSelection(WORD, "kanji", SIMILAR_ELEMENT_VALUES));
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
            similarWordElementSelections.add(wordReviewHelper.getSimilarWordSelection(WORD, "kana", selectionCount, SIMILAR_ELEMENT_VALUES));
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
