package com.gt.ssrs.security.aws;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbImmutable;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

import java.time.Instant;

@DynamoDbImmutable(builder = DDBUserSessionData.Builder.class)
public class DDBUserSessionData {

    public static final String TABLE_NAME = "UserSessionData";

    private static final String USER_ID_ATTRIBUTE_NAME = "userId";
    private static final String ID_TOKEN_ATTRIBUTE_NAME = "idToken";
    private static final String ACCESS_TOKEN_ATTRIBUTE_NAME = "accessToken";
    private static final String REFRESH_TOKEN_ATTRIBUTE_NAME = "refreshToken";
    private static final String EXPIRATION_INSTANT_ATTRIBUTE_NAME = "expirationInstant";

    private final String userId;
    private final String idToken;
    private final String accessToken;
    private final String refreshToken;
    private final Instant expirationInstant;

    private DDBUserSessionData(Builder builder) {
        this.userId = builder.userId;
        this.idToken = builder.idToken;
        this.accessToken = builder.accessToken;
        this.refreshToken = builder.refreshToken;
        this.expirationInstant = builder.expirationInstant;
    }

    public static Builder builder() {
        return new Builder();
    }

    @DynamoDbAttribute(USER_ID_ATTRIBUTE_NAME)
    @DynamoDbPartitionKey()
    public String userId() {
        return userId;
    }

    @DynamoDbAttribute(ID_TOKEN_ATTRIBUTE_NAME)
    public String idToken() {
        return idToken;
    }

    @DynamoDbAttribute(ACCESS_TOKEN_ATTRIBUTE_NAME)
    public String accessToken() {
        return accessToken;
    }

    @DynamoDbAttribute(REFRESH_TOKEN_ATTRIBUTE_NAME)
    public String refreshToken() {
        return refreshToken;
    }

    @DynamoDbAttribute(EXPIRATION_INSTANT_ATTRIBUTE_NAME)
    public Instant expirationInstant() {
        return expirationInstant;
    }

    public static class Builder {
        private String userId;
        private String idToken;
        private String accessToken;
        private String refreshToken;
        private Instant expirationInstant;

        public Builder() { }

        public DDBUserSessionData build() {
            return new DDBUserSessionData(this);
        }

        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public Builder idToken(String idToken) {
            this.idToken = idToken;
            return this;
        }

        public Builder accessToken(String accessToken) {
            this.accessToken = accessToken;
            return this;
        }

        public Builder refreshToken(String refreshToken) {
            this.refreshToken = refreshToken;
            return this;
        }

        public Builder expirationInstant(Instant expirationInstant) {
            this.expirationInstant = expirationInstant;
            return this;
        }
    }
}
