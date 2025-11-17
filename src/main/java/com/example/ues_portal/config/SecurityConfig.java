package com.example.ues_portal.config;

import com.example.ues_portal.security.JwtAuthenticationEntryPoint;
import com.example.ues_portal.security.JwtRequestFilter;
import com.example.ues_portal.security.CustomOAuth2UserService;
import com.example.ues_portal.security.OAuth2AuthenticationSuccessHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

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
                                           JwtRequestFilter jwtRequestFilter,
                                           JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint,
                                           OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler,
                                           CustomOAuth2UserService customOAuth2UserService) throws Exception {
        if (securityEnabled && azureClientId != null && !azureClientId.isEmpty()) {
            http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(authz -> authz
                    .requestMatchers("/login/oauth2/code/*", "/oauth2/authorization/azure").permitAll()
                    .anyRequest().authenticated()
                )
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                    .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                )
                .addFilterBefore(jwtRequestFilter, UsernamePasswordAuthenticationFilter.class)
                .oauth2Login(oauth2 -> oauth2
                    .userInfoEndpoint(userInfo -> userInfo
                        .userService(customOAuth2UserService)
                    )
                    .successHandler(oAuth2AuthenticationSuccessHandler)
                );
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