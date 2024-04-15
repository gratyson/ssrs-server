package com.gt.ssrs.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record Lexicon(String id, String owner, String title, String description, long languageId, String imageFileName, List<String> wordIds) {
    public static final Lexicon EMPTY_LEXICON = new Lexicon("", "", "", "", 0, "", new ArrayList<String>());

    public Lexicon {
        wordIds = Collections.unmodifiableList(wordIds);
    }
}
