package com.agentframework.security;

import com.agentframework.data.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Service for JWT token generation and validation.
 */
@Service
public class JwtService {

    @Value("${jwt.secret:404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970}")
    private String secretKey;

    @Value("${jwt.expiration:86400000}")  // 24 hours in milliseconds
    private long jwtExpiration;

    @Value("${jwt.refresh-expiration:604800000}")  // 7 days in milliseconds
    private long refreshExpiration;

    // Claim keys
    public static final String CLAIM_USER_ID = "userId";
    public static final String CLAIM_TENANT_ID = "tenantId";
    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_SCOPES = "scopes";

    /**
     * Extract username (email) from token.
     */
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * Extract user ID from token.
     */
    public String extractUserId(String token) {
        return extractClaim(token, claims -> claims.get(CLAIM_USER_ID, String.class));
    }

    /**
     * Extract tenant ID from token.
     */
    public String extractTenantId(String token) {
        return extractClaim(token, claims -> claims.get(CLAIM_TENANT_ID, String.class));
    }

    /**
     * Extract role from token.
     */
    public String extractRole(String token) {
        return extractClaim(token, claims -> claims.get(CLAIM_ROLE, String.class));
    }

    /**
     * Extract scopes from token.
     */
    @SuppressWarnings("unchecked")
    public Set<String> extractScopes(String token) {
        List<String> scopes = extractClaim(token, claims -> claims.get(CLAIM_SCOPES, List.class));
        return scopes != null ? new HashSet<>(scopes) : Collections.emptySet();
    }

    /**
     * Extract a specific claim from token.
     */
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    /**
     * Generate token with user details (includes role, scopes, tenantId).
     */
    public String generateToken(UserDetails userDetails) {
        Map<String, Object> extraClaims = buildUserClaims(userDetails);
        return buildToken(extraClaims, userDetails, jwtExpiration);
    }

    /**
     * Generate token with extra claims.
     */
    public String generateToken(Map<String, Object> extraClaims, UserDetails userDetails) {
        Map<String, Object> allClaims = buildUserClaims(userDetails);
        allClaims.putAll(extraClaims);  // Allow overriding
        return buildToken(allClaims, userDetails, jwtExpiration);
    }

    /**
     * Generate refresh token (minimal claims for refresh).
     */
    public String generateRefreshToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        if (userDetails instanceof UserDetailsImpl userDetailsImpl) {
            claims.put(CLAIM_USER_ID, userDetailsImpl.getId().toString());
        }
        return buildToken(claims, userDetails, refreshExpiration);
    }

    /**
     * Build claims map from UserDetails.
     */
    private Map<String, Object> buildUserClaims(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        
        if (userDetails instanceof UserDetailsImpl userDetailsImpl) {
            // Add user ID
            claims.put(CLAIM_USER_ID, userDetailsImpl.getId().toString());
            
            // Add tenant ID
            if (userDetailsImpl.getTenantId() != null) {
                claims.put(CLAIM_TENANT_ID, userDetailsImpl.getTenantId());
            }
            
            // Add role
            String role = userDetailsImpl.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .filter(auth -> auth.startsWith("ROLE_"))
                    .map(auth -> auth.substring(5))  // Remove "ROLE_" prefix
                    .findFirst()
                    .orElse(User.Role.USER.name());
            claims.put(CLAIM_ROLE, role);
            
            // Add scopes
            Set<String> scopes = getEffectiveScopes(userDetailsImpl);
            claims.put(CLAIM_SCOPES, new ArrayList<>(scopes));
        }
        
        return claims;
    }

    /**
     * Get effective scopes for a user (custom scopes or role-based defaults).
     */
    private Set<String> getEffectiveScopes(UserDetailsImpl userDetails) {
        // Check if user has custom scopes
        if (userDetails.getCustomScopes() != null && !userDetails.getCustomScopes().isEmpty()) {
            return Arrays.stream(userDetails.getCustomScopes().split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toSet());
        }
        
        // Fall back to role-based permissions
        String role = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(auth -> auth.startsWith("ROLE_"))
                .map(auth -> auth.substring(5))
                .findFirst()
                .orElse(User.Role.USER.name());
        
        try {
            User.Role userRole = User.Role.valueOf(role);
            return RolePermissions.getScopes(userRole);
        } catch (IllegalArgumentException e) {
            return RolePermissions.getScopes(User.Role.USER);
        }
    }

    private String buildToken(Map<String, Object> extraClaims, UserDetails userDetails, long expiration) {
        return Jwts.builder()
                .claims(extraClaims)
                .subject(userDetails.getUsername())
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSignInKey(), Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Validate token against user details.
     */
    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername())) && !isTokenExpired(token);
    }

    /**
     * Check if user has a specific scope.
     */
    public boolean hasScope(String token, String scope) {
        Set<String> scopes = extractScopes(token);
        return scopes.contains(scope);
    }

    /**
     * Check if user has any of the specified scopes.
     */
    public boolean hasAnyScope(String token, String... requiredScopes) {
        Set<String> scopes = extractScopes(token);
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
    public boolean hasAllScopes(String token, String... requiredScopes) {
        Set<String> scopes = extractScopes(token);
        return scopes.containsAll(Arrays.asList(requiredScopes));
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSignInKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSignInKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
