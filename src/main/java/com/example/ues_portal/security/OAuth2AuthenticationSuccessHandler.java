package com.example.ues_portal.security;

import com.example.ues_portal.model.User;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Component
public class OAuth2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    @Autowired
    private final JwtTokenUtil jwtTokenUtil;
    private final String clientDomain;
    private final String cookieDomain;

    public OAuth2AuthenticationSuccessHandler(
            JwtTokenUtil jwtTokenUtil,
            @Value("${app.client-domain:}") String clientDomain,
            @Value("${app.cookie-domain:}") String cookieDomain
    ) {
        this.jwtTokenUtil = jwtTokenUtil;
        this.clientDomain = clientDomain;
        this.cookieDomain = cookieDomain;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {
        User user = (User) authentication.getPrincipal();
        String jwt = jwtTokenUtil.generateToken(user);

        Cookie cookie = new Cookie("token", jwt);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setSecure(request.isSecure());
        if (cookieDomain != null && !cookieDomain.isEmpty()) {
            cookie.setDomain(cookieDomain);
        }
        response.addCookie(cookie);

        String baseUrl = (clientDomain != null && !clientDomain.isEmpty()) ? clientDomain : "";
        String redirectUrl = UriComponentsBuilder.fromUriString(baseUrl + "/gateway-apis/boa-logs")
                .build().toUriString();
        response.sendRedirect(redirectUrl);
    }
}