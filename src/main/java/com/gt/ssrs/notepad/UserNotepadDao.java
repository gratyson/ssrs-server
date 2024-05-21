package com.gt.ssrs.notepad;

import com.gt.ssrs.userconfig.UserConfigDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class UserNotepadDao {

    private static final Logger log = LoggerFactory.getLogger(UserNotepadDao.class);

    private NamedParameterJdbcTemplate template;

    @Autowired
    public UserNotepadDao(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.template = namedParameterJdbcTemplate;
    }

    private static final String GET_NOTEPAD_TEXT_SQL =
            "SELECT notepad_text FROM user_notepad WHERE username = :username";

    private static final String SAVE_NOTEPAD_TEXT_SQL =
            "INSERT INTO user_notepad (username, notepad_text) " +
            "VALUES (:username, :notepadText) " +
            "ON CONFLICT (username) DO UPDATE " +
                    "SET notepad_text = :notepadText";

    public String getUserNotepadText(String username) {
        List<String> notepadTextList = template.queryForList(GET_NOTEPAD_TEXT_SQL, Map.of("username", username), String.class);

        if (notepadTextList.size() == 0) {
             return "";
        }

        return notepadTextList.get(0);
    }

    public int saveUserNotepadText(String username, String notepadText) {
        return template.update(SAVE_NOTEPAD_TEXT_SQL, Map.of("username", username, "notepadText", notepadText));
    }
}
