package com.example.ues_portal.model;

import java.util.Collection;
import java.util.Map;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

public class User implements OAuth2User {

    private final OAuth2User oauth2User;
    private final String jwt;

    public User(OAuth2User oauth2User, String jwt) {
        this.oauth2User = oauth2User;
        this.jwt = jwt;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return oauth2User.getAttributes();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return oauth2User.getAuthorities();
    }

    @Override
    public String getName() {
        return oauth2User.getAttribute("name");
    }

    public String getJwt() {
        return jwt;
    }
}