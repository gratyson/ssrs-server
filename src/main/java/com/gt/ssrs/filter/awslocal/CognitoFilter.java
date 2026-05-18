package com.gt.ssrs.filter.awslocal;

import com.gt.ssrs.security.aws.CognitoJwsParser;
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

import java.io.IOException;
import java.util.List;

@Component
public class CognitoFilter extends OncePerRequestFilter {

    private final static Logger log = LoggerFactory.getLogger(CognitoFilter.class);

    private static final String AUTHORIZATION_HEADER_NAME = "Authorization";
    private static final String JWT_CLAIMS_USERNAME = "username";
    private static final String JWT_CLAIMS_USERID = "sub";
    private static final String JWT_HEADER_KEY_ID = "kid";
    private static final String JWT_HEADER_KEY_ALGORITHM = "alg";

    private final CognitoJwsParser cognitoJwsParser;

    @Autowired
    public CognitoFilter(CognitoJwsParser cognitoJwsParser) {
        this.cognitoJwsParser = cognitoJwsParser;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String idToken = request.getHeader(AUTHORIZATION_HEADER_NAME);

        if (idToken != null && !idToken.isBlank()) {
            String userId = cognitoJwsParser.extractUserId(idToken);
            if (userId != null) {
                Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(userId, "", List.of());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        filterChain.doFilter(request, response);
    }
}