package com.gt.ssrs.model;

public record TestRelationship(String id, String displayName, String testOn, String promptWith, String showAfterTest,
                               String fallbackId, boolean isReviewRelationship) {

    public static final TestRelationship EMPTY_TEST_RELATIONSHIP = new TestRelationship("empty", "empty",
            null, null, null, null, false);
}
