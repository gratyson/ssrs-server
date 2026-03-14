package com.gt.ssrs.userconfig;

import com.gt.ssrs.auth.AuthenticatedUser;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/rest/userConfig")
public class UserConfigController {

    private final UserConfigService userConfigService;

    public UserConfigController(UserConfigService userConfigService) {
        this.userConfigService = userConfigService;
    }

    @GetMapping("getConfig")
    public Map<String, String> getUserConfig(@AuthenticatedUser String username) {
        return userConfigService.getUserConfig(username);
    }

    @PostMapping("setConfig")
    public void setUserConfig(@RequestBody Map<String, String> userConfig,
                              @AuthenticatedUser String username) {
        userConfigService.saveUserConfig(username, userConfig);
    }
}
