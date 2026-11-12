package com.borrowbox.security;

import com.borrowbox.entity.UserRole;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

public final class RoleAuthorities {

    private RoleAuthorities() {
    }

    public static List<GrantedAuthority> authoritiesFor(UserRole role) {
        UserRole effectiveRole = role == null ? UserRole.USER : role;
        return List.of(new SimpleGrantedAuthority("ROLE_" + effectiveRole.name()));
    }
}
