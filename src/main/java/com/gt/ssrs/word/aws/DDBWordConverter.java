package com.gt.ssrs.word.aws;

import com.gt.ssrs.language.Language;
import com.gt.ssrs.language.WordElement;
import com.gt.ssrs.model.Word;
import com.gt.ssrs.util.HashUtil;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

public class DDBWordConverter {

    public static DDBWord convertWord(Language language, Word word) {
        return convertWord(language, word, Instant.now());
    }

    public static List<DDBWord> convertWordBatch(Language language, List<Word> words) {
        Instant updateInstant = Instant.now();
        List<DDBWord> wordDDBs = new ArrayList<>();

        for (Word word : words) {
            wordDDBs.add(convertWord(language, word, updateInstant));

            // Increment timestamp by one millisecond so ordering stays consistent
            updateInstant = updateInstant.plus(1, ChronoUnit.MILLIS);
        }

        return wordDDBs;
    }

    private static DDBWord convertWord(Language language, Word word, Instant updateInstant) {
        return DDBWord.builder()
                .id(word.id())
                .lexiconId(word.lexiconId())
                .owner(word.owner())
                .elements(word.elements() == null || word.elements().isEmpty() ? null : filterNullValues(word.elements()))
                .attributes(word.attributes())
                .audioFiles(word.audioFiles() == null && word.audioFiles().isEmpty() ? null : word.audioFiles())
                .createInstant(word.createInstant() == null ? updateInstant : word.createInstant())
                .updateInstant(updateInstant)
                .dedupeHash(computeDepupeHash(language, word))
                .build();
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

    public static int computeDepupeHash(Language language, Word word) {
        int hash = 0;

        for (WordElement dedupeElement : language.getDedupeElements()) {
            String elementValue = word.elements().get(dedupeElement.getId());
            if (elementValue != null && !elementValue.isBlank()) {
                // Combine element hashes with bitwise XOR so that hash value is consistent even if element ordering changes
                hash = hash ^ HashUtil.computeHash(elementValue);
            }
        }

        return hash;
    }
}
