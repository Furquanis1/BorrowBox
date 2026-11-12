package com.borrowbox.security;

import com.borrowbox.entity.User;
import com.borrowbox.entity.UserRole;
import com.borrowbox.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

@Service
public class AuthorizationService {

    private final UserRepository userRepository;

    public AuthorizationService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User requireAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new AccessDeniedException("Authentication is required");
        }

        String email = principalEmail(authentication.getPrincipal());
        if (email == null || email.isBlank()) {
            throw new AccessDeniedException("Authentication is required");
        }

        return userRepository.findByEmail(email)
                .orElseThrow(() -> new AccessDeniedException("Authenticated principal does not map to a user"));
    }

    public void requireAdmin() {
        if (!isAdmin(requireAuthenticated())) {
            throw new AccessDeniedException("Administrator access is required");
        }
    }

    public void requireSelfOrAdmin(Long targetUserId) {
        User user = requireAuthenticated();
        if (isAdmin(user)) {
            return;
        }
        if (targetUserId != null && targetUserId.equals(user.getId())) {
            return;
        }
        throw new AccessDeniedException("You are not allowed to access this user");
    }

    public boolean isAdmin(User user) {
        return user != null && user.getRole() == UserRole.ADMIN;
    }

    private String principalEmail(Object principal) {
        if (principal instanceof UserDetails userDetails) {
            return userDetails.getUsername();
        }
        return principal == null ? null : principal.toString();
    }
}
