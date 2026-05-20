package com.gt.ssrs.lexicon.aws;

import com.gt.ssrs.language.Language;
import com.gt.ssrs.model.LexiconMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(SpringExtension.class)
public class DDBLexiconConverterTests {

    private static final String LEXICON_ID = UUID.randomUUID().toString();
    private static final String OWNER = "testOwner";
    private static final String TITLE = "Test Lexicon";
    private static final String DESCRIPTION = "Test Lexicon Description";
    private static final long LANGUAGE_ID = Language.Japanese.getId();
    private static final String IMAGE_FILE_NAME = "test_image.png";

    @Test
    public void testConvertDDBLexiconMetadata() {
        DDBLexiconMetadata ddbLexiconMetadata = DDBLexiconMetadata.builder()
                .id(LEXICON_ID)
                .owner(OWNER)
                .title(TITLE)
                .description(DESCRIPTION)
                .languageId(LANGUAGE_ID)
                .imageFileName(IMAGE_FILE_NAME)
                .build();

        LexiconMetadata lexiconMetadata = DDBLexiconConverter.convertDDBLexiconMetadata(ddbLexiconMetadata);

        assertEquals(LEXICON_ID, lexiconMetadata.id());
        assertEquals(OWNER, lexiconMetadata.owner());
        assertEquals(TITLE, lexiconMetadata.title());
        assertEquals(DESCRIPTION, lexiconMetadata.description());
        assertEquals(LANGUAGE_ID, lexiconMetadata.languageId());
        assertEquals(IMAGE_FILE_NAME, lexiconMetadata.imageFileName());
    }

    @Test
    public void testConvertLexiconMetadata() {
        LexiconMetadata lexiconMetadata = new LexiconMetadata(LEXICON_ID, OWNER, TITLE, DESCRIPTION, LANGUAGE_ID, IMAGE_FILE_NAME);

        DDBLexiconMetadata ddbLexiconMetadata = DDBLexiconConverter.convertLexiconMetadata(lexiconMetadata);

        assertEquals(LEXICON_ID, ddbLexiconMetadata.id());
        assertEquals(OWNER, ddbLexiconMetadata.owner());
        assertEquals(TITLE, ddbLexiconMetadata.title());
        assertEquals(DESCRIPTION, ddbLexiconMetadata.description());
        assertEquals(LANGUAGE_ID, ddbLexiconMetadata.languageId());
        assertEquals(IMAGE_FILE_NAME, ddbLexiconMetadata .imageFileName());
    }
}
