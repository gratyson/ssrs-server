package com.gt.ssrs.userconfig.aws;

import com.gt.ssrs.userconfig.UserConfigDao;
import com.gt.ssrs.util.DDBTestServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@ExtendWith(SpringExtension.class)
public class UserConfigDaoDDBTests {

    private static final String TEST_USERNAME = "testUsername";

    private static final String CONFIG_SETTING_1 = "configSetting1";
    private static final String CONFIG_SETTING_2 = "configSetting2";

    private static final String CONFIG_VALUE_1 = "configValue1";
    private static final String CONFIG_VALUE_2 = "configValue2";

    private DDBTestServer<DDBUserConfig> ddbTestServer;

    private UserConfigDao userConfigDao;

    @BeforeEach
    public void setup() {
        ddbTestServer = DDBTestServer.withTable(DDBUserConfig.TABLE_NAME, DDBUserConfig.class);

        userConfigDao = new UserConfigDaoDDB(ddbTestServer.dynamoDbEnhancedClient());
    }

    @AfterEach
    public void teardown() throws Exception {
        ddbTestServer.close();
    }

    @Test
    public void testSaveUserConfig() {
        Map<String, String> userConfigToSave = Map.of(CONFIG_SETTING_1, CONFIG_VALUE_1,
                                                      CONFIG_SETTING_2, CONFIG_VALUE_2);

        userConfigDao.saveUserConfig(TEST_USERNAME, userConfigToSave);

        assertEquals(Map.of(CONFIG_SETTING_1, CONFIG_VALUE_1,
                            CONFIG_SETTING_2, CONFIG_VALUE_2),
                userConfigDao.getUserConfig(TEST_USERNAME));
        assertNull(userConfigToSave.get("anotherUser"));

        userConfigDao.deleteUserConfig(TEST_USERNAME, List.of(CONFIG_SETTING_1));

        assertEquals(Map.of(CONFIG_SETTING_2, CONFIG_VALUE_2), userConfigDao.getUserConfig(TEST_USERNAME));
    }
}