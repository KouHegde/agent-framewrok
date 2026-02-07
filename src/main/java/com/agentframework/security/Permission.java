package com.agentframework.security;

/**
 * Permissions/scopes for fine-grained access control.
 * These are included in JWT tokens and used for authorization.
 */
public enum Permission {
    
    // Agent permissions
    AGENTS_READ("agents:read"),
    AGENTS_WRITE("agents:write"),
    AGENTS_DELETE("agents:delete"),
    AGENTS_EXECUTE("agents:execute"),
    
    // Session permissions
    SESSIONS_READ("sessions:read"),
    SESSIONS_WRITE("sessions:write"),
    
    // User management permissions (admin only)
    USERS_READ("users:read"),
    USERS_WRITE("users:write"),
    USERS_DELETE("users:delete"),
    
    // System permissions (admin only)
    SYSTEM_ADMIN("system:admin"),
    SYSTEM_METRICS("system:metrics");
    
    private final String scope;
    
    Permission(String scope) {
        this.scope = scope;
    }
    
    public String getScope() {
        return scope;
    }
    
    /**
     * Get Permission from scope string.
     */
    public static Permission fromScope(String scope) {
        for (Permission p : values()) {
            if (p.scope.equals(scope)) {
                return p;
            }
        }
        throw new IllegalArgumentException("Unknown scope: " + scope);
    }
}
