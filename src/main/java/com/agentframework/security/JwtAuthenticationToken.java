package com.agentframework.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.Set;

/**
 * Custom authentication token that holds JWT claims.
 * Allows accessing user info from token without DB lookup.
 */
public class JwtAuthenticationToken extends AbstractAuthenticationToken {

    private final String userId;
    private final String email;
    private final String tenantId;
    private final String role;
    private final Set<String> scopes;

    public JwtAuthenticationToken(
            String userId,
            String email,
            String tenantId,
            String role,
            Set<String> scopes,
            Collection<? extends GrantedAuthority> authorities
    ) {
        super(authorities);
        this.userId = userId;
        this.email = email;
        this.tenantId = tenantId;
        this.role = role;
        this.scopes = scopes;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;  // No credentials stored
    }

    @Override
    public Object getPrincipal() {
        return email;
    }

    public String getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getRole() {
        return role;
    }

    public Set<String> getScopes() {
        return scopes;
    }

    /**
     * Check if user has a specific scope.
     */
    public boolean hasScope(String scope) {
        return scopes != null && scopes.contains(scope);
    }

    /**
     * Check if user has any of the specified scopes.
     */
    public boolean hasAnyScope(String... requiredScopes) {
        if (scopes == null) return false;
        for (String scope : requiredScopes) {
            if (scopes.contains(scope)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if user has all of the specified scopes.
     */
    public boolean hasAllScopes(String... requiredScopes) {
        if (scopes == null) return false;
        for (String scope : requiredScopes) {
            if (!scopes.contains(scope)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Check if user is admin.
     */
    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }
}
