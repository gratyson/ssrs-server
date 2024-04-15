package com.gt.ssrs.userconfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class UserConfigService {

    private static final Logger log = LoggerFactory.getLogger(UserConfigService.class);

    private final UserConfigDao userConfigDao;

    public UserConfigService(UserConfigDao userConfigDao) {
        this.userConfigDao = userConfigDao;
    }

    public Map<String, String> getUserConfig(String username) {
        return userConfigDao.getUserConfig(username);
    }

    public void saveUserConfig(String username, Map<String, String> newConfig) {
        Map<String, String> currentUserConfig = userConfigDao.getUserConfig(username);

        List<String> settingsToDelete = currentUserConfig.keySet().stream().filter(settingName -> !newConfig.containsKey(settingName)).toList();
        if (settingsToDelete.size() > 0) {
            userConfigDao.deleteUserConfig(username, settingsToDelete);
        }
        userConfigDao.saveUserConfig(username, newConfig);
    }
}
