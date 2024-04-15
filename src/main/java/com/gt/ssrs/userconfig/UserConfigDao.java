package com.gt.ssrs.userconfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@Component
public class UserConfigDao {

    private static final Logger log = LoggerFactory.getLogger(UserConfigDao.class);

    private NamedParameterJdbcTemplate template;

    @Autowired
    public UserConfigDao(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.template = namedParameterJdbcTemplate;
    }

    private static final String INSERT_USER_CONFIG_ROW =
            "INSERT INTO user_config (username, setting_name, setting_value) " +
            "VALUES (:username, :settingName, :settingValue) " +
            "ON CONFLICT (username, setting_name) DO UPDATE " +
                    "SET setting_value = :settingValue";

    private static final String GET_CONFIG_FOR_USER =
            "SELECT setting_name, setting_value " +
            "FROM user_config " +
            "WHERE username = :username";

    private static final String DELETE_USER_CONFIG =
            "DELETE FROM user_config " +
            "WHERE username = :username AND setting_name in (:settingsToDelete)";

    public void saveUserConfig(String username, Map<String, String> userConfig) {
        int index = 0;
        SqlParameterSource[] sources = new SqlParameterSource[userConfig.size()];

        for(Map.Entry<String, String> configVal : userConfig.entrySet()) {
            MapSqlParameterSource source = new MapSqlParameterSource();
            source.addValue("username", username);
            source.addValue("settingName", configVal.getKey());
            source.addValue("settingValue", configVal.getValue());

            sources[index++] = source;
        }

        template.batchUpdate(INSERT_USER_CONFIG_ROW, sources);
    }

    public Map<String, String> getUserConfig(String username) {
        return template.query(GET_CONFIG_FOR_USER, Map.of("username", username), (rs) -> {
            Map<String, String> userConfig = new HashMap<>();

            while(rs.next()) {
                String settingName = rs.getString("setting_name");
                String settingValue = rs.getString("setting_value");

                if (settingName != null && !settingName.isBlank() && settingValue != null && !settingValue.isBlank()) {
                    userConfig.put(settingName, settingValue);
                }
            }

            return userConfig;
        });
    }

    public void deleteUserConfig(String username, Collection<String> settingsToDelete) {
        template.update(DELETE_USER_CONFIG, Map.of("username", username, "settingsToDelete", settingsToDelete));
    }
}
