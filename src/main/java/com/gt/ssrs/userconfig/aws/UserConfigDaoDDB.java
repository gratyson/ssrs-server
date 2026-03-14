package com.gt.ssrs.userconfig.aws;

import com.gt.ssrs.userconfig.UserConfigDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class UserConfigDaoDDB implements UserConfigDao {

    private static final Logger log = LoggerFactory.getLogger(UserConfigDao.class);

    private final DynamoDbEnhancedClient dynamoDbEnhancedClient;

    private final DynamoDbTable<DDBUserConfig> userConfigTable;

    @Autowired
    public UserConfigDaoDDB(DynamoDbEnhancedClient dynamoDbEnhancedClient) {
        this.dynamoDbEnhancedClient = dynamoDbEnhancedClient;

        this.userConfigTable = dynamoDbEnhancedClient.table(DDBUserConfig.TABLE_NAME, TableSchema.fromImmutableClass(DDBUserConfig.class));
    }


    @Override
    public void saveUserConfig(String username, Map<String, String> userConfig) {
        userConfigTable.putItem(DDBUserConfigConverter.toUserDDBConfig(username, userConfig));
    }

    @Override
    public Map<String, String> getUserConfig(String username) {
        DDBUserConfig ddbUserConfig = userConfigTable.getItem(Key.builder().partitionValue(username).build());

        if (ddbUserConfig == null) {
            return Map.of();
        }

        return ddbUserConfig.config();
    }

    @Override
    public void deleteUserConfig(String username, Collection<String> settingsToDelete) {
        DDBUserConfig existingUserConfig = userConfigTable.getItem(Key.builder().partitionValue(username).build());

        if (existingUserConfig != null && existingUserConfig.config() != null) {
            Set<String> settingsToDeleteSet = settingsToDelete.stream().collect(Collectors.toSet());

            Map<String, String> newConfig = existingUserConfig.config()
                    .entrySet()
                    .stream()
                    .filter(entry -> !settingsToDeleteSet.contains(entry.getKey()))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

            userConfigTable.putItem(DDBUserConfigConverter.toUserDDBConfig(username, newConfig));
        }
    }
}
