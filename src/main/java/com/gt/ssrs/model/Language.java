package com.gt.ssrs.model;

import java.util.Collections;
import java.util.List;

public record Language(long id, String displayName, String fontName, String audioFileRegex, double testsToDouble, List<WordElement> validElements, List<WordElement> requiredElements, List<WordElement> coreElements, List<WordElement> dedupeElements, List<TestRelationship> testRelationships) {
    public Language {
        validElements = Collections.unmodifiableList(validElements);
        requiredElements = Collections.unmodifiableList(requiredElements);
        coreElements = Collections.unmodifiableList(coreElements);
        dedupeElements = Collections.unmodifiableList(dedupeElements);
        testRelationships = Collections.unmodifiableList(testRelationships);
    }

}
