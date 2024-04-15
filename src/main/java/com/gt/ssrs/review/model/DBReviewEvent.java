package com.gt.ssrs.review.model;

import com.gt.ssrs.model.ReviewEvent;
import com.gt.ssrs.model.ReviewMode;
import com.gt.ssrs.model.ReviewType;

import java.time.Instant;

public record DBReviewEvent(Integer eventId, String lexiconId, String wordId, String username, Instant eventInstant,
                            ReviewType reviewType, ReviewMode reviewMode, String testOn, String promptWith,
                            boolean isCorrect, boolean isNearMiss, long elapsedTimeMs, boolean override) {

    public static DBReviewEvent fromReviewEvent(ReviewEvent reviewEvent, String username, Instant eventInstant) {
        return fromReviewEvent(reviewEvent, null, username, eventInstant);
    }

    public static DBReviewEvent fromReviewEvent(ReviewEvent reviewEvent, Integer eventId, String username, Instant eventInstant) {
        return new DBReviewEvent(
                eventId,
                reviewEvent.lexiconId(),
                reviewEvent.wordId(),
                username,
                eventInstant,
                reviewEvent.reviewType(),
                reviewEvent.reviewMode(),
                reviewEvent.testOn(),
                reviewEvent.promptWith(),
                reviewEvent.isCorrect(),
                reviewEvent.isNearMiss(),
                reviewEvent.elapsedTimeMs(),
                reviewEvent.override());
    }
}
