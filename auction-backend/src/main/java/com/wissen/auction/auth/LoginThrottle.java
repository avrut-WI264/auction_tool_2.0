package com.wissen.auction.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;

/** Database-backed limits are shared by all application instances. */
@Service
public class LoginThrottle {
    private final JdbcTemplate jdbc;
    private final int ipLimit;
    private final int accountLimit;
    private final long windowSeconds;

    public LoginThrottle(JdbcTemplate jdbc, @Value("${app.login.ip-limit:100}") int ipLimit,
                         @Value("${app.login.account-limit:10}") int accountLimit,
                         @Value("${app.login.window-seconds:300}") long windowSeconds) {
        this.jdbc = jdbc;
        this.ipLimit = ipLimit;
        this.accountLimit = accountLimit;
        this.windowSeconds = windowSeconds;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = TooManyLoginAttempts.class)
    public void check(String username, String remoteAddress) {
        if (increment("ip:" + remoteAddress) > ipLimit ||
                increment("account:" + username.toLowerCase(Locale.ROOT)) > accountLimit) {
            throw new TooManyLoginAttempts(windowSeconds);
        }
    }

    private int increment(String value) {
        Instant now = Instant.now();
        return jdbc.queryForObject("""
                INSERT INTO login_attempts(key, window_start, attempts) VALUES (?, ?, 1)
                ON CONFLICT (key) DO UPDATE SET
                  attempts = CASE WHEN login_attempts.window_start < ? THEN 1 ELSE login_attempts.attempts + 1 END,
                  window_start = CASE WHEN login_attempts.window_start < ? THEN EXCLUDED.window_start ELSE login_attempts.window_start END
                RETURNING attempts
                """, Integer.class, hash(value), Timestamp.from(now), Timestamp.from(now.minusSeconds(windowSeconds)),
                Timestamp.from(now.minusSeconds(windowSeconds)));
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public static class TooManyLoginAttempts extends RuntimeException {
        private final long retryAfter;
        public TooManyLoginAttempts(long retryAfter) {
            super("Too many login attempts. Please try again later.");
            this.retryAfter = retryAfter;
        }
        public long retryAfter() { return retryAfter; }
    }
}
