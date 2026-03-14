package com.gt.ssrs.word.aws;

import com.gt.ssrs.model.Word;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

public class DDBWordConverter {

    public static DDBWord convertWord(Word word) {
        return DDBWord.builder()
                .id(word.id())
                .lexiconId(word.lexiconId())
                .owner(word.owner())
                .elements(word.elements() == null || word.elements().isEmpty() ? null : filterNullValues(word.elements()))
                .attributes(word.attributes())
                .audioFiles(word.audioFiles() == null && word.audioFiles().isEmpty() ? null : word.audioFiles())
                .createInstant(word.createInstant() == null ? Instant.now() : word.createInstant())
                .updateInstant(Instant.now())
                .build();

    }

    public static List<DDBWord> convertWordBatch(List<Word> words) {
        Instant updateInstant = Instant.now();
        List<DDBWord> wordDDBs = new ArrayList<>();

        for (Word word : words) {
            wordDDBs.add(convertWord(word));

            // Increment timestamp by one millisecond so ordering stays consistent
            updateInstant = updateInstant.plus(1, ChronoUnit.MILLIS);
        }

        return wordDDBs;
    }

    public static Word convertDDBWord(DDBWord ddbWord) {
        return new Word(
                ddbWord.id(),
                ddbWord.lexiconId(),
                ddbWord.owner(),
                ddbWord.elements() == null ? Map.of() : filterNullValues(ddbWord.elements()),
                ddbWord.attributes(),
                ddbWord.audioFiles() == null ? List.of() : ddbWord.audioFiles(),
                ddbWord.createInstant(),
                ddbWord.updateInstant());
    }

    private static Map<String, String> filterNullValues(Map<String, String> elements) {
        return elements.entrySet().stream()
                .filter(entry -> StringUtils.hasText(entry.getValue()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
