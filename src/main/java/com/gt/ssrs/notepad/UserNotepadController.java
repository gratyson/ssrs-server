package com.gt.ssrs.notepad;

import com.gt.ssrs.auth.AuthenticatedUser;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/rest/userNotepad")
public class UserNotepadController {
    
    private final UserNotepadService userNotepadService;

    public UserNotepadController(UserNotepadService userNotepadService) {
        this.userNotepadService = userNotepadService;
    }

    @GetMapping("getNotepadText")
    public String getUserNotepad(@AuthenticatedUser String username) {
        return userNotepadService.getUserNotepadText(username);
    }

    @PostMapping("setNotepadText")
    public void setUserNotepad(@RequestBody SaveUserNotepadTextRequest saveUserNotepadTextRequest,
                               @AuthenticatedUser String username) {
        userNotepadService.saveUserNotepadText(username, saveUserNotepadTextRequest.notepadText);
    }

    public record SaveUserNotepadTextRequest(String notepadText) { }
}
