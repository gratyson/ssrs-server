package com.gt.ssrs.model;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.gt.ssrs.serialization.ReviewModeSerializer;

@JsonSerialize(using = ReviewModeSerializer.class, as = Integer.class)
public enum ReviewMode {
    WordOverview(0),
    TypingTest(1),
    MultipleChoiceTest(2),
    WordOverviewWithTyping(3),
    WordOverviewReminder(4);

    private int reviewModeId;

    ReviewMode(int reviewModeId) {
        this.reviewModeId = reviewModeId;
    }

    public int getReviewModeId() {
        return reviewModeId;
    }
}
