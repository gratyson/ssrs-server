package com.gt.ssrs.model;

import java.util.Collections;
import java.util.Map;

public record WordFilterOptions(Map<String, String> elements, String attributes, Boolean learned, Boolean hasAudio) {

    public static final WordFilterOptions EMPTY_WORD_FILTERS = new WordFilterOptions(Map.of(), "", null, null);

    public WordFilterOptions {
        elements = Collections.unmodifiableMap(elements);
    }
}