package com.gt.ssrs.reviewSession.aws;

import com.gt.ssrs.model.ScheduledReview;
import com.gt.ssrs.reviewSession.model.ScheduledReviewStatus;

import java.util.UUID;

public class DDBScheduledReviewConverter {

    public static DDBScheduledReview convertScheduledReview(ScheduledReview scheduledReview) {
        return DDBScheduledReview.builder()
                .id(scheduledReview.id() == null || scheduledReview.id().isBlank() ? UUID.randomUUID().toString() : scheduledReview.id())
                .lexiconId(scheduledReview.lexiconId())
                .username(scheduledReview.username())
                .wordId(scheduledReview.wordId())
                .scheduledTestTime(scheduledReview.scheduledTestTime())
                .status(scheduledReview.completed() ? ScheduledReviewStatus.COMPLETED : ScheduledReviewStatus.SCHEDULED)
                .testDelay(scheduledReview.testDelay())
                .testRelationshipId(scheduledReview.testRelationshipId())
                .reviewType(scheduledReview.reviewType())
                .build();
    }

    public static ScheduledReview convertDDBScheduledReview(DDBScheduledReview ddbScheduledReview) {
        return new ScheduledReview(
                ddbScheduledReview.id(),
                ddbScheduledReview.username(),
                ddbScheduledReview.lexiconId(),
                ddbScheduledReview.wordId(),
                ddbScheduledReview.reviewType(),
                ddbScheduledReview.testRelationshipId(),
                ddbScheduledReview.scheduledTestTime(),
                ddbScheduledReview.testDelay(),
                ddbScheduledReview.status() == ScheduledReviewStatus.COMPLETED);
    }

}
