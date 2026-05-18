package com.gt.ssrs.security.aws;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.util.Base64;

@Component
public class CognitoJwsParser {

    private static final String JWT_CLAIMS_USERNAME = "username";
    private static final String JWT_CLAIMS_USERID = "sub";
    private static final String JWT_HEADER_KEY_ID = "kid";
    private static final String JWT_HEADER_KEY_ALGORITHM = "alg";

    private final CognitoJwksProvider cognitoJwksProvider;

    @Autowired
    public CognitoJwsParser(CognitoJwksProvider cognitoJwksProvider) {
        this.cognitoJwksProvider = cognitoJwksProvider;
    }

    public String extractUserId(String idToken) {
        Claims claims = extractClaims(idToken);
        if (claims == null) {
            return "";
        }

        return claims.get(JWT_CLAIMS_USERID).toString();
    }

    public Claims extractClaims(String idToken) {
        PublicKey publicKey = getPublicKey(idToken);

        if (publicKey == null) {
            return null;
        }

        return Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(idToken)
                .getPayload();
    }

    private PublicKey getPublicKey(String idToken) {
        String tokenHeader = idToken.substring(0, idToken.indexOf('.'));
        String decodedHeader = new String(Base64.getDecoder().decode(tokenHeader), StandardCharsets.UTF_8);

        ObjectMapper mapper = new ObjectMapper();
        JsonNode node = mapper.readTree(decodedHeader);

        JsonNode keyIdNode = node.get(JWT_HEADER_KEY_ID);
        if (keyIdNode == null) {
            return null;
        }

        String keyId = keyIdNode.asString();
        String algorithm = node.get(JWT_HEADER_KEY_ALGORITHM).asString();

        return cognitoJwksProvider.getJwksKey(keyId, algorithm);
    }
}
