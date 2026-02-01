package com.gt.ssrs.model;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public record Word(String id, String lexiconId, String owner, Map<String, String> elements, String attributes, List<String> audioFiles, Instant createInstant, Instant updateInstant) {
    public static final Word EMPTY_WORD = new Word("", "", "", Map.of(), "", List.of(), Instant.EPOCH, Instant.EPOCH);

    public Word {
        elements = Collections.unmodifiableMap(elements);
        audioFiles = Collections.unmodifiableList(audioFiles);
    }
}