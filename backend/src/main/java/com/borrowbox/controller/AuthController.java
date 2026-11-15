package com.borrowbox.controller;

import com.borrowbox.dto.AuthResponse;
import com.borrowbox.dto.LoginRequest;
import com.borrowbox.dto.UserCreateRequest;
import com.borrowbox.entity.User;
import com.borrowbox.entity.UserStatus;
import com.borrowbox.exception.UnauthorizedException;
import com.borrowbox.exception.ResourceNotFoundException;
import com.borrowbox.service.JwtService;
import com.borrowbox.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ResponseCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String JWT_COOKIE_NAME = "jwt";
    private static final String JWT_COOKIE_SAME_SITE = "Lax";

    private final UserService userService;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final boolean cookieSecure;

    public AuthController(UserService userService, JwtService jwtService, PasswordEncoder passwordEncoder,
                          @Value("${app.jwt.cookie-secure:false}") boolean cookieSecure) {
        this.userService = userService;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.cookieSecure = cookieSecure;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody UserCreateRequest request) {
        User createdUser = userService.createUser(request);
        String token = jwtService.generateToken(createdUser);
        
        ResponseCookie cookie = buildJwtCookie(token, jwtService.getExpirationMs() / 1000);

        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(new AuthResponse(token, createdUser));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        User user;
        try {
            user = userService.findByEmail(request.email());
        } catch (ResourceNotFoundException ex) {
            throw new UnauthorizedException("Invalid email or password");
        }

        if (user.getPasswordHash() == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid email or password");
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new UnauthorizedException("Invalid email or password");
        }

        String token = jwtService.generateToken(user);
        
        ResponseCookie cookie = buildJwtCookie(token, jwtService.getExpirationMs() / 1000);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(new AuthResponse(token, user));
    }

    /**
     * Returns the currently authenticated user from the JWT cookie.
     * The JwtAuthenticationFilter already validates the cookie and sets the
     * SecurityContext; we just read the principal here.
     */
    @GetMapping("/me")
    public ResponseEntity<?> me() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated()
                || auth.getPrincipal().equals("anonymousUser")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // The filter sets a Spring Security UserDetails as principal
        String email;
        Object principal = auth.getPrincipal();
        if (principal instanceof UserDetails ud) {
            email = ud.getUsername(); // we store email as username
        } else {
            email = principal.toString();
        }

        User user = userService.findByEmail(email);
        return ResponseEntity.ok(new AuthResponse(null, user));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        ResponseCookie cookie = buildJwtCookie("", 0);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .build();
    }

    private ResponseCookie buildJwtCookie(String value, long maxAgeSeconds) {
        return ResponseCookie.from(JWT_COOKIE_NAME, value)
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/")
                .maxAge(maxAgeSeconds)
                .sameSite(JWT_COOKIE_SAME_SITE)
                .build();
    }
}
