package com.gt.ssrs.language;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.gt.ssrs.model.ReviewMode;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum Language {

    Japanese(1,
            "Japanese",
            "Hiragino Kaku Gothic Pro, Meiryo",
            "(^%kana%_[0-9]+\\..*)|(^%kana%_\\(%kanji%\\)_[0-9]+\\..*)",
            3,
            List.of(WordElement.Kana, WordElement.Meaning, WordElement.Kanji, WordElement.AlternateKanji, WordElement.Accent),
            List.of(WordElement.Kana, WordElement.Meaning),
            List.of(WordElement.Kana, WordElement.Meaning, WordElement.Kanji),
            List.of(WordElement.Kana, WordElement.Kanji),
            List.of(WordElement.Kana, WordElement.Meaning, WordElement.Kanji, WordElement.AlternateKanji),
            List.of(TestRelationship.MeaningToKana, TestRelationship.MeaningToKanji, TestRelationship.KanjiToKana),
            List.of(new LearningTestOptions(ReviewMode.WordOverview, 0, false, null),
                    new LearningTestOptions(ReviewMode.MultipleChoiceTest, 4, false, TestRelationship.MeaningToKana),
                    new LearningTestOptions(ReviewMode.MultipleChoiceTest, 6, false, TestRelationship.KanaToMeaning),
                    new LearningTestOptions(ReviewMode.TypingTest, 0, false, TestRelationship.MeaningToKana),
                    new LearningTestOptions(ReviewMode.MultipleChoiceTest, 4, false, TestRelationship.MeaningToKanji),
                    new LearningTestOptions(ReviewMode.MultipleChoiceTest, 6, false, TestRelationship.KanjiToMeaning),
                    new LearningTestOptions(ReviewMode.TypingTest, 0, false, TestRelationship.MeaningToKanji),
                    new LearningTestOptions(ReviewMode.MultipleChoiceTest, 6, false, TestRelationship.KanjiToKana),
                    new LearningTestOptions(ReviewMode.MultipleChoiceTest, 8, false, TestRelationship.KanaToKanji),
                    new LearningTestOptions(ReviewMode.TypingTest, 0, false, TestRelationship.KanjiToKana),
                    new LearningTestOptions(ReviewMode.TypingTest, 0, false, TestRelationship.MeaningToKana),
                    new LearningTestOptions(ReviewMode.TypingTest, 0, false, TestRelationship.MeaningToKanji),
                    new LearningTestOptions(ReviewMode.TypingTest, 0, true, TestRelationship.KanjiToKana)));


    private long id;
    private String displayName;
    private String fontName;
    private String audioFileRegex;
    private double testsToDouble;
    private List<WordElement> validElements;
    private List<WordElement> requiredElements;
    private List<WordElement> coreElements;
    private List<WordElement> dedupeElements;
    private List<WordElement> overviewElements;
    private List<TestRelationship> reviewTestRelationships;
    private List<LearningTestOptions> learningSequence;

    Language(long id, String displayName, String fontName, String audioFileRegex, double testsToDouble, List<WordElement> validElements, List<WordElement> requiredElements, List<WordElement> coreElements, List<WordElement> dedupeElements, List<WordElement> overviewElements, List<TestRelationship> reviewTestRelationships, List<LearningTestOptions> learningSequence) {
        this.id = id;
        this.displayName = displayName;
        this.fontName = fontName;
        this.audioFileRegex = audioFileRegex;
        this.testsToDouble = testsToDouble;
        this.validElements = Collections.unmodifiableList(validElements);
        this.requiredElements = Collections.unmodifiableList(requiredElements);
        this.coreElements = Collections.unmodifiableList(coreElements);
        this.dedupeElements = Collections.unmodifiableList(dedupeElements);
        this.overviewElements = Collections.unmodifiableList(overviewElements);
        this.reviewTestRelationships = Collections.unmodifiableList(reviewTestRelationships);
        this.learningSequence = Collections.unmodifiableList(learningSequence);
    }

    private static Map<Long, Language> languagesById = Arrays.stream(Language.values()).collect(Collectors.toMap(l -> l.id, l -> l));

    public static Language getLanguageById(long id) {
        return languagesById.get(id);
    }

    public long getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getFontName() {
        return fontName;
    }

    public String getAudioFileRegex() {
        return audioFileRegex;
    }

    public double getTestsToDouble() {
        return testsToDouble;
    }

    public List<WordElement> getValidElements() {
        return validElements;
    }

    public List<WordElement> getRequiredElements() {
        return requiredElements;
    }

    public List<WordElement> getCoreElements() {
        return coreElements;
    }

    public List<WordElement> getDedupeElements() {
        return dedupeElements;
    }

    public List<WordElement> getOverviewElements() {
        return overviewElements;
    }

    public List<TestRelationship> getReviewTestRelationships() {
        return reviewTestRelationships;
    }

    public List<TestRelationship> getAllTestRelationships() {
        return Stream.concat(learningSequence.stream().map(ls -> ls.relationship()),
                             reviewTestRelationships.stream())
                .filter(tr -> tr != null)
                .distinct()
                .collect(Collectors.toUnmodifiableList());
    }

    public List<LearningTestOptions> getLearningSequence() {
        return learningSequence;
    }
}
