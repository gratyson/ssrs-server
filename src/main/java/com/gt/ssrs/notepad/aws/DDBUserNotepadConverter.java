package com.gt.ssrs.notepad.aws;

import java.time.Instant;

public class DDBUserNotepadConverter {

    public static DDBUserNotepad fromNotepadText(String username, String notepadText) {
        return DDBUserNotepad.builder()
                .username(username)
                .notepadText(notepadText)
                .updateInstant(Instant.now())
                .build();
    }
}
