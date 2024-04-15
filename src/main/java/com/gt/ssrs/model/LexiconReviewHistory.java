package com.gt.ssrs.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

public record LexiconReviewHistory(String lexiconId,
                                   String wordId,
                                   boolean learned,
                                   Instant mostRecentTestTime,
                                   String nextTestRelationId,
                                   Duration currentTestDelay,
                                   Instant nextTestTime,
                                   double currentBoost,
                                   Duration currentBoostExpirationDelay,
                                   Map<String, TestHistory> testHistory) { }