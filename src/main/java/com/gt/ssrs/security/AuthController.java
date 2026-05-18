package com.gt.ssrs.security;

import com.gt.ssrs.auth.AuthenticatedUser;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/rest/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);



    private final AuthService authService;
    private final String tokenCookieName;
    private final long cookieExpirySec;
    private final boolean allowUserRegistration;

    @Autowired
    public AuthController(AuthService authService,
                          @Value("${server.jwt.tokenCookieName}") String tokenCookieName,
                          @Value("${server.jwt.cookieExpirySec}") long cookieExpirySec,
                          @Value("${ssrs.security.allowUserRegistration}") boolean allowUserRegistration) {

        this.authService = authService;
        this.tokenCookieName = tokenCookieName;
        this.cookieExpirySec = cookieExpirySec;
        this.allowUserRegistration = allowUserRegistration;
    }

    @GetMapping("/username")
    public String getLoggedInUsername(@AuthenticatedUser String userId, HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
        if (userId == null || userId.isBlank()) {
            return "";
        }

        AuthService.AuthCookieData authCookieData = getAuthCookieData(httpServletRequest);

        // Ideally refreshing tokens should be in a filter, however aws-serverless-java-container-springboot
        // doesn't support filters. This function is the next most logical location.
        refreshTokenIfNeeded(authCookieData, httpServletResponse);

        if (authCookieData.displayName() != null && !authCookieData.displayName().isBlank()) {
            return authCookieData.displayName();
        }

        // Fallback in case the username cookie is not set -- need to return a value so the client knows the user is logged in
        return userId;
    }

    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest loginRequest, HttpServletResponse httpServletResponse) {
        return loginAndSetCookies(loginRequest.username, loginRequest.password, httpServletResponse);
    }

    @PostMapping("/logout")
    public void logout(HttpServletResponse httpServletResponse) {
        httpServletResponse.addHeader(HttpHeaders.SET_COOKIE, buildCookie(tokenCookieName, "", 0).toString());
    }

    @GetMapping("/canRegister")
    public boolean canRegister() {
        return allowUserRegistration;
    }

    @PostMapping("/register")
    public RegisterResponse register(@RequestBody RegisterRequest registerRequest, HttpServletResponse httpServletResponse) {
        if (!allowUserRegistration) {
            return new RegisterResponse(false, "New users cannot be registered");
        }

        AuthService.AuthRequestResponse authRequestResponse = authService.registerUser(registerRequest.username, registerRequest.password, registerRequest.reenterPassword);

        if (!authRequestResponse.success()) {
            return new RegisterResponse(false, authRequestResponse.errorMsg());
        }

        LoginResponse loginResponse = loginAndSetCookies(registerRequest.username, registerRequest.password, httpServletResponse);
        return new RegisterResponse(loginResponse.success, loginResponse.errMsg);
    }

    @PostMapping("/changePassword")
    public ChangePasswordResponse changePassword(@AuthenticatedUser String userId, @RequestBody ChangePasswordRequest changePasswordRequest, HttpServletRequest httpServletRequest) {
        if (userId == null || userId.isBlank()) {
            return new ChangePasswordResponse(false, "User is not authenticated");
        }

        String displayName = getAuthCookieData(httpServletRequest).displayName();

        AuthService.AuthRequestResponse authRequestResponse = authService.changeUserPassword(
                displayName,
                changePasswordRequest.oldPassword,
                changePasswordRequest.newPassword,
                changePasswordRequest.reEnterNewPassword);

        return new ChangePasswordResponse(authRequestResponse.success(), authRequestResponse.errorMsg());
    }

    private LoginResponse loginAndSetCookies(String username, String password, HttpServletResponse httpServletResponse) {
        String authCookieData = authService.authenticateAndGetCookieData(username, password);

        if (authCookieData == null || authCookieData.isBlank()) {
            return new LoginResponse(false, "Login unsuccessful");
        }

        httpServletResponse.addHeader(HttpHeaders.SET_COOKIE, buildCookie(tokenCookieName, authCookieData, cookieExpirySec).toString());
        return new LoginResponse(true, "");
    }

    private ResponseCookie buildCookie(String cookieName, String cookieValue, long cookieExpirySec) {
        return ResponseCookie.from(cookieName, cookieValue)
                .httpOnly(false)
                .path("/")
                .maxAge(cookieExpirySec)
                .build();
    }

    private AuthService.AuthCookieData getAuthCookieData(HttpServletRequest httpServletRequest) {
        for (Cookie cookie : httpServletRequest.getCookies()) {
            if (cookie.getName().equals(tokenCookieName)) {
                return authService.parseAuthCookeData(cookie.getValue());
            }
        }

        return new AuthService.AuthCookieData("", "", null);
    }

    private void refreshTokenIfNeeded(AuthService.AuthCookieData authCookieData, HttpServletResponse httpServletResponse) {
        if (authCookieData.refreshAfter() != null && Instant.now().isAfter(authCookieData.refreshAfter())) {
            String newAuthCookieData = authService.refreshToken(authCookieData.displayName(), authCookieData.idToken());
            if (newAuthCookieData != null && newAuthCookieData != "") {
                httpServletResponse.addHeader(HttpHeaders.SET_COOKIE, buildCookie(tokenCookieName, newAuthCookieData, cookieExpirySec).toString());
            }
        }
    }

    public record LoginRequest(String username, String password) { }
    public record LoginResponse(boolean success, String errMsg) { }

    public record RegisterRequest(String username, String password, String reenterPassword) { }
    public record RegisterResponse(boolean success, String errMsg) { }

    public record ChangePasswordRequest(String oldPassword, String newPassword, String reEnterNewPassword) { }
    public record ChangePasswordResponse(boolean success, String errMsg) { }
}
