package com.gt.ssrs.model;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.gt.ssrs.serialization.ReviewTypeSerializer;

@JsonSerialize(using = ReviewTypeSerializer.class, as = Integer.class)
public enum ReviewType {
    Review(0),
    Learn(1),
    None(999);

    private int reviewTypeId;

    ReviewType(int reviewTypeId) {
        this.reviewTypeId = reviewTypeId;
    }

    public int getReviewTypeId() {
        return reviewTypeId;
    }
}
