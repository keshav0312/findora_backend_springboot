package com.findora.security;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.findora.model.User;
import com.findora.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Equivalent of the `protect` middleware: reads "Authorization: Bearer ...", verifies it and loads the
 * user. Missing/invalid tokens do not fail here - routes that need auth are rejected by the security
 * chain (using the reason recorded below), while public routes simply see an anonymous caller.
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    static final String AUTH_ERROR_ATTR = "findora.authError";

    private final JwtService jwt;
    private final UserRepository users;

    public JwtAuthFilter(JwtService jwt, UserRepository users) {
        this.jwt = jwt;
        this.users = users;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!"GET".equals(request.getMethod())) {
            return false;
        }
        String path = request.getServletPath();
        return "/".equals(path) || "/health".equals(path) || "/api/health".equals(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            request.setAttribute(AUTH_ERROR_ATTR, "Not authorized, no token");
        } else {
            try {
                AuthUser claims = jwt.verifyAccessToken(header.substring(7).trim());
                Optional<User> user = users.findById(claims.id());
                if (user.isEmpty()) {
                    request.setAttribute(AUTH_ERROR_ATTR, "User no longer exists");
                } else {
                    // Role comes from the database, not the token, so role changes apply immediately.
                    AuthUser principal = new AuthUser(user.get().getId(), user.get().getRole());
                    var auth = new UsernamePasswordAuthenticationToken(
                            principal, null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + principal.role().toUpperCase())));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            } catch (JWTVerificationException e) {
                request.setAttribute(AUTH_ERROR_ATTR, "Not authorized, invalid token");
            }
        }
        chain.doFilter(request, response);
    }
}
