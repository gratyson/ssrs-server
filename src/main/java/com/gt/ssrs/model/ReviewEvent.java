package com.gt.ssrs.model;

import com.gt.ssrs.language.TestRelationship;

public record ReviewEvent(String scheduledEventId,
                          String lexiconId,
                          String wordId,
                          ReviewType reviewType,
                          ReviewMode reviewMode,
                          String testRelationshipId,
                          boolean isCorrect,
                          boolean isNearMiss,
                          long elapsedTimeMs,
                          boolean override) { }
