package com.wissen.auction.auction.domain;

import java.util.Locale;
import java.util.Set;

/** Canonical city key shared by storage, HTTP, and WebSocket destinations. */
public record AuctionCity(String value) {
    private static final Set<String> SUPPORTED = Set.of("pune", "mumbai", "bangalore");

    public AuctionCity {
        if (value == null) {
            throw new IllegalArgumentException("City is required.");
        }
        value = value.toLowerCase(Locale.ROOT);
        if (!SUPPORTED.contains(value)) {
            throw new IllegalArgumentException("Unknown city.");
        }
    }
}
