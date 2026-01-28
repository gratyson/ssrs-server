package com.gt.ssrs.language;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
public class WordElementTests {

    @Test
    public void testGetWordElementById() {
        for (WordElement wordElement : WordElement.values()) {
            assertEquals(wordElement, WordElement.getWordElementById(wordElement.getId()));
        }
    }
}
