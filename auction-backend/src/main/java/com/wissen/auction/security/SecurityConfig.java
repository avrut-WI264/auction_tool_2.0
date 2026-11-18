package com.wissen.auction.security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Spring Security configuration:
 * - Stateless JWT sessions
 * - CORS from configured frontend origin
 * - Public: POST /api/auth/login
 * - Admin-only: create/update/delete players and teams, auction control
 * - Guest + Admin: GET endpoints for players, teams, auction state
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final tools.jackson.databind.ObjectMapper mapper;

    @Value("${app.allowed.origins}")
    private String allowedOrigins;
    @Value("${app.cookie.secure:true}")
    private boolean secureCookie;
    @Value("${app.management.token:}")
    private String metricsToken;

    @Bean
    @org.springframework.core.annotation.Order(0)
    public SecurityFilterChain managementChain(HttpSecurity http) throws Exception {
        return http.securityMatcher("/actuator/**")
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(access -> access
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/prometheus").access((authentication, context) -> {
                            String header = context.getRequest().getHeader("Authorization");
                            boolean allowed = !metricsToken.isBlank() && header != null && java.security.MessageDigest.isEqual(
                                    ("Bearer " + metricsToken).getBytes(java.nio.charset.StandardCharsets.UTF_8),
                                    header.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                            return new org.springframework.security.authorization.AuthorizationDecision(allowed);
                        }).anyRequest().denyAll()).build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> {
                var repository = new org.springframework.security.web.csrf.CookieCsrfTokenRepository();
                repository.setCookieCustomizer(cookie -> cookie.httpOnly(true).secure(secureCookie)
                        .sameSite(secureCookie ? "None" : "Lax").path("/"));
                csrf.csrfTokenRepository(repository)
                        .csrfTokenRequestHandler(new org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler());
            })
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Public endpoints (login + logout — logout just clears the cookie, no auth needed)
                .requestMatchers(HttpMethod.POST, "/api/auth/password").authenticated()
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/ws-auction", "/ws-auction/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/*/players/*/private").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers("/actuator/**").denyAll()
                .requestMatchers(HttpMethod.POST, "/api/*/owners").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/*/auction/bid").hasAnyRole("ADMIN", "OWNER")
                .requestMatchers(HttpMethod.GET, "/api/*/players").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/*/players/**").permitAll()  // covers /{id}, /{id}/photo, /queue
                .requestMatchers(HttpMethod.GET, "/api/*/teams").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/*/teams/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/*/auction").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/*/auction/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/*/data-version").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/*/statistics").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/*/statistics/**").permitAll()
                // Admin-only write operations
                .requestMatchers(HttpMethod.POST,   "/api/*/players/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT,    "/api/*/players/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/*/players/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST,   "/api/*/teams/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT,    "/api/*/teams/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/*/teams/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST,   "/api/*/auction/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT,    "/api/*/auction/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, error) -> {
                    response.setStatus(401);
                    response.setContentType("application/json");
                    mapper.writeValue(response.getOutputStream(), java.util.Map.of("message", "Authentication required."));
                })
                .accessDeniedHandler((request, response, error) -> {
                    response.setStatus(403);
                    response.setContentType("application/json");
                    boolean csrf = error instanceof org.springframework.security.web.csrf.CsrfException;
                    mapper.writeValue(response.getOutputStream(), java.util.Map.of("code", csrf ? "CSRF_INVALID" : "FORBIDDEN",
                            "message", csrf ? "Refresh your security token and retry." : "You are not authorized for this operation."));
                }))
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // Support comma-separated list of origins
        List<String> origins = List.of(allowedOrigins.split(","));
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
