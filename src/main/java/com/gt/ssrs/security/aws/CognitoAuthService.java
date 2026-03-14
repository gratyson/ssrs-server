package com.gt.ssrs.security.aws;

import com.gt.ssrs.security.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.Map;

@Component
public class CognitoAuthService extends AuthService {

    private static final Logger log = LoggerFactory.getLogger(CognitoAuthService.class);

    private static final String HMAC_SHA256 = "HmacSHA256";

    private static final String USERNAME_AUTH_PARAM = "USERNAME";
    private static final String PASSWORD_AUTH_PARAM = "PASSWORD";
    private static final String SECRET_HASH_AUTH_PARAM = "SECRET_HASH";

    private final CognitoIdentityProviderClient cognitoIdentityProviderClient;
    private final String clientId;
    private final String userPoolId;
    private final String passwordValidationMessage;

    private Mac mac;

    @Autowired
    public CognitoAuthService(CognitoIdentityProviderClient cognitoIdentityProviderClient,
                              @Value("${aws.cognito.clientId}") String clientId,
                              @Value("${aws.cognito.userPoolId}") String userPoolId,
                              @Value("${ssrs.auth.validation.regex}") String passwordValidationRegex,
                              @Value("${ssrs.auth.validation.message}") String passwordValidationMessage) {
        super(passwordValidationRegex);

        this.cognitoIdentityProviderClient = cognitoIdentityProviderClient;
        this.clientId = clientId;
        this.userPoolId = userPoolId;
        this.passwordValidationMessage = passwordValidationMessage;

        this.mac = null;  // requires a callout to Cognito to build, so defer until after instantiation
    }

    @Override
    public String authenticateAndGetToken(String username, String password) {
        AdminInitiateAuthRequest request = AdminInitiateAuthRequest.builder()
                .clientId(clientId)
                .userPoolId(userPoolId)
                .authFlow(AuthFlowType.ADMIN_USER_PASSWORD_AUTH)
                .authParameters(Map.of(
                        USERNAME_AUTH_PARAM, username,
                        PASSWORD_AUTH_PARAM, password,
                        SECRET_HASH_AUTH_PARAM, getSecretHash(username)))
                .build();

        AdminInitiateAuthResponse response;
        try {
            response = cognitoIdentityProviderClient.adminInitiateAuth(request);
        } catch (NotAuthorizedException ex) {
            return "";
        }

        if (response == null || response.authenticationResult() == null) {
            return "";
        }

        return response.authenticationResult().idToken();
    }

    @Override
    public AuthRequestResponse changeUserPassword(String username, String currentPassword, String newPassword, String reenterNewPassword) {
        if (!validateCorrectPassword(username, currentPassword)) {
            return new AuthRequestResponse(false, "Current password is not correct");
        }

        if (!validateNewPassword(newPassword, reenterNewPassword)) {
            return new AuthRequestResponse(false, passwordValidationMessage);
        }

        return setUserPassword(username, newPassword);
    }

    @Override
    public AuthRequestResponse registerUser(String username, String password, String reenterPassword) {
        if (!validateNewPassword(password, reenterPassword)) {
            return new AuthRequestResponse(false, passwordValidationMessage);
        }

        AdminCreateUserRequest request = AdminCreateUserRequest.builder()
                .username(username)
                .userPoolId(userPoolId)
                .build();

        AdminCreateUserResponse response = cognitoIdentityProviderClient.adminCreateUser(request);

        if (response.user() == null) {
            return new AuthRequestResponse(false, "Failed to create user");
        }

        return setUserPassword(username, password);
    }

    private boolean validateCorrectPassword(String username, String password) {
        String accessToken = authenticateAndGetToken(username, password);

        return accessToken != null && !accessToken.isBlank();
    }

    private AuthRequestResponse setUserPassword(String username, String newPassword) {
        AdminSetUserPasswordRequest request = AdminSetUserPasswordRequest.builder()
                .userPoolId(userPoolId)
                .username(username)
                .permanent(true)
                .password(newPassword)
                .build();

        try {
            AdminSetUserPasswordResponse response = cognitoIdentityProviderClient.adminSetUserPassword(request);
        } catch (CognitoIdentityProviderException ex) {
            return new AuthRequestResponse(false, ex.getMessage());
        }

        return new AuthRequestResponse(true, "");
    }

    private String getSecretHash(String username) {
        Mac mac = getMac();
        String message = username + clientId;

        byte[] hmacBytes = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));

        return Base64.getEncoder().encodeToString(hmacBytes);
    }

    private Mac getMac() {
        if (this.mac == null) {
            this.mac = initMac();
        }

        return this.mac;
    }

    private Mac initMac() {
        try {
            String userPoolClientSecret = getUserPoolClientSecret();
            SecretKeySpec keySpec = new SecretKeySpec(userPoolClientSecret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(keySpec);

            return mac;
        } catch (GeneralSecurityException ex) {
            log.error("Failed to initiate Mac", ex);
            throw new SecurityException(ex);
        }
    }

    private String getUserPoolClientSecret() {
        DescribeUserPoolClientRequest request = DescribeUserPoolClientRequest.builder()
                .userPoolId(userPoolId)
                .clientId(clientId)
                .build();

        DescribeUserPoolClientResponse response = cognitoIdentityProviderClient.describeUserPoolClient(request);

        return response.userPoolClient().clientSecret();
    }
}
