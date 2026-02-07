package com.agentframework.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * JWT Authentication Filter.
 * Intercepts requests and validates JWT tokens.
 * Extracts claims directly from token without DB lookup.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        
        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        final String userEmail;

        // Check if Authorization header exists and starts with "Bearer "
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Extract token (remove "Bearer " prefix)
        jwt = authHeader.substring(7);

        try {
            // Extract username from token
            userEmail = jwtService.extractUsername(jwt);

            // If we have a username and no authentication is set yet
            if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                
                // Extract all claims from token (no DB lookup needed)
                String userId = jwtService.extractUserId(jwt);
                String tenantId = jwtService.extractTenantId(jwt);
                String role = jwtService.extractRole(jwt);
                Set<String> scopes = jwtService.extractScopes(jwt);
                
                // Build authorities from role and scopes
                List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                
                // Add role as authority
                if (role != null) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
                }
                
                // Add scopes as authorities (prefixed with SCOPE_)
                if (scopes != null) {
                    for (String scope : scopes) {
                        authorities.add(new SimpleGrantedAuthority("SCOPE_" + scope));
                    }
                }
                
                // Create custom JWT authentication token with all claims
                JwtAuthenticationToken authToken = new JwtAuthenticationToken(
                        userId,
                        userEmail,
                        tenantId,
                        role,
                        scopes,
                        authorities
                );
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                
                SecurityContextHolder.getContext().setAuthentication(authToken);
                log.debug("Authenticated user: {} with role: {} and {} scopes", 
                        userEmail, role, scopes != null ? scopes.size() : 0);
            }
        } catch (Exception e) {
            log.warn("JWT validation failed: {}", e.getMessage());
            // Don't throw - let the request continue without authentication
            // Protected endpoints will return 401/403 as needed
        }

        filterChain.doFilter(request, response);
    }
}
