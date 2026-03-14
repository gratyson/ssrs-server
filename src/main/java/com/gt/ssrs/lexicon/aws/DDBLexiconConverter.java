package com.gt.ssrs.lexicon.aws;

import com.gt.ssrs.model.LexiconMetadata;

public class DDBLexiconConverter {

    public static LexiconMetadata convertDDBLexiconMetadata(DDBLexiconMetadata lexiconMetadataDDB) {
        return new LexiconMetadata(
                lexiconMetadataDDB.id(),
                lexiconMetadataDDB.owner(),
                lexiconMetadataDDB.title(),
                lexiconMetadataDDB.description(),
                lexiconMetadataDDB.languageId(),
                lexiconMetadataDDB.imageFileName());
    }

    public static DDBLexiconMetadata convertLexiconMetadata(LexiconMetadata lexiconMetadata) {
        return DDBLexiconMetadata.builder()
                .id(lexiconMetadata.id())
                .owner(lexiconMetadata.owner())
                .title(lexiconMetadata.title())
                .description(lexiconMetadata.description())
                .languageId(lexiconMetadata.languageId())
                .imageFileName(lexiconMetadata.imageFileName())
                .build();
    }
}
