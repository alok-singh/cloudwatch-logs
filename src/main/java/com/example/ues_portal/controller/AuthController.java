package com.example.ues_portal.controller;

import com.example.ues_portal.model.LoginRequest;
import com.example.ues_portal.model.User;
import com.example.ues_portal.repository.UserRepository;
import com.example.ues_portal.security.JwtTokenUtil;
import com.example.ues_portal.service.CustomUserDetailsService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @Autowired
    private JwtTokenUtil jwtTokenUtil;

    @Value("${app.client.domain:}")
    private String clientDomain;

    @PostMapping("/register")
    public User registerUser(@RequestBody User user) {
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setProvider("local");
        user.setCreatedAt(LocalDateTime.now());
        user.setLastLogin(LocalDateTime.now());
        return userRepository.save(user);
    }

    @GetMapping("/check-auth")
    public ResponseEntity<Authentication> checkAuthentication(Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()) {
            return ResponseEntity.ok(authentication);
        }
        return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
    }

    @PostMapping("/login")
    public ResponseEntity<Void> login(@RequestBody LoginRequest loginRequest, HttpServletRequest request, HttpServletResponse response) {
        UserDetails userDetails;
        try {
            userDetails = userDetailsService.loadUserByUsername(loginRequest.getUsername());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (passwordEncoder.matches(loginRequest.getPassword(), userDetails.getPassword())) {
            final String token = jwtTokenUtil.generateToken(userDetails);
            Cookie cookie = new Cookie("token", token);
            cookie.setPath("/");
            cookie.setSecure(request.isSecure());
            cookie.setHttpOnly(true);
            response.addCookie(cookie);
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        Cookie cookie = new Cookie("token", null); // Set value to null to clear it
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(0); // Expire the cookie immediately
        cookie.setSecure(request.isSecure()); // Match the secure flag of the original cookie
        response.addCookie(cookie);
        return ResponseEntity.ok().build();
    }
}