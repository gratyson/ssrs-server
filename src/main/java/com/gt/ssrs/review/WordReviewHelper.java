package com.gt.ssrs.review;

import com.gt.ssrs.fuzzy.DatasetFuzzyMatcher;
import com.gt.ssrs.language.Language;
import com.gt.ssrs.language.TestRelationship;
import com.gt.ssrs.language.WordElement;
import com.gt.ssrs.lexicon.LexiconDao;
import com.gt.ssrs.lexicon.model.TestOnWordPair;
import com.gt.ssrs.model.ReviewMode;
import com.gt.ssrs.model.Word;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class WordReviewHelper {

    private static final Logger log = LoggerFactory.getLogger(WordReviewHelper.class);

    static final int MAX_VALUES_FOR_FUZZY_MATCHING = 10000;
    static final int MAX_DISTANCE = 6;
    static final int SIMILAR_WORD_CNT = 20;
    private static final int DEFAULT_MIN_TYPING_TEST_CHARACTERS = 10;
    private static final int DEFAULT_MIN_TYPING_TEST_ADDL_CHARACTERS = 6;
    private static final int DEFAULT_MAX_TYPING_TEST_ADDL_CHARACTERS = 8;

    private final LexiconDao lexiconDao;
    private final int testBaseTimeSec;
    private final int testAdditionalTimePerChar;
    private final int minTypingTestChars;
    private final int minTypingTestAddlChars;
    private final int maxTypingTestAddlChars;

    @Autowired
    public WordReviewHelper(LexiconDao lexiconDao,
                            @Value("${ssrs.review.testBaseTimeSec}") int testBaseTimeSec,
                            @Value("${ssrs.review.testAdditionalTimePerChar}") int testAdditionalTimePerChar,
                            @Value("${ssrs.review.minTypingTestChars:" + DEFAULT_MIN_TYPING_TEST_CHARACTERS + "}") int minTypingTestChars,
                            @Value("${ssrs.review.minTypingTestAddlChars:" + DEFAULT_MIN_TYPING_TEST_ADDL_CHARACTERS + "}") int minTypingTestAddlChars,
                            @Value("${ssrs.review.maxTypingTestAddlChars:" + DEFAULT_MAX_TYPING_TEST_ADDL_CHARACTERS + "}") int maxTypingTestAddlChars) {
        this.lexiconDao = lexiconDao;

        this.testBaseTimeSec = testBaseTimeSec;
        this.testAdditionalTimePerChar = testAdditionalTimePerChar;
        this.minTypingTestChars = minTypingTestChars;
        this.minTypingTestAddlChars = minTypingTestAddlChars;
        this.maxTypingTestAddlChars = maxTypingTestAddlChars;
    }

    public List<Word> getWordsToLearn(String lexiconId, String username, int wordCnt) {
        return lexiconDao.getWordsToLearn(lexiconId, username, wordCnt);
    }

    public Map<WordElement, Map<Word, List<String>>> findSimilarWordElementValues(String lexiconId, Collection<TestOnWordPair> testOnWordPairs) {
        Map<WordElement, Map<Word, List<String>>> similarWordElementValues = new HashMap<>();
        Map<WordElement, DatasetFuzzyMatcher> fuzzyMatchers = new HashMap<>();

        for (TestOnWordPair pair : testOnWordPairs) {
            similarWordElementValues
                    .computeIfAbsent(pair.testOn(),
                                     k -> new HashMap<>())
                    .put(pair.word(),
                         fuzzyMatchers
                                 .computeIfAbsent(pair.testOn(),
                                                  k -> new DatasetFuzzyMatcher(lexiconDao.getUniqueElementValues(lexiconId, pair.testOn(), MAX_VALUES_FOR_FUZZY_MATCHING)))
                                 .findSimilarTo(pair.word().elements().get(pair.testOn().getId()), SIMILAR_WORD_CNT, MAX_DISTANCE));
        }

        return similarWordElementValues;
    }

    public List<String> getSimilarCharacterSelection(Word word, WordElement testOn, List<String> similarElementValues) {
        String elementValue = word.elements().get(testOn.getId());
        List<String> characters = new ArrayList<>(toCharList(elementValue).stream().distinct().toList());

        Set<String> addlCharacterCandidates = new HashSet<>();
        for(String similarElementValue : similarElementValues) {
            addlCharacterCandidates.addAll(toCharList(similarElementValue));
        }

        List<String> addlCharacterCandidateList = new ArrayList<>(addlCharacterCandidates.stream().filter(c -> !characters.contains(c)).toList());

        if (addlCharacterCandidateList.size() > 0) {
            Collections.shuffle(addlCharacterCandidateList);
            int additionalCharCnt = calcTypingTestAddlCharacters(elementValue);
            characters.addAll(addlCharacterCandidateList.subList(0, Math.min(addlCharacterCandidateList.size() - 1, additionalCharCnt)));
        }

        Collections.shuffle(characters);
        return characters;
    }

    public List<String> getSimilarWordSelection(Word word, WordElement testOn, int selectionCount, List<String> similarElementValues) {
        String elementValue = word.elements().get(testOn.getId());
        List<String> selections = new ArrayList<>(List.of(elementValue));

        List<String> filteredSimiarElementValues =
                new ArrayList<>(similarElementValues.stream()
                    .distinct()
                    .filter(selection -> !selection.equals(elementValue))
                    .toList());

        if (filteredSimiarElementValues.size() > 0) {
            Collections.shuffle(filteredSimiarElementValues);

            selections.addAll(filteredSimiarElementValues.subList(0, filteredSimiarElementValues.size() < selectionCount ? filteredSimiarElementValues.size() - 1 : selectionCount - 1));
        }

        Collections.shuffle(selections);
        return selections;
    }


    public int getWordAllowedTime(Language language, Word word, ReviewMode reviewMode, TestRelationship testRelationship) {
        int testTimeSec = 0;

        if (reviewMode == ReviewMode.TypingTest) {
            testTimeSec = testBaseTimeSec + (word.elements().get(testRelationship.getTestOn().getId()).length() * testAdditionalTimePerChar);
            if (testRelationship.getTestOn().getTestTimeMultiplier() > 1) {
                testTimeSec *= testRelationship.getTestOn().getTestTimeMultiplier();
            }

        } else if (reviewMode == ReviewMode.MultipleChoiceTest) {
            testTimeSec = testBaseTimeSec;
        }

        return testTimeSec;
    }

    private int calcTypingTestAddlCharacters(String testOnElementValue) {
        int additionalChars = (int)Math.floor(Math.random() * (maxTypingTestAddlChars - minTypingTestAddlChars + 1)) + minTypingTestAddlChars;

        if (testOnElementValue.length() + additionalChars < minTypingTestChars) {
            return minTypingTestChars - testOnElementValue.length();
        }

        return additionalChars;
    }

    public static List<String> toCharList(String s) {
        return Arrays.stream(s.split("(?!^)")).toList();
    }
}
