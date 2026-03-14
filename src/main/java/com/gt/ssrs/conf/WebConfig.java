package com.gt.ssrs.conf;

import com.gt.ssrs.auth.AuthenticatedUserResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.*;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;

import java.util.List;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final Logger log = LoggerFactory.getLogger(WebConfig.class);

    private final AuthenticatedUserResolver authenticatedUserResolver;
    private final String allowedOrigin;

    @Autowired
    public WebConfig(@Value("${server.cors.allowedOrigin:}") String allowedOrigin, AuthenticatedUserResolver authenticatedUserResolver) {
        this.allowedOrigin = allowedOrigin;
        this.authenticatedUserResolver = authenticatedUserResolver;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(authenticatedUserResolver);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (allowedOrigin != null && !allowedOrigin.isBlank()) {
            log.info("Setting allowed origin: {}", allowedOrigin);
            registry.addMapping("/rest/**").allowedOrigins(allowedOrigin);
        }
    }

    @Bean
    public JsonMapperBuilderCustomizer jacksonCustomizer() {
        return builder -> builder
                .enable(DateTimeFeature.WRITE_DURATIONS_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);
    }
}