package com.gt.ssrs.review;

import com.gt.ssrs.lexicon.LexiconDao;
import com.gt.ssrs.lexicon.model.TestOnWordPair;
import com.gt.ssrs.model.Language;
import com.gt.ssrs.model.ReviewMode;
import com.gt.ssrs.model.Word;
import com.gt.ssrs.model.WordElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class WordReviewHelper {

    private static final Logger log = LoggerFactory.getLogger(WordReviewHelper.class);

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

    public Map<String, Map<String, List<String>>> findSimilarWordElementValuesBatch(String lexiconId, Collection<TestOnWordPair> testOnWordPairs) {
        return lexiconDao.findSimilarWordElementValuesBatch(lexiconId, testOnWordPairs, MAX_DISTANCE, SIMILAR_WORD_CNT);
    }

    public List<String> getSimilarCharacterSelection(Word word, String testOn, List<String> similarElementValues) {
        String elementValue = word.elements().get(testOn);
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

    public List<String> getSimilarWordSelection(Word word, String testOn, int selectionCount, List<String> similarElementValues) {
        String elementValue = word.elements().get(testOn);
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


    public int getWordAllowedTime(Language language, Word word, ReviewMode reviewMode, String testOn) {
        int testTimeSec = 0;

        if (reviewMode == ReviewMode.TypingTest) {
            testTimeSec = testBaseTimeSec + (word.elements().get(testOn).length() * testAdditionalTimePerChar);
            WordElement element = getWordElementById(language, testOn);
            if (element != null && element.testTimeMultiplier() > 1) {
                testTimeSec *= element.testTimeMultiplier();
            }

        } else if (reviewMode == ReviewMode.MultipleChoiceTest) {
            testTimeSec = testBaseTimeSec;
        }

        return testTimeSec;
    }

    private WordElement getWordElementById(Language language, String elementId) {
        for(WordElement wordElement : language.validElements()) {
            if (wordElement.id().equals(elementId)) {
                return wordElement;
            }
        }

        return null;
    }


    private int calcTypingTestAddlCharacters(String testOnElement) {
        int additionalChars = (int)Math.floor(Math.random() * (maxTypingTestAddlChars - minTypingTestAddlChars + 1)) + minTypingTestAddlChars;

        if (testOnElement.length() + additionalChars < minTypingTestChars) {
            return minTypingTestChars - testOnElement.length();
        }

        return additionalChars;
    }

    public static List<String> toCharList(String s) {
        return Arrays.stream(s.split("(?!^)")).toList();
    }
}
