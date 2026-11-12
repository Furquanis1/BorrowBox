package com.borrowbox.security;

import com.borrowbox.entity.User;
import com.borrowbox.entity.UserRole;
import com.borrowbox.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AuthorizationServiceTest {

    private static final String EMAIL = "alice@example.com";

    @Mock
    private UserRepository userRepository;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private AuthorizationService service() {
        return new AuthorizationService(userRepository);
    }

    private void authenticate(String email) {
        UserDetails principal = new org.springframework.security.core.userdetails.User(
                email, "ignored", AuthorityUtils.NO_AUTHORITIES);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private void authenticateAnonymous() {
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken("anonymous-key", "anonymousUser",
                        AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
    }

    private User persistedUser(Long id, UserRole role) {
        User user = new User("Alice", EMAIL);
        user.setId(id);
        user.setRole(role);
        return user;
    }

    @Test
    void missingAuthenticationIsDenied() {
        AuthorizationService service = service();

        assertThatThrownBy(service::requireAuthenticated).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(service::requireAdmin).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.requireSelfOrAdmin(7L)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(userRepository);
    }

    @Test
    void anonymousAuthenticationIsDenied() {
        authenticateAnonymous();
        AuthorizationService service = service();

        assertThatThrownBy(service::requireAuthenticated).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(service::requireAdmin).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.requireSelfOrAdmin(7L)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(userRepository);
    }

    @Test
    void unknownPrincipalIsDenied() {
        authenticate(EMAIL);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
        AuthorizationService service = service();

        assertThatThrownBy(service::requireAuthenticated).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(service::requireAdmin).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.requireSelfOrAdmin(7L)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void requireAuthenticatedReturnsPersistedUser() {
        authenticate(EMAIL);
        User user = persistedUser(7L, UserRole.USER);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        User resolved = service().requireAuthenticated();

        assertThat(resolved).isSameAs(user);
        assertThat(resolved.getEmail()).isEqualTo(EMAIL);
        assertThat(resolved.getRole()).isEqualTo(UserRole.USER);
    }

    @Test
    void nonUserDetailsPrincipalIsResolvedByEmail() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(EMAIL, null, AuthorityUtils.NO_AUTHORITIES));
        User user = persistedUser(7L, UserRole.USER);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        assertThat(service().requireAuthenticated()).isSameAs(user);
    }

    @Test
    void regularUserCannotPerformAdminActions() {
        authenticate(EMAIL);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(persistedUser(7L, UserRole.USER)));

        assertThatThrownBy(service()::requireAdmin).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void adminCanPerformAdminActions() {
        authenticate(EMAIL);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(persistedUser(1L, UserRole.ADMIN)));

        assertThatCode(service()::requireAdmin).doesNotThrowAnyException();
    }

    @Test
    void regularUserCanAccessOwnRecord() {
        authenticate(EMAIL);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(persistedUser(7L, UserRole.USER)));

        assertThatCode(() -> service().requireSelfOrAdmin(7L)).doesNotThrowAnyException();
    }

    @Test
    void regularUserCannotAccessAnotherUsersRecord() {
        authenticate(EMAIL);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(persistedUser(7L, UserRole.USER)));

        assertThatThrownBy(() -> service().requireSelfOrAdmin(8L)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void adminCanAccessAnotherUsersRecord() {
        authenticate(EMAIL);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(persistedUser(1L, UserRole.ADMIN)));

        assertThatCode(() -> service().requireSelfOrAdmin(8L)).doesNotThrowAnyException();
    }

    @Test
    void roleIsReadFromRepositoryOnEveryCheck() {
        authenticate(EMAIL);
        when(userRepository.findByEmail(EMAIL))
                .thenReturn(Optional.of(persistedUser(7L, UserRole.USER)))
                .thenReturn(Optional.of(persistedUser(7L, UserRole.ADMIN)));
        AuthorizationService service = service();

        assertThatThrownBy(service::requireAdmin).isInstanceOf(AccessDeniedException.class);
        assertThatCode(service::requireAdmin).doesNotThrowAnyException();
        verify(userRepository, times(2)).findByEmail(EMAIL);
    }
}
