package com.gt.ssrs.notepad;

public interface UserNotepadDao {

    String getUserNotepadText(String username);

    int saveUserNotepadText(String username, String notepadText);
}
