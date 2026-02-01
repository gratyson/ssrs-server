package com.gt.ssrs.model;

public record LexiconMetadata(String id, String owner, String title, String description, long languageId, String imageFileName) {
    public static final LexiconMetadata EMPTY_LEXICON = new LexiconMetadata("", "", "", "", 0, "");
}
