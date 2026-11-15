package com.borrowbox.service;

import com.borrowbox.entity.User;
import com.borrowbox.entity.UserRole;
import com.borrowbox.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class CustomUserDetailsServiceTest {

    private static final String EMAIL = "alice@example.com";

    @Mock
    private UserRepository userRepository;

    @Test
    void regularUserReceivesUserAuthority() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(persistedUser(UserRole.USER)));

        UserDetails details = new CustomUserDetailsService(userRepository).loadUserByUsername(EMAIL);

        assertThat(details.getUsername()).isEqualTo(EMAIL);
        assertThat(details.getPassword()).isEqualTo("hash");
        assertThat(details.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_USER");
    }

    @Test
    void administratorReceivesAdminAuthority() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(persistedUser(UserRole.ADMIN)));

        UserDetails details = new CustomUserDetailsService(userRepository).loadUserByUsername(EMAIL);

        assertThat(details.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ADMIN");
    }

    @Test
    void unknownEmailIsRejected() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new CustomUserDetailsService(userRepository).loadUserByUsername(EMAIL))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    private User persistedUser(UserRole role) {
        User user = new User("Alice", EMAIL);
        user.setId(7L);
        user.setPasswordHash("hash");
        user.setRole(role);
        return user;
    }
}
