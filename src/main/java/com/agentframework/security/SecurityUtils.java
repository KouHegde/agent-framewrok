package com.agentframework.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.Set;

/**
 * Utility class for security operations.
 * Provides easy access to current user info from JWT token.
 */
public final class SecurityUtils {

    private SecurityUtils() {
        // Utility class
    }

    /**
     * Get current authentication.
     */
    public static Optional<JwtAuthenticationToken> getCurrentAuthentication() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            return Optional.of(jwtAuth);
        }
        return Optional.empty();
    }

    /**
     * Get current user ID.
     */
    public static Optional<String> getCurrentUserId() {
        return getCurrentAuthentication().map(JwtAuthenticationToken::getUserId);
    }

    /**
     * Get current user email.
     */
    public static Optional<String> getCurrentUserEmail() {
        return getCurrentAuthentication().map(JwtAuthenticationToken::getEmail);
    }

    /**
     * Get current tenant ID.
     */
    public static Optional<String> getCurrentTenantId() {
        return getCurrentAuthentication().map(JwtAuthenticationToken::getTenantId);
    }

    /**
     * Get current user role.
     */
    public static Optional<String> getCurrentRole() {
        return getCurrentAuthentication().map(JwtAuthenticationToken::getRole);
    }

    /**
     * Get current user scopes.
     */
    public static Set<String> getCurrentScopes() {
        return getCurrentAuthentication()
                .map(JwtAuthenticationToken::getScopes)
                .orElse(Set.of());
    }

    /**
     * Check if current user has a specific scope.
     */
    public static boolean hasScope(String scope) {
        return getCurrentAuthentication()
                .map(auth -> auth.hasScope(scope))
                .orElse(false);
    }

    /**
     * Check if current user has any of the specified scopes.
     */
    public static boolean hasAnyScope(String... scopes) {
        return getCurrentAuthentication()
                .map(auth -> auth.hasAnyScope(scopes))
                .orElse(false);
    }

    /**
     * Check if current user has all of the specified scopes.
     */
    public static boolean hasAllScopes(String... scopes) {
        return getCurrentAuthentication()
                .map(auth -> auth.hasAllScopes(scopes))
                .orElse(false);
    }

    /**
     * Check if current user is admin.
     */
    public static boolean isAdmin() {
        return getCurrentAuthentication()
                .map(JwtAuthenticationToken::isAdmin)
                .orElse(false);
    }

    /**
     * Check if current user belongs to the specified tenant.
     */
    public static boolean isInTenant(String tenantId) {
        return getCurrentTenantId()
                .map(tid -> tid.equals(tenantId))
                .orElse(false);
    }

    /**
     * Require a specific scope, throw exception if not present.
     */
    public static void requireScope(String scope) {
        if (!hasScope(scope)) {
            throw new SecurityException("Missing required scope: " + scope);
        }
    }

    /**
     * Require admin role, throw exception if not admin.
     */
    public static void requireAdmin() {
        if (!isAdmin()) {
            throw new SecurityException("Admin access required");
        }
    }
}
