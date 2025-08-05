package com.gt.ssrs.util;

import com.gt.ssrs.model.Language;
import com.gt.ssrs.model.WordElement;
import com.gt.ssrs.model.TestRelationship;

import java.util.List;

public class TestUtils {

    private static final WordElement KANA = new WordElement("kana", "Kana", "Kana", 1, true, 0, "", "");
    private static final WordElement MEANING = new WordElement("meaning", "Meaning", "Meaning", 1, false, 0, "", "");
    private static final WordElement KANJI = new WordElement("kanji", "Kanji", "Kanji", 1, true, 2, "", "");
    private static final WordElement ALT_KANJI = new WordElement("altKanji", "Alternate Kanji", "Alt Kanji", 1, true, 0, "", "");
    private static final WordElement ACCENT = new WordElement("accent", "Accent", "Accent", 1, false, 0, "^\\d+(,\\d+)*$", "Stuff goes here");

    public static Language getTestLanguage() {
        return new Language(1, "Japanese", "Meiryo", "", 3,
                List.of(KANA, MEANING, KANJI, ALT_KANJI),
                List.of(KANA, MEANING),
                List.of(KANA, MEANING, KANJI),
                List.of(KANA, MEANING, KANJI),
                List.of(KANA, MEANING, KANJI, ALT_KANJI),
                List.of(new TestRelationship("meaning-to-kana", "Meaning->Kana", "kana", "meaning", "kanji", null, true),
                        new TestRelationship("meaning-to-kanji", "Meaning->Kanji", "kanji", "meaning", "kana", "meaning-to-kana", true),
                        new TestRelationship("kanji-to-kana", "Kanji->Kana", "kana", "kanji", "meaning", "meaning-to-kana", true),
                        new TestRelationship("kana-to-meaning", "Kana->Meaning", "meaning", "kana", "kanji", null, false),
                        new TestRelationship("kanji-to-meaning", "Kanji->Meaning","meaning", "kanji", "kana", "kana-to-meaning", false),
                        new TestRelationship("kana-to-kanj", "Kana->Kanji","kanji", "kana",  "meaning", "kana-to-meaning", false)));

    }
}
