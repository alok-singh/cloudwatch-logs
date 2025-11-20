package com.example.ues_portal.config;


import com.example.ues_portal.security.CustomOAuth2UserService;
import com.example.ues_portal.security.OAuth2AuthenticationSuccessHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Logger log = LogManager.getLogger(SecurityConfig.class);
    private final boolean securityEnabled;
    private final String azureClientId;

    public SecurityConfig(
            @Value("${app.security.enabled:false}") boolean securityEnabled,
            @Value("${spring.security.oauth2.client.registration.azure.client-id:}") String azureClientId) {
        this.securityEnabled = securityEnabled;
        this.azureClientId = azureClientId;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler,
                                           CustomOAuth2UserService customOAuth2UserService) throws Exception {
        if (securityEnabled && azureClientId != null && !azureClientId.isEmpty()) {
            http.authorizeHttpRequests(auth -> auth.
                    anyRequest().authenticated())
                    .oauth2Login(oauth -> oauth
                            .successHandler((request, response, authentication) -> {
                                OAuth2User user = (OAuth2User) authentication.getPrincipal();
                                log.debug("User Attributes: {}", user.getAttributes());
                                response.sendRedirect("/logs");
                            }));
        } else {
            http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(authz -> authz
                    .anyRequest().permitAll()
                );
        }
        return http.build();
    }
}