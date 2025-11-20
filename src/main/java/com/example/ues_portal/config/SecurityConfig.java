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
    @Autowired
    private OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;

    @Autowired
    private CustomOAuth2UserService customOAuth2UserService;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        if (securityEnabled && azureClientId != null && !azureClientId.isEmpty()) {
            http
                .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/register", "/login").permitAll()
                    .anyRequest().authenticated()
                )
                .formLogin(form -> form
                    .loginPage("/login")
                    .permitAll()
                )
                .oauth2Login(oauth -> oauth
                    .userInfoEndpoint(userInfo -> userInfo
                        .oidcUserService(customOAuth2UserService)
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

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}