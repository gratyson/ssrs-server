package com.gt.ssrs.notepad.aws;

import com.gt.ssrs.notepad.UserNotepadDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

@Component
public class UserNotepadDaoDDB implements UserNotepadDao {

    private static final Logger log = LoggerFactory.getLogger(UserNotepadDaoDDB.class);

    private final DynamoDbEnhancedClient dynamoDbEnhancedClient;

    private final DynamoDbTable<DDBUserNotepad> userNotepadTable;

    @Autowired
    public UserNotepadDaoDDB(DynamoDbEnhancedClient dynamoDbEnhancedClient) {
        this.dynamoDbEnhancedClient = dynamoDbEnhancedClient;

        this.userNotepadTable = dynamoDbEnhancedClient.table(DDBUserNotepad.TABLE_NAME, TableSchema.fromImmutableClass(DDBUserNotepad.class));
    }

    @Override
    public String getUserNotepadText(String username) {
        DDBUserNotepad notepad = userNotepadTable.getItem(Key.builder().partitionValue(username).build());

        if (notepad == null) {
             return "";
        }

        return notepad.notepadText();
    }

    @Override
    public int saveUserNotepadText(String username, String notepadText) {
        userNotepadTable.putItem(DDBUserNotepadConverter.fromNotepadText(username, notepadText));

        return 1;
    }
}
