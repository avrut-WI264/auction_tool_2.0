package com.wissen.auction.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Intercepts every request, extracts Bearer token, validates it,
 * and sets Spring Security context with username + city.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;
    private final com.wissen.auction.auth.AuthSessionRepository sessions;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        // Prefer HttpOnly cookie; fall back to Authorization header for API clients / local dev
        String token = extractTokenFromCookie(request);
        if (token == null) {
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                token = authHeader.substring(7);
            }
        }

        if (token != null) {
            try {
                var claims = jwtUtils.claims(token);
                if (claims.getId() != null) {
                    sessions.findByIdAndRevokedFalseAndExpiresAtAfter(claims.getId(), java.time.Instant.now())
                            .filter(session -> session.getUser().isEnabled())
                            .filter(session -> session.getUser().getUsername().equals(claims.getSubject()))
                            .ifPresent(session -> {
                                var user = session.getUser();
                                request.setAttribute("jwtCity", user.getCity());
                                request.setAttribute("jwtRole", user.getRole());
                                request.setAttribute("jwtUsername", user.getUsername());
                                request.setAttribute("jwtSessionId", session.getId());
                                var auth = new UsernamePasswordAuthenticationToken(user.getUsername(), null,
                                        List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().toUpperCase(java.util.Locale.ROOT))));
                                SecurityContextHolder.getContext().setAuthentication(auth);
                            });
                }
            } catch (io.jsonwebtoken.JwtException | IllegalArgumentException invalidToken) {
                // Invalid/expired tokens remain anonymous; security returns 401 for protected routes.
            }
        }

        filterChain.doFilter(request, response);
    }

    private String extractTokenFromCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        return Arrays.stream(request.getCookies())
                .filter(c -> "wbpl_jwt".equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }
}
