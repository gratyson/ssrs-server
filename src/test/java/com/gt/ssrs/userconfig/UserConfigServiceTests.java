package com.gt.ssrs.userconfig;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
public class UserConfigServiceTests {

    private static final String TEST_USERNAME = "testUsername";

    private static final String CONFIG_SETTING_1 = "configSetting1";
    private static final String CONFIG_SETTING_2 = "configSetting2";
    private static final String CONFIG_SETTING_3 = "configSetting3";

    private static final String CONFIG_VALUE_1 = "configValue1";
    private static final String CONFIG_VALUE_2 = "configValue2";
    private static final String CONFIG_VALUE_3 = "configValue3";

    @MockitoBean private UserConfigDao userConfigDao;

    private UserConfigService userConfigService;

    @BeforeEach
    public void setup() {
        userConfigService = new UserConfigService(userConfigDao);

        when(userConfigDao.getUserConfig(TEST_USERNAME)).thenReturn(
                Map.of(CONFIG_SETTING_1, CONFIG_VALUE_1,
                       CONFIG_SETTING_2, CONFIG_VALUE_2));
    }

    @Test
    public void testGetUserConfig() {
        assertEquals(Map.of(CONFIG_SETTING_1, CONFIG_VALUE_1,
                            CONFIG_SETTING_2, CONFIG_VALUE_2),
                userConfigService.getUserConfig(TEST_USERNAME));
    }

    @Test
    public void testSaveUserConfig() {
        Map<String, String> newConfig = Map.of(CONFIG_SETTING_1, "newConfig",
                                               CONFIG_SETTING_3, CONFIG_VALUE_3);

        userConfigService.saveUserConfig(TEST_USERNAME, newConfig);

        verify(userConfigDao).deleteUserConfig(TEST_USERNAME, List.of(CONFIG_SETTING_2));
        verify(userConfigDao).saveUserConfig(TEST_USERNAME, newConfig);
    }
}
