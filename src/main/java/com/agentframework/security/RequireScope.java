package com.agentframework.security;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to require specific scope(s) for method access.
 * Uses Spring Security's @PreAuthorize under the hood.
 * 
 * Usage examples:
 * 
 * @RequireScope("agents:read")  // Single scope
 * @RequireScope({"agents:read", "agents:write"})  // Any of these scopes
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("@scopeChecker.hasAnyScope(authentication, #root.args)")
public @interface RequireScope {
    /**
     * The scope(s) required. If multiple, user needs at least one.
     */
    String[] value();
}
