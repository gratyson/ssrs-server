package com.gt.ssrs.model;

import com.gt.ssrs.language.TestRelationship;
import com.gt.ssrs.reviewSession.model.ClientReviewEvent;

import java.time.Instant;

public record ReviewEvent(String eventId, String scheduledReviewId, String lexiconId, String wordId, String username,
                          Instant eventInstant, ReviewType reviewType, ReviewMode reviewMode, TestRelationship testRelationship,
                          boolean isCorrect, boolean isNearMiss, long elapsedTimeMs, boolean override) {

    public static ReviewEvent fromClientReviewEvent(ClientReviewEvent reviewEvent, String username, Instant eventInstant) {
        return fromClientReviewEvent(reviewEvent, null, username, eventInstant);
    }

    public static ReviewEvent fromClientReviewEvent(ClientReviewEvent reviewEvent, String eventId, String username, Instant eventInstant) {
        TestRelationship testRelationship = TestRelationship.getTestRelationshipById(reviewEvent.testRelationshipId());

        return new ReviewEvent(
                eventId,
                reviewEvent.scheduledEventId(),
                reviewEvent.lexiconId(),
                reviewEvent.wordId(),
                username,
                eventInstant,
                reviewEvent.reviewType(),
                reviewEvent.reviewMode(),
                testRelationship,
                reviewEvent.isCorrect(),
                reviewEvent.isNearMiss(),
                reviewEvent.elapsedTimeMs(),
                reviewEvent.override());
    }
}
