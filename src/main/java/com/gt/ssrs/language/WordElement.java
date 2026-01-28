package com.gt.ssrs.language;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum WordElement {

    Kana("kana", "Kana", "Kana", "", 2, true, 1, ""),
    Kanji("kanji", "Kanji", "Kanji", "", 2, true, 2, ""),
    AlternateKanji("alt_kanji", "Alternate Janji", "Alt Kanji", "", 2, true, 1, ""),
    Meaning("meaning", "Meaning", "Meaning", "", 5, false, 1, ""),
    Accent("accent", "Accent", "Accent", "The position in the word where the accent occurs. 0 indicates a flat accent. In the case of multiple valid accents, the positions are comma seperated and ordered from most common to least common.", 1, false, 1, "^\\d+(,\\d+)*$");

    private String id;
    private String name;
    private String abbreviation;
    private String description;
    private int weight;
    private boolean applyLanguageFont;
    private double testTimeMultiplier;
    private String validationRegex;

    WordElement(String id, String name, String abbreviation, String description, int weight, boolean applyLanguageFont, double testTimeMultiplier, String validationRegex) {
        this.id = id;
        this.name = name;
        this.abbreviation = abbreviation;
        this.description = description;
        this. weight = weight;
        this.applyLanguageFont = applyLanguageFont;
        this.testTimeMultiplier = testTimeMultiplier;
        this.validationRegex = validationRegex;
    }

    private static Map<String, WordElement> wordElementsById = Arrays.stream(WordElement.values()).collect(Collectors.toMap(we -> we.id, we -> we));

    public static WordElement getWordElementById(String id) {
        return wordElementsById.get(id);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getAbbreviation() {
        return abbreviation;
    }

    public String getDescription() {
        return description;
    }

    public int getWeight() {
        return weight;
    }

    public boolean isApplyLanguageFont() {
        return applyLanguageFont;
    }

    public double getTestTimeMultiplier() {
        return testTimeMultiplier;
    }

    public String getValidationRegex() {
        return validationRegex;
    }
}
