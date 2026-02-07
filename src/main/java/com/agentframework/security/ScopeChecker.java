package com.agentframework.security;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Bean for checking scopes in SpEL expressions.
 * Used by @PreAuthorize annotations.
 * 
 * Usage in @PreAuthorize:
 * @PreAuthorize("@scopeChecker.hasScope(authentication, 'agents:read')")
 * @PreAuthorize("@scopeChecker.hasAnyScope(authentication, 'agents:read', 'agents:write')")
 * @PreAuthorize("@scopeChecker.hasAllScopes(authentication, 'agents:read', 'agents:write')")
 */
@Component("scopeChecker")
public class ScopeChecker {

    /**
     * Check if user has a specific scope.
     */
    public boolean hasScope(Authentication authentication, String scope) {
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.hasScope(scope);
        }
        return false;
    }

    /**
     * Check if user has any of the specified scopes.
     */
    public boolean hasAnyScope(Authentication authentication, String... scopes) {
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.hasAnyScope(scopes);
        }
        return false;
    }

    /**
     * Check if user has all of the specified scopes.
     */
    public boolean hasAllScopes(Authentication authentication, String... scopes) {
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.hasAllScopes(scopes);
        }
        return false;
    }

    /**
     * Check if user is admin.
     */
    public boolean isAdmin(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.isAdmin();
        }
        return false;
    }

    /**
     * Check if user belongs to the specified tenant.
     */
    public boolean isInTenant(Authentication authentication, String tenantId) {
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            return tenantId != null && tenantId.equals(jwtAuth.getTenantId());
        }
        return false;
    }

    /**
     * Check if user is admin OR belongs to tenant.
     * Useful for tenant-scoped resources where admins can access all.
     */
    public boolean isAdminOrInTenant(Authentication authentication, String tenantId) {
        return isAdmin(authentication) || isInTenant(authentication, tenantId);
    }
}
