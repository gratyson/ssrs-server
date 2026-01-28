package com.gt.ssrs.language;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
public class LanguageTests {

    @Test
    public void testGetLanguageById() {
        for (Language language : Language.values()) {
            assertEquals(language, Language.getLanguageById(language.getId()));
        }
    }

    @Test
    public void testGetAllTestRelationships() {
        // Should equal all test relationships in the learning sequence
        assertEquals(Set.of(List.of(TestRelationship.MeaningToKana,
                                    TestRelationship.KanaToMeaning,
                                    TestRelationship.MeaningToKanji,
                                    TestRelationship.KanjiToMeaning,
                                    TestRelationship.KanjiToKana,
                                    TestRelationship.KanaToKanji)),
                     Set.of(Language.Japanese.getAllTestRelationships()));
    }
}
