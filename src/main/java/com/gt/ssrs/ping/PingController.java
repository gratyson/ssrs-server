package com.gt.ssrs.ping;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/")
public class PingController {

    private static final String PING_RESPONSE = "pong";

    @GetMapping("/ping")
    public String ping() {
        return PING_RESPONSE;
    }
}