package com.wissen.auction.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.security.Principal;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authentication;
    private final AuthCookieFactory cookies;
    private final LoginThrottle throttle;

    public record LoginRequest(@NotBlank @jakarta.validation.constraints.Size(max = 60) String username,
                               @NotBlank @jakarta.validation.constraints.Size(max = 72) String password) {}

    @PostMapping("/login")
    public ResponseEntity<UserProfile> login(@Valid @RequestBody LoginRequest request, jakarta.servlet.http.HttpServletRequest http) {
        throttle.check(request.username(), http.getRemoteAddr());
        AuthenticatedSession session = authentication.login(request.username(), request.password());
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookies.authenticated(session.token()).toString())
                .body(session.profile());
    }

    @GetMapping("/me")
    public ResponseEntity<UserProfile> me(Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(authentication.profile(principal.getName()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(jakarta.servlet.http.HttpServletRequest request) {
        authentication.logout((String) request.getAttribute("jwtSessionId"));
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookies.cleared().toString()).build();
    }

    @GetMapping("/csrf")
    public java.util.Map<String, String> csrf(org.springframework.security.web.csrf.CsrfToken token) {
        return java.util.Map.of("token", token.getToken(), "headerName", token.getHeaderName());
    }

    public record PasswordChange(@NotBlank String currentPassword,
            @NotBlank @jakarta.validation.constraints.Size(min = 12, max = 72) String newPassword) {}

    @PostMapping("/password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody PasswordChange request, Principal principal) {
        authentication.changePassword(principal.getName(), request.currentPassword(), request.newPassword());
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookies.cleared().toString()).build();
    }
}
