package com.gt.ssrs.notepad;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class UserNotepadService {

    private static final Logger log = LoggerFactory.getLogger(UserNotepadService.class);

    private static final int MAX_NOTEPAD_TEXT_LENGTH = 1048576; // 1 MB

    private final UserNotepadDao userNotepadDao;

    public UserNotepadService(UserNotepadDao userNotepadDao) {
        this.userNotepadDao = userNotepadDao;
    }

    public String getUserNotepadText(String username) {
        return userNotepadDao.getUserNotepadText(username);
    }

    public void saveUserNotepadText(String username, String text) {
        String textToSave = text.length() < MAX_NOTEPAD_TEXT_LENGTH ? text : text.substring(0, MAX_NOTEPAD_TEXT_LENGTH - 1);

        if (userNotepadDao.saveUserNotepadText(username, textToSave) == 0) {
            log.warn("Failed to save user notepad text for user " + username);
        }
    }
}
