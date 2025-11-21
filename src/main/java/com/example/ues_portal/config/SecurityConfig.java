package com.example.ues_portal.config;


import com.example.ues_portal.security.CustomOAuth2UserService;
import com.example.ues_portal.security.OAuth2AuthenticationSuccessHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.boot.web.servlet.server.CookieSameSiteSupplier;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizationRequestRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.Collections;

@Configuration
 @EnableWebSecurity
 public class SecurityConfig {
 
     private static final Logger log = LogManager.getLogger(SecurityConfig.class);
     private final boolean securityEnabled;
     private final String azureClientId;
     private final String clientDomain;
    private final String allowedDomain;
 
     public SecurityConfig(
             @Value("${app.security.enabled:false}") boolean securityEnabled,
             @Value("${spring.security.oauth2.client.registration.azure.client-id:}") String azureClientId,
             @Value("${app.client-domain:}") String clientDomain,
             @Value("${app.security.allowed-domain:}") String allowedDomain) {
         this.securityEnabled = securityEnabled;
         this.azureClientId = azureClientId;
         this.clientDomain = clientDomain;
        this.allowedDomain = allowedDomain;
     }
     @Autowired
     private OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;

    @Autowired
    private CustomOAuth2UserService customOAuth2UserService;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        if (securityEnabled && azureClientId != null && !azureClientId.isEmpty()) {
            http
                    .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                    .csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers("/api/auth/**", "/error").permitAll()
                            .anyRequest().authenticated()
                    )
                    .oauth2Login(oauth -> oauth
                            .userInfoEndpoint(userInfo -> userInfo
                                    .oidcUserService(customOAuth2UserService)
                            )
                            .successHandler(oAuth2AuthenticationSuccessHandler)
                    );
        } else {
            http
                    .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                    .csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(authz -> authz
                    .anyRequest().permitAll()
                );
        }
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
    
    @Bean
    public CookieSameSiteSupplier applicationCookieSameSiteSupplier() {
        return CookieSameSiteSupplier.ofLax();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList(clientDomain, allowedDomain));
        configuration.setAllowedMethods(Collections.singletonList("*"));
        configuration.setAllowedHeaders(Collections.singletonList("*"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}