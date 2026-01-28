package com.gt.ssrs.model;

import com.gt.ssrs.language.TestRelationship;

import java.util.List;

public record WordReview(long languageId,
                         Word word,
                         String scheduledEventId,
                         TestRelationship testRelationship,
                         ReviewMode reviewMode,
                         ReviewType reviewType,
                         boolean recordResult,
                         int allowedTimeSec,
                         List<String> typingTestButtons,
                         List<String> multipleChoiceButtons) {
}
