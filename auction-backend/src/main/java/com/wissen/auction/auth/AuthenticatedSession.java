package com.wissen.auction.auth;

/** Internal login result; HTTP controllers return only the profile. */
public record AuthenticatedSession(UserProfile profile, String token) {
    @Override
    public String toString() {
        return "AuthenticatedSession[profile=" + profile + ", token=REDACTED]";
    }
}
