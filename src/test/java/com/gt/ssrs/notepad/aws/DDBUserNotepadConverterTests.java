package com.gt.ssrs.notepad.aws;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(SpringExtension.class)
public class DDBUserNotepadConverterTests {

    private static final String TEST_USER = "testUser";
    private static final String TEST_TEXT = "Test\nText";

    @Test
    public void testFromNotepadText() {
        Instant startInstant = Instant.now();

        DDBUserNotepad userNotepad = DDBUserNotepadConverter.fromNotepadText(TEST_USER, TEST_TEXT);

        assertEquals(TEST_USER, userNotepad.username());
        assertEquals(TEST_TEXT, userNotepad.notepadText());
        assertTrue(!userNotepad.updateInstant().isBefore(startInstant));
        assertTrue(!userNotepad.updateInstant().isAfter(Instant.now()));
    }
}
