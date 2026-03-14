package com.gt.ssrs.reviewSession.aws;

import com.gt.ssrs.model.ReviewType;
import com.gt.ssrs.reviewSession.model.ScheduledReviewStatus;
import software.amazon.awssdk.enhanced.dynamodb.mapper.Order;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

import java.time.Duration;
import java.time.Instant;

@DynamoDbImmutable(builder = DDBScheduledReview.Builder.class)
public class DDBScheduledReview {

    public static final String TABLE_NAME = "ScheduledReviews";

    public static final String SCHEDULED_REVIEW_BY_LEXICON_INDEX_NAME = "ScheduledReview-by-lexicon";

    public static final String ID_ATTRIBUTE_NAME = "id";
    public static final String LEXICON_ID_ATTRIBUTE_NAME = "lexiconId";
    public static final String USERNAME_ATTRIBUTE_NAME = "username";
    public static final String WORD_ID_ATTRIBUTE_NAME = "wordId";
    public static final String SCHEDULED_TEST_TIME_ATTRIBUTE_NAME = "scheduledTestTime";
    public static final String COMPLETED_ATTRIBUTE_NAME = "status";
    public static final String TEST_DELAY_ATTRIBUTE_NAME = "testDelay";
    public static final String TEST_RELATIONSHIP_ID_ATTRIBUTE_NAME = "testRelationshipId";
    public static final String REVIEW_TYPE_ATTRIBUTE_NAME = "reviewType";
    public static final String DELETE_AFTER_INSTANT_ATTRIBUTE_NAME = "deleteAfterInstant";

    private final String id;
    private final String lexiconId;
    private final String username;
    private final String wordId;
    private final Instant scheduledTestTime;
    private final ScheduledReviewStatus status;
    private final Duration testDelay;
    private final String testRelationshipId;
    private final ReviewType reviewType;
    private final Instant deleteAfterInstant;

    private DDBScheduledReview(Builder builder) {
        this.id = builder.id;
        this.lexiconId = builder.lexiconId;
        this.username = builder.username;
        this.wordId = builder.wordId;
        this.scheduledTestTime = builder.scheduledTestTime;
        this.status = builder.status;
        this.testDelay = builder.testDelay;
        this.testRelationshipId = builder.testRelationshipId;
        this.reviewType = builder.reviewType;
        this.deleteAfterInstant = builder.deleteAfterInstant;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(DDBScheduledReview ddbScheduledReview) {
        return new Builder(ddbScheduledReview);
    }

    @DynamoDbPartitionKey
    public String id() {
        return id;
    }

    @DynamoDbAttribute(LEXICON_ID_ATTRIBUTE_NAME)
    @DynamoDbSecondaryPartitionKey(indexNames = { SCHEDULED_REVIEW_BY_LEXICON_INDEX_NAME })
    public String lexiconId() {
        return lexiconId;
    }

    @DynamoDbAttribute(USERNAME_ATTRIBUTE_NAME)
    @DynamoDbSecondarySortKey(indexNames = { SCHEDULED_REVIEW_BY_LEXICON_INDEX_NAME }, order = Order.FIRST)
    public String username() {
        return username;
    }

    @DynamoDbAttribute(WORD_ID_ATTRIBUTE_NAME)
    public String wordId() {
        return wordId;
    }

    @DynamoDbAttribute(SCHEDULED_TEST_TIME_ATTRIBUTE_NAME)
    @DynamoDbSecondarySortKey(indexNames = {SCHEDULED_REVIEW_BY_LEXICON_INDEX_NAME}, order = Order.THIRD)
    public Instant scheduledTestTime() {
        return scheduledTestTime;
    }

    @DynamoDbAttribute(COMPLETED_ATTRIBUTE_NAME)
    @DynamoDbSecondarySortKey(indexNames = { SCHEDULED_REVIEW_BY_LEXICON_INDEX_NAME }, order = Order.SECOND)
    public ScheduledReviewStatus status() {
        return status;
    }

    @DynamoDbAttribute(TEST_DELAY_ATTRIBUTE_NAME)
    public Duration testDelay() {
        return testDelay;
    }

    @DynamoDbAttribute(TEST_RELATIONSHIP_ID_ATTRIBUTE_NAME)
    public String testRelationshipId() {
        return testRelationshipId;
    }

    @DynamoDbAttribute(REVIEW_TYPE_ATTRIBUTE_NAME)
    public ReviewType reviewType() {
        return reviewType;
    }

    @DynamoDbAttribute(DELETE_AFTER_INSTANT_ATTRIBUTE_NAME)
    public Instant deleteAfterInstant() {
        return deleteAfterInstant;
    }

    @Override
    public String toString() {
        return "DDBScheduledReview{" +
                "id='" + id + '\'' +
                ", lexiconId='" + lexiconId + '\'' +
                ", username='" + username + '\'' +
                ", wordId='" + wordId + '\'' +
                ", scheduledTestTime=" + scheduledTestTime +
                ", status=" + status +
                ", testDelay=" + testDelay +
                ", testRelationshipId='" + testRelationshipId + '\'' +
                ", reviewType=" + reviewType +
                ", deleteAfterInstant=" + deleteAfterInstant +
                '}';
    }

    public static final class Builder {
        private String id;
        private String lexiconId;
        private String username;
        private String wordId;
        private Instant scheduledTestTime;
        private ScheduledReviewStatus status;
        private Duration testDelay;
        private String testRelationshipId;
        private ReviewType reviewType;
        private Instant deleteAfterInstant;

        private Builder() { }

        private Builder(DDBScheduledReview ddbScheduledReview) {
            this.id = ddbScheduledReview.id;
            this.lexiconId = ddbScheduledReview.lexiconId;
            this.username = ddbScheduledReview.username;
            this.wordId = ddbScheduledReview.wordId;
            this.scheduledTestTime = ddbScheduledReview.scheduledTestTime;
            this.status = ddbScheduledReview.status;
            this.testDelay = ddbScheduledReview.testDelay;
            this.testRelationshipId = ddbScheduledReview.testRelationshipId;
            this.reviewType = ddbScheduledReview.reviewType;
            this.deleteAfterInstant = ddbScheduledReview.deleteAfterInstant();
        }

        public DDBScheduledReview build() {
            return new DDBScheduledReview(this);
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

        public Builder scheduledTestTime(Instant scheduledTestTime) {
            this.scheduledTestTime = scheduledTestTime;
            return this;
        }

        public Builder status(ScheduledReviewStatus status) {
            this.status = status;
            return this;
        }

        public Builder testDelay(Duration testDelay) {
            this.testDelay = testDelay;
            return this;
        }

        public Builder testRelationshipId(String testRelationshipId) {
            this.testRelationshipId = testRelationshipId;
            return this;
        }

        public Builder reviewType(ReviewType reviewType) {
            this.reviewType = reviewType;
            return this;
        }

        public Builder deleteAfterInstant(Instant deleteAfterInstant) {
            this.deleteAfterInstant = deleteAfterInstant;
            return this;
        }
    }
}
