package com.gt.ssrs.model;

import java.util.Collections;
import java.util.List;

public record LexiconReviewSummary(int totalWords, int learnedWords, List<FutureReviewEvent> futureReviewEvents) {

    public LexiconReviewSummary {
        futureReviewEvents = Collections.unmodifiableList(futureReviewEvents);
    }
}
