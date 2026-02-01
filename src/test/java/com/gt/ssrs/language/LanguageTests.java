package com.gt.ssrs.language;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(SpringExtension.class)
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
