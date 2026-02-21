package com.gt.ssrs.reviewSession.model;

import com.gt.ssrs.model.ReviewMode;
import com.gt.ssrs.model.ReviewType;

public record ClientReviewEvent(String scheduledEventId,
                                String lexiconId,
                                String wordId,
                                ReviewType reviewType,
                                ReviewMode reviewMode,
                                String testRelationshipId,
                                boolean isCorrect,
                                boolean isNearMiss,
                                long elapsedTimeMs,
                                boolean override) { }
