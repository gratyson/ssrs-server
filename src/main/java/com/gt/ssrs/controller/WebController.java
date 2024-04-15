package com.gt.ssrs.controller;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.ModelAndView;

@Controller
public class WebController {

    private static final Logger log = LoggerFactory.getLogger(WebController.class);


    @GetMapping("/")
    public ModelAndView getApp(HttpServletRequest request) {

        // Do not delete - it appears that the call to getToken() is necessary for the response to include the CRSF token
        CsrfToken token = (CsrfToken)request.getAttribute("_csrf");
        log.debug("CRSF token: {}", token.getToken());

        return new ModelAndView("index.html");
    }
}
