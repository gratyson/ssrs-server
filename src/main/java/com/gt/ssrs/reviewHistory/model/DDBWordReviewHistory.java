package com.gt.ssrs.reviewHistory.model;

import software.amazon.awssdk.enhanced.dynamodb.mapper.Order;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbImmutable;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@DynamoDbImmutable(builder = DDBWordReviewHistory.Builder.class)
public class DDBWordReviewHistory {

    public static final String TABLE_NAME = "word-review-history";
    public static final String BY_LEARNED_INDEX_NAME = "word-review-history-by-learned";

    private final String lexiconId;
    private final String username;
    private final String wordId;
    private final LearnedStatus learned;
    private final Instant mostRecentTestTime;
    private final String mostRecentTestRelationshipId;
    private final Duration currentTestDelay;
    private final double currentBoost;
    private final Duration currentBoostExpiration;
    private final Map<String, DDBTestHistory> wordTestHistory;
    private final Instant createInstant;
    private final Instant updateInstant;

    private DDBWordReviewHistory(Builder builder) {
        this.lexiconId = builder.lexiconId;
        this.username = builder.username;
        this.wordId = builder.wordId;
        this.learned = builder.learned;
        this.mostRecentTestTime = builder.mostRecentTestTime;
        this.mostRecentTestRelationshipId = builder.mostRecentTestRelationshipId;
        this.currentTestDelay = builder.currentTestDelay;
        this.currentBoost = builder.currentBoost;
        this.currentBoostExpiration = builder.currentBoostExpiration;
        this.wordTestHistory = builder.wordTestHistory == null ? null : Map.copyOf(builder.wordTestHistory);
        this.createInstant = createInstant();
        this.updateInstant = updateInstant();
    }

    public static Builder builder() {
        return new Builder();
    }

    @DynamoDbPartitionKey()
    @DynamoDbSecondaryPartitionKey(indexNames = { BY_LEARNED_INDEX_NAME }, order = Order.FIRST)
    public String lexiconId() {
        return lexiconId;
    }

    @DynamoDbPartitionKey()
    @DynamoDbSecondaryPartitionKey(indexNames = { BY_LEARNED_INDEX_NAME }, order = Order.SECOND)
    public String username() {
        return username;
    }

    @DynamoDbPartitionKey()
    public String wordId() {
        return wordId;
    }

    @DynamoDbAttribute("learned")
    @DynamoDbSecondaryPartitionKey(indexNames = { BY_LEARNED_INDEX_NAME }, order = Order.THIRD)
    public LearnedStatus learned() {
        return learned;
    }

    @DynamoDbAttribute("mostRecentTestTime")
    public Instant mostRecentTestTime() {
        return mostRecentTestTime;
    }

    @DynamoDbAttribute("mostRecentTestRelationshipId")
    public String mostRecentTestRelationshipId() {
        return mostRecentTestRelationshipId;
    }

    @DynamoDbAttribute("currentTestDelay")
    public Duration currentTestDelay() {
        return currentTestDelay;
    }

    @DynamoDbAttribute("currentBoost")
    public double currentBoost() {
        return currentBoost;
    }

    @DynamoDbAttribute("currentBoostExpiration")
    public Duration currentBoostExpiration() {
        return currentBoostExpiration;
    }

    @DynamoDbAttribute("wordTestHistory")
    public Map<String, DDBTestHistory> wordTestHistory() {
        return wordTestHistory;
    }

    @DynamoDbAttribute("createInstant")
    public Instant createInstant() {
        return createInstant;
    }

    @DynamoDbAttribute("updateInstant")
    public Instant updateInstant() {
        return updateInstant;
    }

    public static class Builder {
        private String lexiconId;
        private String username;
        private String wordId;
        private LearnedStatus learned;
        private Instant mostRecentTestTime;
        private String mostRecentTestRelationshipId;
        private Duration currentTestDelay;
        private double currentBoost;
        private Duration currentBoostExpiration;
        private Map<String, DDBTestHistory> wordTestHistory;
        private Instant createInstant;
        private Instant updateInstant;

        private Builder() { }

        public DDBWordReviewHistory build() {
            return new DDBWordReviewHistory(this);
        }

        public Builder lexiconId(String lexiconId) {
            this.lexiconId = lexiconId;
            return this;
        }

        public Builder username(String username) {
            this.username = username;
            return this;
        }

        public Builder wordId(String wordId) {
            this.wordId = wordId;
            return this;
        }

        public Builder learned(LearnedStatus learned) {
            this.learned = learned;
            return this;
        }

        public Builder mostRecentTestTime(Instant mostRecentTestTime) {
            this.mostRecentTestTime = mostRecentTestTime;
            return this;
        }

        public Builder mostRecentTestRelationshipId(String mostRecentTestRelationshipId) {
            this.mostRecentTestRelationshipId = mostRecentTestRelationshipId;
            return this;
        }

        public Builder currentTestDelay(Duration currentTestDelay) {
            this.currentTestDelay = currentTestDelay;
            return this;
        }

        public Builder currentBoost(double currentBoost) {
            this.currentBoost = currentBoost;
            return this;
        }

        public Builder currentBoostExpiration(Duration currentBoostExpiration) {
            this.currentBoostExpiration = currentBoostExpiration;
            return this;
        }

        public Builder wordTestHistory(Map<String, DDBTestHistory> wordTestHistory) {
            this.wordTestHistory = wordTestHistory;
            return this;
        }

        public Builder createInstant(Instant createInstant) {
            this.createInstant = createInstant;
            return this;
        }

        public Builder updateInstant(Instant updateInstant) {
            this.updateInstant = updateInstant;
            return this;
        }
    }
}
