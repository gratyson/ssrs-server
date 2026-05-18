package com.gt.ssrs.security;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

public abstract class AuthService {

    private static final String COOKIE_DELIMITER = "|";

    private final String passwordValidationRegex;

    private final Pattern validationPattern;

    protected AuthService(String passwordValidationRegex) {
        this.passwordValidationRegex = passwordValidationRegex;

        this.validationPattern = Pattern.compile(this.passwordValidationRegex);
    }

    public abstract String authenticateAndGetCookieData(String username, String password);

    public abstract String refreshToken(String username, String idToken);

    public abstract AuthRequestResponse changeUserPassword(String username, String currentPassword, String newPassword, String reenterNewPassword);

    public abstract AuthRequestResponse registerUser(String username, String password, String reenterPassword);

    protected boolean validateNewPassword(String password, String reenterPassword) {
        return password != null
                && password.equals(reenterPassword)
                && validatePassword(password);
    }

    protected boolean validatePassword(String password) {
        if (password == null) {
            return false;
        }

        return validationPattern.matcher(password).find();
    }

    protected String buildAuthCookieValue(String idToken, String displayName) {
        return buildAuthCookieValue(idToken, displayName, null);
    }

    protected String buildAuthCookieValue(String idToken, String displayName, Instant refreshAfter) {
        StringBuilder builder = new StringBuilder();
        builder.append(idToken);
        builder.append(COOKIE_DELIMITER);
        builder.append(displayName);
        if (refreshAfter != null) {
            builder.append(COOKIE_DELIMITER);
            builder.append(refreshAfter);
        }

        return builder.toString();
    }

    public AuthCookieData parseAuthCookeData(String authCookieValue) {
        String[] cookiePieces = authCookieValue.split(Pattern.quote(COOKIE_DELIMITER));

        String idToken = cookiePieces[0];
        String displayName = cookiePieces.length > 1 ? cookiePieces[1] : "";
        Instant issuedAt = cookiePieces.length > 2 ? Instant.parse(cookiePieces[2]) : null;

        return new AuthCookieData(idToken, displayName, issuedAt);
    }

    public record AuthCookieData(String idToken, String displayName, Instant refreshAfter) { }
    public record AuthRequestResponse(boolean success, String errorMsg) { }
}
