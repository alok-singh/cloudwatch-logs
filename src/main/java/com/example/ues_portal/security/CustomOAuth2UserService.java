package com.example.ues_portal.security;

import com.example.ues_portal.model.User;
import com.example.ues_portal.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class CustomOAuth2UserService extends OidcUserService {

    @Value("${app.security.allowed-domain}")
    private String allowedDomain;

    @Autowired
    private UserRepository userRepository;

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oidcUser = super.loadUser(userRequest);
        String email = oidcUser.getAttribute("email");

        // if (email == null || !email.toLowerCase().endsWith("@" + allowedDomain.toLowerCase())) {
        //     throw new OAuth2AuthenticationException("Login failed: User's email domain is not allowed: " + email);
        // }

        Optional<User> userOptional = userRepository.findByEmail(email);
        User user;
        if (userOptional.isPresent()) {
            user = userOptional.get();
            user.setLastLogin(LocalDateTime.now());
        } else {
            user = new User();
            user.setEmail(email);
            user.setFirstName(oidcUser.getAttribute("name"));
            user.setLastName(oidcUser.getAttribute("family_name"));
            user.setProvider("azure");
            user.setProviderId(oidcUser.getName());
            user.setCreatedAt(LocalDateTime.now());
            user.setLastLogin(LocalDateTime.now());
        }
        
        userRepository.save(user);

        user.setAttributes(oidcUser.getAttributes());
        user.setIdToken(oidcUser.getIdToken());
        user.setUserInfo(oidcUser.getUserInfo());

        return user;
    }
}