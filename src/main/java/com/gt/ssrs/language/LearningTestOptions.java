package com.gt.ssrs.language;

import com.gt.ssrs.model.ReviewMode;

public record LearningTestOptions(ReviewMode reviewMode, int optionCount, boolean recordEvent, TestRelationship relationship) { }
