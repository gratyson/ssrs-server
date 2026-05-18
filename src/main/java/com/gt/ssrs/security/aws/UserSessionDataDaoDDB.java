package com.gt.ssrs.security.aws;

import com.gt.ssrs.reviewHistory.aws.WordReviewHistoryDaoDDB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

@Component
public class UserSessionDataDaoDDB {

    private static final Logger log = LoggerFactory.getLogger(WordReviewHistoryDaoDDB.class);

    private final DynamoDbEnhancedClient dynamoDbEnhancedClient;
    private final DynamoDbTable<DDBUserSessionData> userSessionDataTable;

    @Autowired
    public UserSessionDataDaoDDB(DynamoDbEnhancedClient dynamoDbEnhancedClient) {
        this.dynamoDbEnhancedClient = dynamoDbEnhancedClient;
        this.userSessionDataTable = dynamoDbEnhancedClient.table(DDBUserSessionData.TABLE_NAME, TableSchema.fromImmutableClass(DDBUserSessionData.class));
    }

    public void saveUserSessionData(DDBUserSessionData userSessionData) {
        userSessionDataTable.putItem(userSessionData);
    }

    public DDBUserSessionData loadUserSessionData(String userId) {
        return userSessionDataTable.getItem(Key.builder().partitionValue(userId).build());
    }
}
