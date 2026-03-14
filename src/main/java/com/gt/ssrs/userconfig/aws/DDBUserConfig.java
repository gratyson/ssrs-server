package com.gt.ssrs.userconfig.aws;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbImmutable;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

import java.time.Instant;
import java.util.Map;

@DynamoDbImmutable(builder = DDBUserConfig.Builder.class)
public class DDBUserConfig {

    public static final String TABLE_NAME = "UserConfig";

    public static final String USERNAME_ATTRIBUTE_NAME = "username";
    public static final String CONFIG_ATTRIBUTE_NAME = "config";
    public static final String UPDATE_INSTANT_ATTRIBUTE_NAME = "updateInstant";

    public final String username;
    public final Map<String, String> config;
    public final Instant updateInstant;

    private DDBUserConfig(Builder builder) {
        this.username = builder.username;
        this.config = builder.config == null ? null : Map.copyOf(builder.config);
        this.updateInstant = builder.updateInstant;
    }

    public static Builder builder() {
        return new Builder();
    }

    @DynamoDbAttribute(USERNAME_ATTRIBUTE_NAME)
    @DynamoDbPartitionKey
    public String username() {
        return username;
    }

    @DynamoDbAttribute(CONFIG_ATTRIBUTE_NAME)
    public Map<String, String> config() {
        return config;
    }

    @DynamoDbAttribute(UPDATE_INSTANT_ATTRIBUTE_NAME)
    public Instant updateInstant() {
        return updateInstant;
    }

    public static class Builder {
        public String username;
        public Map<String, String> config;
        public Instant updateInstant;

        private Builder() { }

        public DDBUserConfig build() {
            return new DDBUserConfig(this);
        }

        public Builder username(String username) {
            this.username = username;
            return this;
        }

        public Builder config(Map<String, String> config) {
            this.config = config;
            return this;
        }

        public Builder updateInstant(Instant updateInstant) {
            this.updateInstant = updateInstant;
            return this;
        }
    }
}
