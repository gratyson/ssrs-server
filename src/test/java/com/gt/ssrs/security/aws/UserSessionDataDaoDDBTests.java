package com.gt.ssrs.security.aws;

import com.gt.ssrs.util.DDBTestServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(SpringExtension.class)
public class UserSessionDataDaoDDBTests {

    private static final String TEST_USER_ID = "testUser";

    private DDBTestServer<DDBUserSessionData> ddbTestServer;

    private UserSessionDataDaoDDB userSessionDataDao;

    @BeforeEach
    public void setup() {
        ddbTestServer = DDBTestServer.withTable(DDBUserSessionData.TABLE_NAME, DDBUserSessionData.class);

        userSessionDataDao = new UserSessionDataDaoDDB(ddbTestServer.dynamoDbEnhancedClient());
    }

    @AfterEach
    public void teardown() throws Exception {
        ddbTestServer.close();
    }

    @Test
    public void testSaveAndLoadUserSessionData() {
        DDBUserSessionData ddbUserSessionData = DDBUserSessionData.builder()
                .userId(TEST_USER_ID)
                .idToken(UUID.randomUUID().toString())
                .accessToken(UUID.randomUUID().toString())
                .refreshToken(UUID.randomUUID().toString())
                .expirationInstant(Instant.now().plusSeconds(3600))
                .build();

        userSessionDataDao.saveUserSessionData(ddbUserSessionData);

        DDBUserSessionData loadedUserSessionData = userSessionDataDao.loadUserSessionData(TEST_USER_ID);
        assertEquals(TEST_USER_ID, loadedUserSessionData.userId());
        assertEquals(ddbUserSessionData.idToken(), loadedUserSessionData.idToken());
        assertEquals(ddbUserSessionData.accessToken(), loadedUserSessionData.accessToken());
        assertEquals(ddbUserSessionData.accessToken(), loadedUserSessionData.accessToken());
        assertEquals(ddbUserSessionData.refreshToken(), loadedUserSessionData.refreshToken());
        assertEquals(ddbUserSessionData.expirationInstant(), loadedUserSessionData.expirationInstant());
    }
}
