package com.gt.ssrs.model;

public record ReviewEvent(String scheduledEventId,
                          String lexiconId,
                          String wordId,
                          ReviewType reviewType,
                          ReviewMode reviewMode,
                          String testOn,
                          String promptWith,
                          boolean isCorrect,
                          boolean isNearMiss,
                          long elapsedTimeMs,
                          boolean override) { }
