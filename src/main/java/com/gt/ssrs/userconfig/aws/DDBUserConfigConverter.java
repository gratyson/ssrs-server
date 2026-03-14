package com.gt.ssrs.userconfig.aws;

import java.time.Instant;
import java.util.Map;

public class DDBUserConfigConverter {

    public static DDBUserConfig toUserDDBConfig(String username, Map<String, String> config) {
        return DDBUserConfig.builder()
                .username(username)
                .config(config)
                .updateInstant(Instant.now())
                .build();
    }
}
