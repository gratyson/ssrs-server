package com.gt.ssrs.userconfig;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
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
    public Map<String, String> getUserConfig(@AuthenticationPrincipal UserDetails userDetails) {
        return userConfigService.getUserConfig(userDetails.getUsername());
    }

    @PostMapping("setConfig")
    public void setUserConfig(@RequestBody Map<String, String> userConfig,
                              @AuthenticationPrincipal UserDetails userDetails) {
        userConfigService.saveUserConfig(userDetails.getUsername(), userConfig);
    }
}
