package com.gt.ssrs.filter.awslocal;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.util.Base64;
import java.util.List;

@Component
public class CognitoFilter extends OncePerRequestFilter {

    private final static Logger log = LoggerFactory.getLogger(CognitoFilter.class);

    private static final String AUTHORIZATION_HEADER_NAME = "Authorization";
    private static final String JWT_CLAIMS_USERNAME = "username";
    private static final String JWT_CLAIMS_USERID = "sub";
    private static final String JWT_HEADER_KEY_ID = "kid";
    private static final String JWT_HEADER_KEY_ALGORITHM = "alg";

    private final CognitoJwksProvider cognitoJwksProvider;

    @Autowired
    public CognitoFilter(CognitoJwksProvider cognitoJwksProvider) {
        this.cognitoJwksProvider = cognitoJwksProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String idToken = request.getHeader(AUTHORIZATION_HEADER_NAME);

        if (idToken != null && !idToken.isBlank()) {
            Claims claims = extractClaims(idToken);
            if (claims != null) {
                Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(claims.get(JWT_CLAIMS_USERID).toString(), "", List.of());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        filterChain.doFilter(request, response);
    }

    private Claims extractClaims(String idToken) {
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