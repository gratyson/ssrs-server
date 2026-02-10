package com.gt.ssrs.lexicon;

import com.gt.ssrs.blob.BlobDao;
import com.gt.ssrs.language.Language;
import com.gt.ssrs.model.LexiconMetadata;
import com.gt.ssrs.model.Word;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
public class LexiconServiceTests {

    private static final Language TEST_LANGUAGE = Language.Japanese;
    private static final String TEST_USERNAME = "testuser";
    private static final LexiconMetadata TEST_LEXICON_METADATA = new LexiconMetadata(UUID.randomUUID().toString(), TEST_USERNAME, "Test Lexicon", "Test Lexicon", TEST_LANGUAGE.getId(), "");

    private static final int INSTANT_NOW_ALLOWED_MARGIN_MILLIS = 50;

    private static final Word TEST_WORD_1 = new Word(
            UUID.randomUUID().toString(),
            TEST_LEXICON_METADATA.id(),
            TEST_USERNAME,
            Map.of("kana", "かな", "meaning", "kana", "kanji", "仮名"),
            "n",
            List.of(),
            Instant.EPOCH,
            Instant.now());
    private static final Word TEST_WORD_2 = new Word(
            UUID.randomUUID().toString(),
            TEST_LEXICON_METADATA.id(),
            TEST_USERNAME,
            Map.of("kana", "かな2", "meaning", "kana2", "kanji", "仮名2"),
            "n",
            List.of(),
            Instant.EPOCH,
            Instant.now());
    private static final Word TEST_WORD_3 = new Word(
            UUID.randomUUID().toString(),
            TEST_LEXICON_METADATA.id(),
            TEST_USERNAME,
            Map.of("kana", "かな3", "meaning", "kana3", "kanji", "仮名3"),
            "n",
            List.of(),
            Instant.EPOCH,
            Instant.now());


    @Mock private LexiconDao lexiconDao;
    @Mock private BlobDao blobDao;

    private LexiconService lexiconService;

    @BeforeEach
    public void setup() {
        lexiconService = new LexiconService(lexiconDao, blobDao);

        when(lexiconDao.getLexiconMetadata(TEST_LEXICON_METADATA.id())).thenReturn(TEST_LEXICON_METADATA);
    }

    @Test
    public void testGetLexiconLanguageId() {
        assertEquals(Language.Japanese.getId(), lexiconService.getLexiconLanguageId(TEST_LEXICON_METADATA.id()));
    }

    @Test
    public void testGetAllLexiconMetadata() {
        when(lexiconDao.getAllLexiconMetadata(TEST_USERNAME)).thenReturn(List.of(TEST_LEXICON_METADATA));

        assertEquals(List.of(TEST_LEXICON_METADATA), lexiconService.getAllLexiconMetadata(TEST_USERNAME));
    }

    @Test
    public void testGetLexiconMetadata() {
        assertEquals(TEST_LEXICON_METADATA, lexiconService.getLexiconMetadata(TEST_LEXICON_METADATA.id()));
    }
}
