package com.gt.ssrs.language.model;

import java.util.List;

public record DBLanguage(long id, String displayName, String fontName, String audioFileRegex, double testsToDouble, List<String> validElementIds, List<String> requiredElementIds, List<String> coreElementIds, List<String> dedupeElementIds) { }

