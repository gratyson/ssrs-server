package com.gt.ssrs.language;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(SpringExtension.class)
public class WordElementTests {

    @Test
    public void testGetWordElementById() {
        for (WordElement wordElement : WordElement.values()) {
            assertEquals(wordElement, WordElement.getWordElementById(wordElement.getId()));
        }
    }
}
