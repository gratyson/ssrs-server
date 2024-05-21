package com.gt.ssrs.notepad;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/rest/userNotepad")
public class UserNotepadController {
    
    private final UserNotepadService userNotepadService;

    public UserNotepadController(UserNotepadService userNotepadService) {
        this.userNotepadService = userNotepadService;
    }

    @GetMapping("getNotepadText")
    public String getUserNotepad(@AuthenticationPrincipal UserDetails userDetails) {
        return userNotepadService.getUserNotepadText(userDetails.getUsername());
    }

    @PostMapping("setNotepadText")
    public void setUserNotepad(@RequestBody SaveUserNotepadTextRequest saveUserNotepadTextRequest,
                               @AuthenticationPrincipal UserDetails userDetails) {
        userNotepadService.saveUserNotepadText(userDetails.getUsername(), saveUserNotepadTextRequest.notepadText);
    }

    private record SaveUserNotepadTextRequest(String notepadText) { }
}
