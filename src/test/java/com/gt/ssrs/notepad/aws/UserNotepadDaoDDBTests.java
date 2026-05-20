package com.gt.ssrs.notepad.aws;

import com.gt.ssrs.notepad.UserNotepadDao;
import com.gt.ssrs.util.DDBTestServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(SpringExtension.class)
public class UserNotepadDaoDDBTests {

    private static final String TEST_USER = "testUser";
    private static final String TEST_TEXT = "Test\nText";

    private DDBTestServer<DDBUserNotepad> ddbTestServer;

    private UserNotepadDao userNotepadDao;

    @BeforeEach
    public void setup() {
        ddbTestServer = DDBTestServer.withTable(DDBUserNotepad.TABLE_NAME, DDBUserNotepad.class);

        userNotepadDao = new UserNotepadDaoDDB(ddbTestServer.dynamoDbEnhancedClient());
    }

    @AfterEach
    public void teardown() throws Exception {
        ddbTestServer.close();
    }

    @Test
    public void testSaveAndLoadNotepadText() {
        userNotepadDao.saveUserNotepadText(TEST_USER, TEST_TEXT);

        assertEquals(TEST_TEXT, userNotepadDao.getUserNotepadText(TEST_USER));
    }
}
