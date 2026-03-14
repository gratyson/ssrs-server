package com.gt.ssrs.filter.awslocal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPublicKeySpec;
import java.time.Instant;
import java.util.*;

@Component
public class CognitoJwksProvider {

    private static final Logger log = LoggerFactory.getLogger(CognitoJwksProvider.class);

    private static final String COGNITO_JWKS_ENDPOINT_FORMAT = "https://cognito-idp.%s.amazonaws.com/%s/.well-known/jwks.json";

    private static final String KEYS_NODE_NAME = "keys";
    private static final String JWKS_KEY_ID = "kid";
    private static final String JWKS_KEY_MODULUS = "n";
    private static final String JWKS_KEY_EXPONENT = "e";
    private static final String RSA_KEY_ALGORITHM = "RSA";
    private static final String RSA_ALGORITHM_PREFIX = "RS";
    private static final int REFRESH_PERIOD_IN_SECONDS = 86400;  // 1 day

    private final String region;
    private final String userPoolId;

    private Map<String, PublicKey> rsaKeysByKeyId;
    private Instant nextRefreshInstant;

    @Autowired
    public CognitoJwksProvider(@Value("${aws.region}") String region,
                               @Value("${aws.cognito.userPoolId}") String userPoolId) {
        this.region = region;
        this.userPoolId = userPoolId;

        this.rsaKeysByKeyId = null;
        this.nextRefreshInstant = Instant.MIN;
    }

    public PublicKey getJwksKey(String keyId, String algorithm) {
        verifyAlgorithm(algorithm);

        if (rsaKeysByKeyId == null || Instant.now().isAfter(nextRefreshInstant)) {
            try {
                rsaKeysByKeyId = fetchKeys();
            } catch (IOException | InterruptedException | GeneralSecurityException e) {
                log.error("Failed to retrieve jwk", e);
                throw new SecurityException("Failed to retrieve jwk", e);
            }

            nextRefreshInstant = Instant.now().plusSeconds(REFRESH_PERIOD_IN_SECONDS);
        }

        return rsaKeysByKeyId.get(keyId);
    }

    private void verifyAlgorithm(String algorithm) {
        if (!algorithm.startsWith(RSA_ALGORITHM_PREFIX)) {
            throw new SecurityException("Signing key algorithm " + algorithm + " is not supported");
        }
    }

    private Map<String, PublicKey> fetchKeys() throws IOException, InterruptedException, GeneralSecurityException {
        String jwksEndpoint = String.format(COGNITO_JWKS_ENDPOINT_FORMAT, region, userPoolId);

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(jwksEndpoint))
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        return parseKeys(response.body());
    }

    private Map<String, PublicKey> parseKeys(String jwksStr) throws IOException, GeneralSecurityException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode node = mapper.readTree(jwksStr);

        return extractKeys(node.get(KEYS_NODE_NAME).values());
    }

    private Map<String, PublicKey> extractKeys(Collection<JsonNode> nodes) throws GeneralSecurityException {
        Map<String, PublicKey> keys = new HashMap<>();

        for(JsonNode node : nodes) {
            String keyId = node.get(JWKS_KEY_ID).asString();
            String modulus = node.get(JWKS_KEY_MODULUS).asString();
            String exponent = node.get(JWKS_KEY_EXPONENT).asString();

            byte[] modulusBytes = Base64.getUrlDecoder().decode(modulus);
            byte[] exponentBytes = Base64.getUrlDecoder().decode(exponent);

            keys.put(keyId, buildRsaKey(modulusBytes, exponentBytes));
        }

        return keys;
    }

    private PublicKey buildRsaKey(byte[] modulusBytes, byte[] exponentBytes) {
        try {
            KeyFactory keyFactory = KeyFactory.getInstance(RSA_KEY_ALGORITHM);
            return keyFactory.generatePublic(new RSAPublicKeySpec(new BigInteger(1, modulusBytes), new BigInteger(1, exponentBytes)));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException ex) {
            log.error("Unable to generate key", ex);
            throw new SecurityException("Unable to generate key", ex);
        }
    }
}