package com.gt.ssrs.reviewSession.aws;


import com.gt.ssrs.model.ReviewMode;
import com.gt.ssrs.model.ReviewType;
import com.gt.ssrs.reviewSession.model.ReviewEventStatus;
import software.amazon.awssdk.enhanced.dynamodb.mapper.Order;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

import java.time.Duration;
import java.time.Instant;

@DynamoDbImmutable(builder = DDBReviewEvent.Builder.class)
public class DDBReviewEvent {

    public static final String TABLE_NAME = "ReviewEvents";

    public static final String REVIEW_EVENT_BY_LEXICON_INDEX_NAME = "ReviewEvent-by-lexicon";

    public static final String ID_ATTRIBUTE_NAME = "id";
    public static final String SCHEDULED_REVIEW_ID_ATTRIBUTE_NAME = "scheduledReviewId";
    public static final String LEXICON_ID_ATTRIBUTE_NAME = "lexiconId";
    public static final String USERNAME_ATTRIBUTE_NAME = "username";
    public static final String WORD_ID_ATTRIBUTE_NAME = "wordId";
    public static final String REVIEW_TYPE_ATTRIBUTE_NAME = "reviewType";
    public static final String REVIEW_MODE_ATTRIBUTE_NAME = "reviewMode";
    public static final String TEST_RELATIONSHIP_ID_ATTRIBUTE_NAME = "testOn";
    public static final String CORRECT_ATTRIBUTE_NAME = "correct";
    public static final String NEAR_MISS_ATTRIBUTE_NAME = "nearMiss";
    public static final String ELAPSED_TIME_ATTRIBUTE_NAME = "elapsedTime";
    public static final String STATUS_ATTRIBUTE_NAME = "status";
    public static final String EVENT_INSTANT_ATTRIBUTE_NAME = "eventInstant";
    public static final String OVERRIDE_ATTRIBUTE_NAME = "override";
    public static final String DELETE_AFTER_INSTANT_ATTRIBUTE_NAME = "deleteAfterInstant";

    private final String id;
    private final String scheduledReviewId;
    private final String lexiconId;
    private final String username;
    private final String wordId;
    private final ReviewType reviewType;
    private final ReviewMode reviewMode;
    private final String testRelationshipId;
    private final boolean correct;
    private final boolean nearMiss;
    private final Duration elapsedTime;
    private final ReviewEventStatus status;
    private final Instant eventInstant;
    private final boolean override;
    private final Instant deleteAfterInstant;

    private DDBReviewEvent(Builder builder) {
        this.id = builder.id;
        this.scheduledReviewId = builder.scheduledReviewId;
        this.lexiconId = builder.lexiconId;
        this.username = builder.username;
        this.wordId = builder.wordId;
        this.reviewType = builder.reviewType;
        this.reviewMode = builder.reviewMode;
        this.testRelationshipId = builder.testRelationshipId;
        this.correct = builder.correct;
        this.nearMiss = builder.nearMiss;
        this.elapsedTime = builder.elapsedTime;
        this.status = builder.status;
        this.eventInstant = builder.eventInstant;
        this.override = builder.override;
        this.deleteAfterInstant = builder.deleteAfterInstant;
    }

    public static Builder builder() {
        return new Builder();
    }

    @DynamoDbPartitionKey
    public String id() {
        return id;
    }

    @DynamoDbAttribute(SCHEDULED_REVIEW_ID_ATTRIBUTE_NAME)
    public String scheduledReviewId() {
        return scheduledReviewId;
    }

    @DynamoDbAttribute(LEXICON_ID_ATTRIBUTE_NAME)
    @DynamoDbSecondaryPartitionKey(indexNames = { REVIEW_EVENT_BY_LEXICON_INDEX_NAME })
    public String lexiconId() {
        return lexiconId;
    }

    @DynamoDbAttribute(USERNAME_ATTRIBUTE_NAME)
    @DynamoDbSecondarySortKey(indexNames = { REVIEW_EVENT_BY_LEXICON_INDEX_NAME }, order = Order.FIRST)
    public String username() {
        return username;
    }

    @DynamoDbAttribute(WORD_ID_ATTRIBUTE_NAME)
    public String wordId() {
        return wordId;
    }

    @DynamoDbAttribute(REVIEW_TYPE_ATTRIBUTE_NAME)
    public ReviewType reviewType() {
        return reviewType;
    }

    @DynamoDbAttribute(REVIEW_MODE_ATTRIBUTE_NAME)
    public ReviewMode reviewMode() {
        return reviewMode;
    }

    @DynamoDbAttribute(TEST_RELATIONSHIP_ID_ATTRIBUTE_NAME)
    public String testRelationshipId() {
        return testRelationshipId;
    }

    @DynamoDbAttribute(CORRECT_ATTRIBUTE_NAME)
    public boolean correct() {
        return correct;
    }

    @DynamoDbAttribute(NEAR_MISS_ATTRIBUTE_NAME)
    public boolean nearMiss() {
        return nearMiss;
    }

    @DynamoDbAttribute(ELAPSED_TIME_ATTRIBUTE_NAME)
    public Duration elapsedTime() {
        return elapsedTime;
    }

    @DynamoDbAttribute(STATUS_ATTRIBUTE_NAME)
    @DynamoDbSecondarySortKey(indexNames = { REVIEW_EVENT_BY_LEXICON_INDEX_NAME }, order = Order.SECOND)
    public ReviewEventStatus status() {
        return status;
    }

    @DynamoDbAttribute(EVENT_INSTANT_ATTRIBUTE_NAME)
    @DynamoDbSecondarySortKey(indexNames = { REVIEW_EVENT_BY_LEXICON_INDEX_NAME }, order = Order.THIRD)
    public Instant eventInstant() {
        return eventInstant;
    }

    @DynamoDbAttribute(OVERRIDE_ATTRIBUTE_NAME)
    public boolean override() {
        return override;
    }

    @DynamoDbAttribute(DELETE_AFTER_INSTANT_ATTRIBUTE_NAME)
    public Instant deleteAfterInstant() {
        return deleteAfterInstant;
    }

    @Override
    public String toString() {
        return "DDBReviewEvent{" +
                "id='" + id + '\'' +
                ", scheduledReviewId='" + scheduledReviewId + '\'' +
                ", lexiconId='" + lexiconId + '\'' +
                ", username='" + username + '\'' +
                ", wordId='" + wordId + '\'' +
                ", reviewType=" + reviewType +
                ", reviewMode=" + reviewMode +
                ", testRelationshipId='" + testRelationshipId + '\'' +
                ", correct=" + correct +
                ", nearMiss=" + nearMiss +
                ", elapsedTime=" + elapsedTime +
                ", status=" + status +
                ", eventInstant=" + eventInstant +
                ", override=" + override +
                ", deleteAfterInstant=" + deleteAfterInstant +
                '}';
    }

    public static class Builder {
        private String id;
        private String scheduledReviewId;
        private String lexiconId;
        private String username;
        private String wordId;
        private ReviewType reviewType;
        private ReviewMode reviewMode;
        private String testRelationshipId;
        private boolean correct;
        private boolean nearMiss;
        private Duration elapsedTime;
        private ReviewEventStatus status;
        private Instant eventInstant;
        private boolean override;
        private Instant deleteAfterInstant;

        private Builder() { }

        public DDBReviewEvent build() {
            return new DDBReviewEvent(this);
        }

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder scheduledReviewId(String scheduledReviewId) {
            this.scheduledReviewId = scheduledReviewId;
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

        public Builder reviewType(ReviewType reviewType) {
            this.reviewType = reviewType;
            return this;
        }

        public Builder reviewMode(ReviewMode reviewMode) {
            this.reviewMode = reviewMode;
            return this;
        }

        public Builder testRelationshipId(String testRelationshipId) {
            this.testRelationshipId = testRelationshipId;
            return this;
        }

        public Builder correct(boolean correct) {
            this.correct = correct;
            return this;
        }

        public Builder nearMiss(boolean nearMiss) {
            this.nearMiss = nearMiss;
            return this;
        }

        public Builder elapsedTime(Duration elapsedTime) {
            this.elapsedTime = elapsedTime;
            return this;
        }

        public Builder status(ReviewEventStatus status) {
            this.status = status;
            return this;
        }

        public Builder eventInstant(Instant eventInstant) {
            this.eventInstant = eventInstant;
            return this;
        }

        public Builder override(boolean override) {
            this.override = override;
            return this;
        }

        public Builder deleteAfterInstant(Instant deleteAfterInstant) {
            this.deleteAfterInstant = deleteAfterInstant;
            return this;
        }
    }
}