package com.wissen.auction.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import java.time.Duration;

@Component
public class AuthCookieFactory {
    private static final String COOKIE_NAME = "wbpl_jwt";
    private final boolean secure;
    private final Duration lifetime;

    public AuthCookieFactory(@Value("${app.cookie.secure:true}") boolean secure,
                             @Value("${app.jwt.expiration-ms}") long expirationMs) {
        this.secure = secure;
        this.lifetime = Duration.ofMillis(expirationMs);
    }

    public ResponseCookie authenticated(String token) {
        return cookie(token, lifetime);
    }

    public ResponseCookie cleared() {
        return cookie("", Duration.ZERO);
    }

    private ResponseCookie cookie(String value, Duration maxAge) {
        return ResponseCookie.from(COOKIE_NAME, value).httpOnly(true).secure(secure)
                .sameSite(secure ? "None" : "Lax").path("/").maxAge(maxAge).build();
    }
}
