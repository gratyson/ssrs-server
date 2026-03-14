package com.gt.ssrs.security;

import java.util.regex.Pattern;

public abstract class AuthService {

    private final String passwordValidationRegex;

    private final Pattern validationPattern;

    protected AuthService(String passwordValidationRegex) {
        this.passwordValidationRegex = passwordValidationRegex;

        this.validationPattern = Pattern.compile(this.passwordValidationRegex);
    }

    public abstract String authenticateAndGetToken(String username, String password);

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

    public record AuthRequestResponse(boolean success, String errorMsg) { }
}
