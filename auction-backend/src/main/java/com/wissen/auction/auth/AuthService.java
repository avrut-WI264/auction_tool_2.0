package com.wissen.auction.auth;

import com.wissen.auction.security.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {
    private final AppUserRepository users;
    private final PasswordEncoder passwords;
    private final JwtUtils tokens;
    private final AuthSessionRepository sessions;
    @org.springframework.beans.factory.annotation.Value("${app.jwt.expiration-ms}")
    private long expirationMs;

    @Transactional
    public AuthenticatedSession login(String username, String password) {
        if (password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) throw new InvalidCredentialsException();
        AppUser user = users.findByUsername(username).orElseThrow(InvalidCredentialsException::new);
        if (!user.isEnabled() || !passwords.matches(password, user.getPassword())) {
            throw new InvalidCredentialsException();
        }
        String sessionId = java.util.UUID.randomUUID().toString();
        sessions.save(AuthSession.builder().id(sessionId).user(user)
                .expiresAt(java.time.Instant.now().plusMillis(expirationMs)).build());
        String token = tokens.generateToken(user.getUsername(), user.getRole(), user.getCity(), sessionId);
        return new AuthenticatedSession(UserProfile.from(user), token);
    }

    @Transactional
    public void logout(String sessionId) {
        if (sessionId != null) sessions.revoke(sessionId);
    }

    @Transactional
    public void changePassword(String username, String currentPassword, String newPassword) {
        if (newPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("Password must not exceed 72 UTF-8 bytes.");
        }
        AppUser user = users.findByUsername(username).orElseThrow(InvalidCredentialsException::new);
        if (!passwords.matches(currentPassword, user.getPassword())) throw new InvalidCredentialsException();
        user.setPassword(passwords.encode(newPassword));
        users.save(user);
        sessions.revokeAll(user.getId());
    }

    public UserProfile profile(String username) {
        return UserProfile.from(users.findByUsername(username)
                .orElseThrow(() -> new SecurityException("Account not found.")));
    }
}
