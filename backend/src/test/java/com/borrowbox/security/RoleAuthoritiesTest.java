package com.borrowbox.security;

import com.borrowbox.entity.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import static org.assertj.core.api.Assertions.assertThat;

public class RoleAuthoritiesTest {

    @Test
    void userRoleMapsToRoleUser() {
        assertThat(RoleAuthorities.authoritiesFor(UserRole.USER))
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_USER");
    }

    @Test
    void adminRoleMapsToRoleAdmin() {
        assertThat(RoleAuthorities.authoritiesFor(UserRole.ADMIN))
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ADMIN");
    }

    @Test
    void missingRoleFallsBackToLeastPrivilege() {
        assertThat(RoleAuthorities.authoritiesFor(null))
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_USER");
    }
}
