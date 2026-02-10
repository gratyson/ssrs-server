package com.gt.ssrs.reviewHistory.model;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbImmutable;

@DynamoDbImmutable(builder = DDBTestHistory.Builder.class)
public class DDBTestHistory {

    private final int totalTests;
    private final int correctTests;
    private final int correctStreak;

    private DDBTestHistory(Builder builder) {
        this.totalTests = builder.totalTests;
        this.correctTests = builder.correctTests;
        this.correctStreak = builder.correctStreak;
    }

    public static Builder builder() {
        return new Builder();
    }

    @DynamoDbAttribute("totalTests")
    public int totalTests() {
        return totalTests;
    }

    @DynamoDbAttribute("correctTests")
    public int correctTests() {
        return correctTests;
    }

    @DynamoDbAttribute("correctStreak")
    public int correctStreak() {
        return correctStreak;
    }

    public static class Builder {

        private int totalTests;
        private int correctTests;
        private int correctStreak;

        private Builder() { }

        public Builder totalTests(int totalTests) {
            this.totalTests = totalTests;
            return this;
        }

        public Builder correctTests(int correctTests) {
            this.correctTests = correctTests;
            return this;
        }

        public Builder correctStreak(int correctStreak) {
            this.correctStreak = correctStreak;
            return this;
        }

        public DDBTestHistory build() {
            return new DDBTestHistory(this);
        }
    }
}