package com.gt.ssrs.lexicon.aws;

import com.gt.ssrs.language.Language;
import com.gt.ssrs.lexicon.LexiconDao;
import com.gt.ssrs.model.LexiconMetadata;
import com.gt.ssrs.util.DDBTestServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
public class LexiconDaoDDBTests {

    private static final String TEST_USERNAME = "testUser";

    private static final LexiconMetadata OWNED_LEXICON_METADATA_1 = buildLexiconMetadata(TEST_USERNAME, "Lex1", "Lexicon 1", "image1.png");
    private static final LexiconMetadata OWNED_LEXICON_METADATA_2 = buildLexiconMetadata(TEST_USERNAME, "Lex2", "Lexicon 2", "image2.png");
    private static final LexiconMetadata NOT_OWNED_LEXICON_METADATA = buildLexiconMetadata("NotTestUser", "Lex3", "Lexicon 3", "image3.png");

    private DDBTestServer<DDBLexiconMetadata> ddbTestServer;

    private LexiconDao lexiconDao;

    @BeforeEach
    public void setup() {
        ddbTestServer = DDBTestServer.withTable(DDBLexiconMetadata.TABLE_NAME, DDBLexiconMetadata.class);

        lexiconDao = new LexiconDaoDDB(ddbTestServer.dynamoDbEnhancedClient());
    }

    @AfterEach
    public void teardown() throws Exception {
        ddbTestServer.close();
    }

    @Test
    public void testSaveAndLoadLexiconMetadata() {
        lexiconDao.createLexiconMetadata(OWNED_LEXICON_METADATA_1);

        LexiconMetadata loadedLexiconMetadata = lexiconDao.getLexiconMetadata(OWNED_LEXICON_METADATA_1.id());

        verifyLexiconMetadataEqual(OWNED_LEXICON_METADATA_1, loadedLexiconMetadata);
    }

    @Test
    public void testLoadAllLexiconMetadata() {
        lexiconDao.createLexiconMetadata(OWNED_LEXICON_METADATA_1);
        lexiconDao.createLexiconMetadata(OWNED_LEXICON_METADATA_2);
        lexiconDao.createLexiconMetadata(NOT_OWNED_LEXICON_METADATA);

        List<LexiconMetadata> ownedLexiconMetadataList = lexiconDao.getAllLexiconMetadata(TEST_USERNAME);

        assertEquals(2, ownedLexiconMetadataList.size());
        assertNotEquals(ownedLexiconMetadataList.get(0).id(), ownedLexiconMetadataList.get(1).id());
        for (LexiconMetadata ownedLexiconMetadata : ownedLexiconMetadataList) {
            LexiconMetadata originalLexiconMetadata = ownedLexiconMetadata.id().equals(OWNED_LEXICON_METADATA_1.id()) ? OWNED_LEXICON_METADATA_1 : OWNED_LEXICON_METADATA_2;

            verifyLexiconMetadataEqual(originalLexiconMetadata, ownedLexiconMetadata);
        }
    }

    @Test
    public void testGetLexiconMetadatas() {
        lexiconDao.createLexiconMetadata(OWNED_LEXICON_METADATA_1);
        lexiconDao.createLexiconMetadata(OWNED_LEXICON_METADATA_2);
        lexiconDao.createLexiconMetadata(NOT_OWNED_LEXICON_METADATA);

        List<LexiconMetadata> loadedLexiconMetadataList = lexiconDao.getLexiconMetadatas(List.of(OWNED_LEXICON_METADATA_2.id(), NOT_OWNED_LEXICON_METADATA.id()));

        assertEquals(2, loadedLexiconMetadataList.size());
        assertNotEquals(loadedLexiconMetadataList.get(0).id(), loadedLexiconMetadataList.get(1).id());
        for (LexiconMetadata loadedLexiconMetadata : loadedLexiconMetadataList) {
            LexiconMetadata originalLexiconMetadata = loadedLexiconMetadata.id().equals(OWNED_LEXICON_METADATA_2.id()) ? OWNED_LEXICON_METADATA_2 : NOT_OWNED_LEXICON_METADATA;

            verifyLexiconMetadataEqual(originalLexiconMetadata, loadedLexiconMetadata);
        }
    }

    @Test
    public void testUpdateLexiconMetadata() {
        lexiconDao.createLexiconMetadata(OWNED_LEXICON_METADATA_1);

        LexiconMetadata updatedLexiconMetadata = new LexiconMetadata(
                OWNED_LEXICON_METADATA_1.id(),
                TEST_USERNAME,
                "updated title",
                "updated description",
                Language.Japanese.getId(),
                "updatedFile.png");

        assertEquals(1, lexiconDao.updateLexiconMetadata(TEST_USERNAME, updatedLexiconMetadata));

        verifyLexiconMetadataEqual(updatedLexiconMetadata, lexiconDao.getLexiconMetadata(OWNED_LEXICON_METADATA_1.id()));
    }

    @Test
    public void testUpdateLexiconMetadata_DoesNotExist() {
        assertEquals(0, lexiconDao.updateLexiconMetadata(TEST_USERNAME, OWNED_LEXICON_METADATA_1));
    }

    @Test
    public void testUpdateLexiconMetadata_NotOwned() {
        lexiconDao.createLexiconMetadata(OWNED_LEXICON_METADATA_1);

        LexiconMetadata updatedLexiconMetadata = new LexiconMetadata(
                OWNED_LEXICON_METADATA_1.id(),
                TEST_USERNAME,
                "updated title",
                "updated description",
                Language.Japanese.getId(),
                "updatedFile.png");

        try {
            lexiconDao.updateLexiconMetadata("NotTheOwner", updatedLexiconMetadata);
        } catch (SecurityException ex) {
            // expected exception
            return;
        }

        fail("Expected SecurityException");
    }

    @Test
    public void testUpdateLexiconMetadata_ChangingOwner() {
        lexiconDao.createLexiconMetadata(OWNED_LEXICON_METADATA_1);

        LexiconMetadata updatedLexiconMetadata = new LexiconMetadata(
                OWNED_LEXICON_METADATA_1.id(),
                "NotTheOwner",
                "updated title",
                "updated description",
                Language.Japanese.getId(),
                "updatedFile.png");

        try {
            lexiconDao.updateLexiconMetadata(TEST_USERNAME, updatedLexiconMetadata);
        } catch (SecurityException ex) {
            // expected exception
            return;
        }

        fail("Expected SecurityException");
    }

    @Test
    public void testDeleteLexiconMetadata() {
        lexiconDao.createLexiconMetadata(OWNED_LEXICON_METADATA_1);
        lexiconDao.createLexiconMetadata(OWNED_LEXICON_METADATA_2);

        assertEquals(2, lexiconDao.getAllLexiconMetadata(TEST_USERNAME).size());

        lexiconDao.deleteLexiconMetadata(OWNED_LEXICON_METADATA_1.id());

        List<LexiconMetadata> loadedLexiconMetadataList = lexiconDao.getAllLexiconMetadata(TEST_USERNAME);
        assertEquals(1, loadedLexiconMetadataList.size());
        verifyLexiconMetadataEqual(OWNED_LEXICON_METADATA_2, loadedLexiconMetadataList.get(0));
    }

    private void verifyLexiconMetadataEqual(LexiconMetadata expected, LexiconMetadata actual) {
        assertEquals(expected.id(), actual.id());
        assertEquals(expected.owner(), actual.owner());
        assertEquals(expected.title(), actual.title());
        assertEquals(expected.description(), actual.description());
        assertEquals(expected.languageId(), actual.languageId());
        assertEquals(expected.imageFileName(), actual.imageFileName());
    }

    private static LexiconMetadata buildLexiconMetadata(String owner, String title, String description, String imageFileName) {
        return new LexiconMetadata(UUID.randomUUID().toString(), owner, title, description, Language.Japanese.getId(), imageFileName);
    }
}
