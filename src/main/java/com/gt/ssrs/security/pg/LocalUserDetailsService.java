package com.gt.ssrs.security.pg;

import com.gt.ssrs.exception.InvalidUserDetailsException;
import com.gt.ssrs.security.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.UserDetailsManager;
import org.springframework.stereotype.Component;

@Component
public class LocalUserDetailsService extends AuthService {

    private static final Logger log = LoggerFactory.getLogger(LocalUserDetailsService.class);

    private final AuthenticationManager authenticationManager;
    private final UserDetailsManager userDetailsManager;
    private final JwtService jwtService;
    private final String passwordValidationMessage;

    @Autowired
    public LocalUserDetailsService(AuthenticationManager authenticationManager,
                                   UserDetailsManager userDetailsManager,
                                   JwtService jwtService,
                                   @Value("${ssrs.auth.validation.regex}") String passwordValidationRegex,
                                   @Value("${ssrs.auth.validation.message}") String passwordValidationMessage) {
        super(passwordValidationRegex);

        this.authenticationManager = authenticationManager;
        this.userDetailsManager = userDetailsManager;
        this.jwtService = jwtService;

        this.passwordValidationMessage = passwordValidationMessage;
    }

    @Override
    public String authenticateAndGetToken(String username, String password) {
        Authentication authenticationRequest = UsernamePasswordAuthenticationToken.unauthenticated(username, password);
        Authentication authenticationResponse;

        try {
            authenticationResponse = this.authenticationManager.authenticate(authenticationRequest);
        } catch (BadCredentialsException ex) {
            return "";
        }

        if (authenticationResponse.isAuthenticated()) {
            return jwtService.generateToken(username);
        }

        return "";
    }

    @Override
    public AuthRequestResponse changeUserPassword(String username, String currentPassword, String newPassword, String reenterNewPassword) {
        Authentication authenticationResponse = null;
        try {
            authenticationResponse = this.authenticationManager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(username, currentPassword));
        } catch (BadCredentialsException ex) { }

        if (authenticationResponse == null || !authenticationResponse.isAuthenticated()) {
            return new AuthRequestResponse(false, "Current password is not correct");
        }

        if (!validateNewPassword(newPassword, reenterNewPassword)) {
            return new AuthRequestResponse(false, passwordValidationMessage);
        }

        try {
            userDetailsManager.changePassword(currentPassword, newPassword);
        } catch (Exception ex) {
            log.warn("An error occurred processing a password change request.", ex);
            return new AuthRequestResponse(false, "An error occurred processing the password change request.");
        }

        return new AuthRequestResponse(true, "");
    }

    @Override
    public AuthRequestResponse registerUser(String username, String password, String reenterPassword) {
        try {
            saveNewUser(username, password, reenterPassword);
        } catch (InvalidUserDetailsException ex) {
            return new AuthRequestResponse(false, ex.getMessage());
        }

        return new AuthRequestResponse(true, "");
    }

    public void saveNewUser(String username, String password, String reenterPassword) throws InvalidUserDetailsException {
        validateNewUser(username, password, reenterPassword);

        LocalUser localUser = new LocalUser(null, username, password, null, null, null, true);
        userDetailsManager.createUser(localUser);
    }

    private void validateNewUser(String username, String password, String reenterPassword) throws InvalidUserDetailsException {
        if (password != null && !password.equals(reenterPassword)) {
            throw new InvalidUserDetailsException("Passwords do not match");
        }

        if (!validatePassword(password)) {
            throw new InvalidUserDetailsException(passwordValidationMessage);
        }

        try {
            UserDetails existingUser = userDetailsManager.loadUserByUsername(username);
            if (userDetailsManager.loadUserByUsername(username) != null) {
                throw new InvalidUserDetailsException("Username already exists");
            }
        } catch (UsernameNotFoundException ex) {
            // expected when creating new user
        }
    }
}
