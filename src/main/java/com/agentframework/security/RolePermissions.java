package com.agentframework.security;

import com.agentframework.data.entity.User.Role;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Maps roles to their default permissions.
 */
public class RolePermissions {
    
    private static final Map<Role, Set<Permission>> ROLE_PERMISSIONS = Map.of(
            Role.USER, EnumSet.of(
                    Permission.AGENTS_READ,
                    Permission.AGENTS_WRITE,
                    Permission.AGENTS_EXECUTE,
                    Permission.SESSIONS_READ,
                    Permission.SESSIONS_WRITE
            ),
            Role.ADMIN, EnumSet.allOf(Permission.class)  // Admin has all permissions
    );
    
    /**
     * Get default permissions for a role.
     */
    public static Set<Permission> getPermissions(Role role) {
        return ROLE_PERMISSIONS.getOrDefault(role, Collections.emptySet());
    }
    
    /**
     * Get scope strings for a role.
     */
    public static Set<String> getScopes(Role role) {
        Set<Permission> permissions = getPermissions(role);
        Set<String> scopes = new java.util.HashSet<>();
        for (Permission p : permissions) {
            scopes.add(p.getScope());
        }
        return scopes;
    }
}
