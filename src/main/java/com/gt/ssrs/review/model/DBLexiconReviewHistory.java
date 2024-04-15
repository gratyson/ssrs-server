package com.gt.ssrs.review.model;

import com.gt.ssrs.model.TestHistory;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;

public record DBLexiconReviewHistory(String lexiconId,
                                     String wordId,
                                     boolean learned,
                                     Instant mostRecentTestTime,
                                     Duration currentTestDelay,
                                     double currentBoost,
                                     Duration currentBoostExpirationDelay,
                                     Map<String, TestHistory> testHistory) {
    public DBLexiconReviewHistory {
        currentTestDelay = currentTestDelay == null ? Duration.ofSeconds(0) : currentTestDelay;
        currentBoostExpirationDelay = currentBoostExpirationDelay == null ? Duration.ofSeconds(0) : currentBoostExpirationDelay;
        testHistory = Collections.unmodifiableMap(testHistory);
    }


}
