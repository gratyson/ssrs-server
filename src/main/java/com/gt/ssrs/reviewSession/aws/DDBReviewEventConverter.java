package com.gt.ssrs.reviewSession.aws;

import com.gt.ssrs.language.TestRelationship;
import com.gt.ssrs.model.ReviewEvent;
import com.gt.ssrs.reviewSession.model.ReviewEventStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public class DDBReviewEventConverter {

    public static DDBReviewEvent convertReviewEvent(ReviewEvent reviewEvent) {
        return convertReviewEvent(reviewEvent, false, null);
    }

    public static DDBReviewEvent convertReviewEvent(ReviewEvent reviewEvent, boolean processed, Instant deleteAfterInstant) {
        return DDBReviewEvent.builder()
                .id(reviewEvent.eventId() == null || reviewEvent.eventId().isBlank() ? UUID.randomUUID().toString() : reviewEvent.eventId())
                .scheduledReviewId(reviewEvent.scheduledReviewId())
                .lexiconId(reviewEvent.lexiconId())
                .username(reviewEvent.username())
                .wordId(reviewEvent.wordId())
                .reviewType(reviewEvent.reviewType())
                .reviewMode(reviewEvent.reviewMode())
                .testRelationshipId(reviewEvent.testRelationship().getId())
                .correct(reviewEvent.isCorrect())
                .nearMiss(reviewEvent.isNearMiss())
                .elapsedTime(Duration.ofMillis(reviewEvent.elapsedTimeMs()))
                .status(processed ? ReviewEventStatus.Processed : ReviewEventStatus.Unprocessed)
                .eventInstant(reviewEvent.eventInstant())
                .override(reviewEvent.override())
                .deleteAfterInstant(deleteAfterInstant)
                .build();
    }

    public static ReviewEvent convertDDBReviewEvent(DDBReviewEvent ddbReviewEvent) {
        return new ReviewEvent(
                ddbReviewEvent.id(),
                ddbReviewEvent.scheduledReviewId(),
                ddbReviewEvent.lexiconId(),
                ddbReviewEvent.wordId(),
                ddbReviewEvent.username(),
                ddbReviewEvent.eventInstant(),
                ddbReviewEvent.reviewType(),
                ddbReviewEvent.reviewMode(),
                TestRelationship.getTestRelationshipById(ddbReviewEvent.testRelationshipId()),
                ddbReviewEvent.correct(),
                ddbReviewEvent.nearMiss(),
                ddbReviewEvent.elapsedTime() == null ? null : ddbReviewEvent.elapsedTime().toMillis(),
                ddbReviewEvent.override());
    }
}
