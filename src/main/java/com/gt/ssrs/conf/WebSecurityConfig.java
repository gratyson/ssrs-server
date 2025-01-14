package com.gt.ssrs.conf;

import com.gt.ssrs.filter.JwtAuthFilter;
import jakarta.servlet.DispatcherType;
import org.apache.catalina.Context;
import org.apache.catalina.connector.Connector;
import org.apache.coyote.ajp.AjpNioProtocol;
import org.apache.tomcat.util.descriptor.web.SecurityCollection;
import org.apache.tomcat.util.descriptor.web.SecurityConstraint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.servlet.server.ServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.LogoutFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.net.InetSocketAddress;
import java.util.List;

@Configuration
@EnableWebSecurity
public class WebSecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(WebSecurityConfig.class);

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter) throws Exception {
        XorCsrfTokenRequestAttributeHandler delegate = new XorCsrfTokenRequestAttributeHandler();
        delegate.setCsrfRequestAttributeName("_csrf");

        http.cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(delegate::handle))
                .authorizeHttpRequests((requests) -> requests
                        .dispatcherTypeMatchers(DispatcherType.FORWARD, DispatcherType.ERROR).permitAll()
                        .requestMatchers("/").permitAll()
                        .requestMatchers("/app/**").permitAll()
                        .requestMatchers("/styles-*.css").permitAll()
                        .requestMatchers("/main-*.js").permitAll()
                        .requestMatchers("/polyfills-*.js").permitAll()
                        .requestMatchers("/rest/auth/login").permitAll()
                        .requestMatchers("/rest/auth/register").permitAll()
                        .requestMatchers("/rest/auth/canRegister").permitAll()
                        .anyRequest().authenticated())
                .logout((logout) -> logout.permitAll())
                .addFilterAfter(jwtAuthFilter, LogoutFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider authenticationProvider = new DaoAuthenticationProvider();
        authenticationProvider.setUserDetailsService(userDetailsService);
        authenticationProvider.setPasswordEncoder(passwordEncoder);

        return new ProviderManager(authenticationProvider);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public ServletWebServerFactory servletContainer(@Value("${server.port.http}") int httpPort,
                                                    @Value("${server.port}") int httpsPort,
                                                    @Value("${ajp.port:0}") int ajpPort,
                                                    @Value("${ajp.secret:}") String ajpSecret,
                                                    @Value("${ajp.enabled:false}") boolean ajpEnabled) {
        TomcatServletWebServerFactory tomcat = new TomcatServletWebServerFactory() {
            @Override
            protected void postProcessContext(Context context) {
                SecurityConstraint securityConstraint = new SecurityConstraint();
                securityConstraint.setUserConstraint("CONFIDENTIAL");
                SecurityCollection collection = new SecurityCollection();
                collection.addPattern("/*");
                securityConstraint.addCollection(collection);
                context.addConstraint(securityConstraint);
            }
        };
        tomcat.addAdditionalTomcatConnectors(redirectConnector(httpPort, httpsPort));

        if(ajpEnabled) {
            if (ajpPort > 0 && !ajpSecret.isBlank()) {
                tomcat.addAdditionalTomcatConnectors(ajpConnector(ajpPort, ajpSecret));
            } else {
                log.error("AJP connector is enabled but not configured properly.");
            }
        }

        return tomcat;
    }

    private Connector redirectConnector(int httpPort, int httpsPort) {
        Connector connector = new Connector("org.apache.coyote.http11.Http11NioProtocol");
        connector.setScheme("http");
        connector.setPort(httpPort);
        connector.setSecure(false);
        connector.setRedirectPort(httpsPort);
        return connector;
    }

    private Connector ajpConnector(int ajpPort, String ajpSecret) {
        Connector ajpConnector = new Connector("AJP/1.3");

        AjpNioProtocol protocol= (AjpNioProtocol)ajpConnector.getProtocolHandler();
        protocol.setSecret(ajpSecret);

        ajpConnector.setPort(ajpPort);
        ajpConnector.setSecure(true);
        return ajpConnector;
    }
}
