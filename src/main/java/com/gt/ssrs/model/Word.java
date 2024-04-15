package com.gt.ssrs.model;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public record Word(String id, String owner, Map<String, String> elements, String attributes, List<String> audioFiles) {
    public static final Word EMPTY_WORD = new Word("", "", Map.of(), "", List.of());

    public Word {
        elements = Collections.unmodifiableMap(elements);
        audioFiles = Collections.unmodifiableList(audioFiles);
    }
}