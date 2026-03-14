package com.gt.ssrs.notepad.aws;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbImmutable;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

import java.time.Instant;

@DynamoDbImmutable(builder = DDBUserNotepad.Builder.class)
public class DDBUserNotepad {

    public static final String TABLE_NAME = "UserNotepad";

    public static final String USERNAME_ATTRIBUTE_NAME = "username";
    public static final String NOTEPAD_TEXT_ATTRIBUTE_NAME = "notepadText";
    public static final String UPDATE_INSTANT_ATTRIBUTE_NAME = "updateInstant";

    private final String username;
    private final String notepadText;
    private final Instant updateInstant;

    public static Builder builder() {
        return new Builder();
    }

    private DDBUserNotepad(Builder builder) {
        this.username = builder.username;
        this.notepadText = builder.notepadText;
        this.updateInstant = builder.updateInstant;
    }

    @DynamoDbAttribute(USERNAME_ATTRIBUTE_NAME)
    @DynamoDbPartitionKey
    public String username() {
        return username;
    }

    @DynamoDbAttribute(NOTEPAD_TEXT_ATTRIBUTE_NAME)
    public String notepadText() {
        return notepadText;
    }

    @DynamoDbAttribute(UPDATE_INSTANT_ATTRIBUTE_NAME)
    public Instant updateInstant() {
        return updateInstant;
    }

    public static class Builder {

        private String username;
        private String notepadText;
        private Instant updateInstant;

        private Builder() { }

        public DDBUserNotepad build() {
            return new DDBUserNotepad(this);
        }

        public Builder username(String username) {
            this.username = username;
            return this;
        }

        public Builder notepadText(String notepadText) {
            this.notepadText = notepadText;
            return this;
        }

        public Builder updateInstant(Instant updateInstant) {
            this.updateInstant = updateInstant;
            return this;
        }
    }
}
