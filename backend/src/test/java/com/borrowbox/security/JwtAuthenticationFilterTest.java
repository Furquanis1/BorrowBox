package com.borrowbox.security;

import com.borrowbox.entity.User;
import com.borrowbox.entity.UserRole;
import com.borrowbox.entity.UserStatus;
import com.borrowbox.repository.UserRepository;
import com.borrowbox.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class JwtAuthenticationFilterTest {

    private static final String EMAIL = "alice@example.com";
    private static final String TOKEN = "test-token";

    @Mock
    private JwtService jwtService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private FilterChain filterChain;

    @BeforeEach
    void clearSecurityContextBeforeEach() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void adminAuthorityComesFromPersistedUser() throws Exception {
        User user = persistedUser(UserRole.ADMIN);
        when(jwtService.extractUserId(TOKEN)).thenReturn(7L);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(jwtService.isTokenValid(TOKEN, user)).thenReturn(true);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users");
        request.addHeader("Authorization", "Bearer " + TOKEN);
        MockHttpServletResponse response = new MockHttpServletResponse();

        new JwtAuthenticationFilter(jwtService, userRepository).doFilter(request, response, filterChain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getName()).isEqualTo(EMAIL);
        assertThat(authentication.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ADMIN");
        verify(filterChain).doFilter(any(), any());
    }

    @Test
    void regularUserGetsOnlyUserAuthority() throws Exception {
        User user = persistedUser(UserRole.USER);
        when(jwtService.extractUserId(TOKEN)).thenReturn(7L);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(jwtService.isTokenValid(TOKEN, user)).thenReturn(true);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users");
        request.setCookies(new Cookie("jwt", TOKEN));
        MockHttpServletResponse response = new MockHttpServletResponse();

        new JwtAuthenticationFilter(jwtService, userRepository).doFilter(request, response, filterChain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_USER");
    }

    @Test
    void suspendedUserEstablishesNoAuthentication() throws Exception {
        User user = persistedUser(UserRole.USER);
        user.setStatus(UserStatus.SUSPENDED);
        when(jwtService.extractUserId(TOKEN)).thenReturn(7L);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users");
        request.setCookies(new Cookie("jwt", TOKEN));
        MockHttpServletResponse response = new MockHttpServletResponse();

        new JwtAuthenticationFilter(jwtService, userRepository).doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(jwtService, never()).isTokenValid(any(), any());
        verify(filterChain).doFilter(any(), any());
    }

    @Test
    void deletedUserEstablishesNoAuthentication() throws Exception {
        when(jwtService.extractUserId(TOKEN)).thenReturn(7L);
        when(userRepository.findById(7L)).thenReturn(Optional.empty());

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users");
        request.setCookies(new Cookie("jwt", TOKEN));
        MockHttpServletResponse response = new MockHttpServletResponse();

        new JwtAuthenticationFilter(jwtService, userRepository).doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(jwtService, never()).isTokenValid(any(), any());
        verify(filterChain).doFilter(any(), any());
    }

    @Test
    void malformedOrInvalidTokenEstablishesNoAuthentication() throws Exception {
        when(jwtService.extractUserId(TOKEN)).thenReturn(null);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users");
        request.setCookies(new Cookie("jwt", TOKEN));
        MockHttpServletResponse response = new MockHttpServletResponse();

        new JwtAuthenticationFilter(jwtService, userRepository).doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(userRepository);
        verify(jwtService, never()).isTokenValid(any(), any());
        verify(filterChain).doFilter(any(), any());
    }

    @Test
    void mismatchedTokenIdentityEstablishesNoAuthentication() throws Exception {
        User user = persistedUser(UserRole.USER);
        when(jwtService.extractUserId(TOKEN)).thenReturn(7L);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(jwtService.isTokenValid(TOKEN, user)).thenReturn(false);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users");
        request.setCookies(new Cookie("jwt", TOKEN));
        MockHttpServletResponse response = new MockHttpServletResponse();

        new JwtAuthenticationFilter(jwtService, userRepository).doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(any(), any());
    }

    @Test
    void requestWithoutTokenEstablishesNoAuthentication() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new JwtAuthenticationFilter(jwtService, userRepository).doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(jwtService, userRepository);
        verify(filterChain).doFilter(any(), any());
    }

    @Test
    void databaseFailurePropagatesWithoutContinuingTheChain() throws Exception {
        when(jwtService.extractUserId(TOKEN)).thenReturn(7L);
        when(userRepository.findById(7L))
                .thenThrow(new DataAccessResourceFailureException("database unavailable"));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users");
        request.setCookies(new Cookie("jwt", TOKEN));
        MockHttpServletResponse response = new MockHttpServletResponse();

        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, userRepository);

        assertThatThrownBy(() -> filter.doFilter(request, response, filterChain))
                .isInstanceOf(DataAccessResourceFailureException.class);
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void cookieTakesPrecedenceOverBearerToken() throws Exception {
        User user = persistedUser(UserRole.USER);
        when(jwtService.extractUserId(TOKEN)).thenReturn(7L);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(jwtService.isTokenValid(TOKEN, user)).thenReturn(true);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users");
        request.setCookies(new Cookie("jwt", TOKEN));
        request.addHeader("Authorization", "Bearer ignored-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new JwtAuthenticationFilter(jwtService, userRepository).doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        verify(jwtService, never()).extractUserId("ignored-token");
    }

    private User persistedUser(UserRole role) {
        User user = new User("Alice", EMAIL);
        user.setId(7L);
        user.setPasswordHash("hash");
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }
}
