package com.gt.ssrs.userconfig;

import java.util.Collection;
import java.util.Map;

public interface UserConfigDao {

    void saveUserConfig(String username, Map<String, String> userConfig);

    Map<String, String> getUserConfig(String username);

    void deleteUserConfig(String username, Collection<String> settingsToDelete);
}
