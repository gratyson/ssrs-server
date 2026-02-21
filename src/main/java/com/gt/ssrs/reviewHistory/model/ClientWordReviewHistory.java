package com.gt.ssrs.reviewHistory.model;

import com.gt.ssrs.model.TestHistory;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

public record ClientWordReviewHistory(String lexiconId,
                                      String wordId,
                                      boolean learned,
                                      Instant mostRecentTestTime,
                                      String mostRecentTestRelationshipId,
                                      Duration currentTestDelay,
                                      double currentBoost,
                                      Duration currentBoostExpirationDelay,
                                      Map<String, TestHistory> testHistory) { }


