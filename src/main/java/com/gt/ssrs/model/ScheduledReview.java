package com.gt.ssrs.model;

import java.time.Duration;
import java.time.Instant;

public record ScheduledReview(String id,
                              String username,
                              String lexiconId,
                              String wordId,
                              ReviewType reviewType,
                              String testRelationshipId,
                              Instant scheduledTestTime,
                              Duration testDelay,
                              boolean completed) { }
