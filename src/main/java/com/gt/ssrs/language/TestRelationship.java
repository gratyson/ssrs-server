package com.gt.ssrs.language;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum TestRelationship {

    MeaningToKana("meaning-to-kana", "Meaning → Kana", WordElement.Meaning, WordElement.Kana, WordElement.Kanji, null),
    MeaningToKanji("meaning-to-kanji", "Meaning → Kanji", WordElement.Meaning, WordElement.Kanji, WordElement.Kana, MeaningToKana),
    KanjiToKana("kanji-to-kana", "Kanji → Kana", WordElement.Kanji, WordElement.Kana, WordElement.Meaning, MeaningToKana),
    KanaToMeaning("kana-to-meaning", "Kana → Meaning", WordElement.Meaning, WordElement.Kana, WordElement.Kanji, null),
    KanjiToMeaning("kanji-to-meaning", "Kanji → Meaning", WordElement.Kanji, WordElement.Meaning, WordElement.Kana, KanaToMeaning),
    KanaToKanji("kana-to-kanji", "Kana → Kanji", WordElement.Kana, WordElement.Kanji, WordElement.Meaning, KanaToMeaning),

    EmptyTestRelationship("empty", "empty", null, null, null, null);

    private String id;
    private String displayName;
    private WordElement testOn;
    private WordElement promptWith;
    private WordElement showAfterTest;
    private TestRelationship fallback;

    TestRelationship(String id, String displayName, WordElement promptWith, WordElement testOn, WordElement showAfterTest, TestRelationship fallback) {
        this.id = id;
        this.displayName = displayName;
        this.testOn = testOn;
        this.promptWith = promptWith;
        this.showAfterTest = showAfterTest;
        this.fallback = fallback;
    }

    private static Map<String, TestRelationship> testRelationshipById = Arrays.stream(TestRelationship.values()).collect(Collectors.toMap(tr -> tr.id, tr -> tr));;

    public static TestRelationship getTestRelationshipById(String id) {
        return testRelationshipById.get(id);
    }

    public static TestRelationship getTestRelationshipByElements(String testOn, String promptWith) {
        for (TestRelationship testRelationship : values()) {
            if (testRelationship.getTestOn().getId().equals(testOn) && testRelationship.getPromptWith().getId().equals(promptWith)) {
                return testRelationship;
            }
        }

        return null;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public WordElement getTestOn() {
        return testOn;
    }

    public WordElement getPromptWith() {
        return promptWith;
    }

    public WordElement getShowAfterTest() {
        return showAfterTest;
    }

    public TestRelationship getFallback() {
        return fallback;
    }
}
