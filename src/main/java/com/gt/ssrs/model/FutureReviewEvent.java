package com.gt.ssrs.model;

import java.time.Instant;

public record FutureReviewEvent(String lexiconId, String wordId, Instant reviewInstant, boolean inferred) { }
