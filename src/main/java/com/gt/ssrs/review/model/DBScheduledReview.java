package com.gt.ssrs.review.model;

import com.gt.ssrs.model.ReviewType;

import java.time.Duration;
import java.time.Instant;

public record DBScheduledReview(String id, String lexiconId, String wordId, ReviewType reviewType,
                                String testRelationshipId, Instant scheduledTestTime, Duration testDelay, boolean completed) {

}
