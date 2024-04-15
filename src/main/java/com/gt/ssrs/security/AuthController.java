package com.gt.ssrs.security;

import com.gt.ssrs.exception.InvalidUserDetailsException;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/rest/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);


    private final AuthenticationManager authenticationManager;
    private final LocalUserDetailsManager localUserDetailsManager;
    private final JwtService jwtService;
    private final String jwtCookieName;
    private final long cookieExpirySec;

    public AuthController(AuthenticationManager authenticationManager,
                          LocalUserDetailsManager localUserDetailsManager,
                          JwtService jwtService,
                          @Value("${server.jwt.cookieName}") String jwtCookieName,
                          @Value("${server.jwt.cookieExpirySec}") long cookieExpirySec) {
        this.authenticationManager = authenticationManager;
        this.localUserDetailsManager = localUserDetailsManager;
        this.jwtService = jwtService;
        this.jwtCookieName = jwtCookieName;
        this.cookieExpirySec = cookieExpirySec;
    }

    @GetMapping("/username")
    public String getLoggedInUsername(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails != null && userDetails.getUsername() != null) {
            return userDetails.getUsername();
        }

        return "";
    }

    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest loginRequest, HttpServletResponse httpServletResponse) {
        ResponseCookie responseCookie = AuthenticateAndBuildCookie(loginRequest.username, loginRequest.password);
        if (responseCookie == null) {
            return new LoginResponse(false, "Login unsuccessful");
        }

        httpServletResponse.addHeader(HttpHeaders.SET_COOKIE, responseCookie.toString());
        return new LoginResponse(true, "");
    }

    @PostMapping("/logout")
    public void logout(HttpServletResponse httpServletResponse) {
        ResponseCookie cookie = buildAuthCookie(null, 0);
        httpServletResponse.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    @PostMapping("/register")
    public RegisterResponse register(@RequestBody RegisterRequest registerRequest, HttpServletResponse httpServletResponse) {
        try {
            localUserDetailsManager.createUser(registerRequest.username, registerRequest.password, registerRequest.reenterPassword);
        } catch (InvalidUserDetailsException ex) {
            return new RegisterResponse(false, ex.getMessage());
        }

        ResponseCookie responseCookie = AuthenticateAndBuildCookie(registerRequest.username, registerRequest.password);
        if (responseCookie == null) {
            return new RegisterResponse(false, "Failed to save user");
        }

        httpServletResponse.addHeader(HttpHeaders.SET_COOKIE, responseCookie.toString());
        return new RegisterResponse(true, "");
    }

    private ResponseCookie AuthenticateAndBuildCookie(String username, String password) {
        Authentication authenticationRequest = UsernamePasswordAuthenticationToken.unauthenticated(username, password);
        Authentication authenticationResponse;

        try {
            authenticationResponse = this.authenticationManager.authenticate(authenticationRequest);
        } catch (BadCredentialsException ex) {
            return null;
        }

        if (authenticationResponse.isAuthenticated()) {
            String accessToken = jwtService.generateToken(username);
            ResponseCookie cookie = buildAuthCookie(accessToken, cookieExpirySec);

            return cookie;
        }
        return null;
    }

    private ResponseCookie buildAuthCookie(String token, long expirySec) {
        return ResponseCookie.from(jwtCookieName, token)
                .httpOnly(false)
                .secure(false)
                .path("/")
                .maxAge(expirySec)
                .build();
    }

    private record LoginRequest(String username, String password) { }
    private record LoginResponse(boolean success, String errMsg) { }

    private record RegisterRequest(String username, String password, String reenterPassword) { }

    private record RegisterResponse(boolean success, String errMsg) { }
}
