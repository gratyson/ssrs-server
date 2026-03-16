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

@RestController
@RequestMapping("/rest/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;
    private final String tokenCookieName;
    private final String displayNameCookieName;
    private final long cookieExpirySec;
    private final boolean allowUserRegistration;

    @Autowired
    public AuthController(AuthService authService,
                          @Value("${server.jwt.tokenCookieName}") String tokenCookieName,
                          @Value("${server.jwt.displayNameCookieName}") String displayNameCookieName,
                          @Value("${server.jwt.cookieExpirySec}") long cookieExpirySec,
                          @Value("${ssrs.security.allowUserRegistration}") boolean allowUserRegistration) {

        this.authService = authService;
        this.tokenCookieName = tokenCookieName;
        this.displayNameCookieName = displayNameCookieName;
        this.cookieExpirySec = cookieExpirySec;
        this.allowUserRegistration = allowUserRegistration;
    }

    @GetMapping("/username")
    public String getLoggedInUsername(@AuthenticatedUser String userId, HttpServletRequest httpServletRequest) {
        if (userId == null || userId.isBlank()) {
            return "";
        }

        String displayName = getDisplayNameFromCookie(httpServletRequest);
        if (displayName != null && !displayName.isBlank()) {
            return displayName;
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
        httpServletResponse.addHeader(HttpHeaders.SET_COOKIE, buildAuthTokenCookie(null, 0).toString());
        httpServletResponse.addHeader(HttpHeaders.SET_COOKIE, buildDisplayNameCookie(null, 0).toString());
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

        String displayName = getDisplayNameFromCookie(httpServletRequest);

        AuthService.AuthRequestResponse authRequestResponse = authService.changeUserPassword(
                displayName,
                changePasswordRequest.oldPassword,
                changePasswordRequest.newPassword,
                changePasswordRequest.reenterNewPassword);

        return new ChangePasswordResponse(authRequestResponse.success(), authRequestResponse.errorMsg());
    }

    private LoginResponse loginAndSetCookies(String username, String password, HttpServletResponse httpServletResponse) {
        String accessToken = authService.authenticateAndGetToken(username, password);

        if (accessToken == null || accessToken.isBlank()) {
            return new LoginResponse(false, "Login unsuccessful");
        }

        httpServletResponse.addHeader(HttpHeaders.SET_COOKIE, buildAuthTokenCookie(accessToken, cookieExpirySec).toString());
        httpServletResponse.addHeader(HttpHeaders.SET_COOKIE, buildDisplayNameCookie(username, cookieExpirySec).toString());
        return new LoginResponse(true, "");
    }

    private ResponseCookie buildAuthTokenCookie(String token, long cookieExpirySec) {
        return buildCookie(tokenCookieName, token, cookieExpirySec);
    }

    private ResponseCookie buildDisplayNameCookie(String displayName, long cookieExpirySec) {
        return buildCookie(displayNameCookieName, displayName, cookieExpirySec);
    }

    private ResponseCookie buildCookie(String cookieName, String cookieValue, long cookieExpirySec) {
        return ResponseCookie.from(cookieName, cookieValue)
                .httpOnly(false)
                .path("/")
                .maxAge(cookieExpirySec)
                .build();
    }

    private String getDisplayNameFromCookie(HttpServletRequest httpServletRequest) {
        for (Cookie cookie : httpServletRequest.getCookies()) {
            if (cookie.getName().equals(displayNameCookieName)) {
                return cookie.getValue();
            }
        }

        return "";
    }

    public record LoginRequest(String username, String password) { }
    public record LoginResponse(boolean success, String errMsg) { }

    public record RegisterRequest(String username, String password, String reenterPassword) { }
    public record RegisterResponse(boolean success, String errMsg) { }

    public record ChangePasswordRequest(String username, String oldPassword, String newPassword, String reenterNewPassword) { }
    public record ChangePasswordResponse(boolean success, String errMsg) { }
}
