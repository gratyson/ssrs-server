package com.gt.ssrs.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

public record WordReviewHistory(String lexiconId,
                                String username,
                                String wordId,
                                boolean learned,
                                Instant mostRecentTestTime,
                                String mostRecentTestRelationshipId,
                                Duration currentTestDelay,
                                double currentBoost,
                                Duration currentBoostExpirationDelay,
                                Map<String, TestHistory> testHistory) { }