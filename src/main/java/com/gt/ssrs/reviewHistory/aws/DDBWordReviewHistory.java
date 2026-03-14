package com.gt.ssrs.reviewHistory.aws;

import com.gt.ssrs.reviewHistory.model.LearnedStatus;
import software.amazon.awssdk.enhanced.dynamodb.mapper.Order;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@DynamoDbImmutable(builder = DDBWordReviewHistory.Builder.class)
public class DDBWordReviewHistory {

    public static final String TABLE_NAME = "WordReviewHistory";
    public static final String BY_LEARNED_INDEX_NAME = "WordReviewHistory-by-learned";

    public static final String ID_ATTRIBUTE_NAME = "id";
    public static final String LEXICON_ID_ATTRIBUTE_NAME = "lexiconId";
    public static final String USERNAME_ATTRIBUTE_NAME = "username";
    public static final String WORD_ID_ATTRIBUTE_NAME = "wordId";
    public static final String LEARNED_ATTRIBUTE_NAME = "learned";
    public static final String MOST_RECENT_TEST_TIME_ATTRIBUTE_NAME = "mostRecentTestTime";
    public static final String MOST_RECENT_TEST_RELATIONSHIP_ID_ATTRIBUTE_NAME = "mostRecentTestRelationshipId";
    public static final String CURRENT_TEST_DELAY_ATTRIBUTE_NAME = "currentTestDelay";
    public static final String CURRENT_BOOST_ATTRIBUTE_NAME = "currentBoost";
    public static final String CURRENT_BOOST_EXPIRATION_ATTRIBUTE_NAME = "currentBoostExpiration";
    public static final String WORD_TEST_HISTORY_ATTRIBUTE_NAME = "wordTestHistory";
    public static final String CREATE_INSTANT_ATTRIBUTE_NAME = "createInstant";
    public static final String UPDATE_INSTANT_ATTRIBUTE_NAME = "updateInstant";

    private final String id;
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
        this.id = builder.id;
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
        this.createInstant = builder.createInstant;
        this.updateInstant = builder.updateInstant;
    }

    public static Builder builder() {
        return new Builder();
    }

    @DynamoDbAttribute(ID_ATTRIBUTE_NAME)
    @DynamoDbPartitionKey()
    public String id() {
        return id;
    }

    @DynamoDbAttribute(LEXICON_ID_ATTRIBUTE_NAME)
    @DynamoDbSecondaryPartitionKey(indexNames = { BY_LEARNED_INDEX_NAME }, order = Order.FIRST)
    public String lexiconId() {
        return lexiconId;
    }

    @DynamoDbAttribute(USERNAME_ATTRIBUTE_NAME)
    @DynamoDbSecondarySortKey(indexNames = { BY_LEARNED_INDEX_NAME }, order = Order.FIRST)
    public String username() {
        return username;
    }

    @DynamoDbAttribute(WORD_ID_ATTRIBUTE_NAME)
    @DynamoDbSortKey()
    public String wordId() {
        return wordId;
    }

    @DynamoDbAttribute(LEARNED_ATTRIBUTE_NAME)
    @DynamoDbSecondarySortKey(indexNames = { BY_LEARNED_INDEX_NAME }, order = Order.SECOND)
    public LearnedStatus learned() {
        return learned;
    }

    @DynamoDbAttribute(MOST_RECENT_TEST_TIME_ATTRIBUTE_NAME)
    public Instant mostRecentTestTime() {
        return mostRecentTestTime;
    }

    @DynamoDbAttribute(MOST_RECENT_TEST_RELATIONSHIP_ID_ATTRIBUTE_NAME)
    public String mostRecentTestRelationshipId() {
        return mostRecentTestRelationshipId;
    }

    @DynamoDbAttribute(CURRENT_TEST_DELAY_ATTRIBUTE_NAME)
    public Duration currentTestDelay() {
        return currentTestDelay;
    }

    @DynamoDbAttribute(CURRENT_BOOST_ATTRIBUTE_NAME)
    public double currentBoost() {
        return currentBoost;
    }

    @DynamoDbAttribute(CURRENT_BOOST_EXPIRATION_ATTRIBUTE_NAME)
    public Duration currentBoostExpiration() {
        return currentBoostExpiration;
    }

    @DynamoDbAttribute(WORD_TEST_HISTORY_ATTRIBUTE_NAME)
    public Map<String, DDBTestHistory> wordTestHistory() {
        return wordTestHistory;
    }

    @DynamoDbSecondarySortKey(indexNames = { BY_LEARNED_INDEX_NAME }, order = Order.THIRD)
    @DynamoDbAttribute(CREATE_INSTANT_ATTRIBUTE_NAME)
    public Instant createInstant() {
        return createInstant;
    }

    @DynamoDbAttribute(UPDATE_INSTANT_ATTRIBUTE_NAME)
    public Instant updateInstant() {
        return updateInstant;
    }

    @Override
    public String toString() {
        return "DDBWordReviewHistory{" +
                "id='" + id + '\'' +
                ", lexiconId='" + lexiconId + '\'' +
                ", username='" + username + '\'' +
                ", wordId='" + wordId + '\'' +
                ", learned=" + learned +
                ", mostRecentTestTime=" + mostRecentTestTime +
                ", mostRecentTestRelationshipId='" + mostRecentTestRelationshipId + '\'' +
                ", currentTestDelay=" + currentTestDelay +
                ", currentBoost=" + currentBoost +
                ", currentBoostExpiration=" + currentBoostExpiration +
                ", wordTestHistory=" + wordTestHistory +
                ", createInstant=" + createInstant +
                ", updateInstant=" + updateInstant +
                '}';
    }

    public static class Builder {
        private String id;
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

        public Builder id(String id) {
            this.id = id;
            return this;
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
