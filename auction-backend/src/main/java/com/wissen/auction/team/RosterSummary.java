package com.wissen.auction.team;

/** Aggregate values used for eligibility without loading player photos/entities. */
public record RosterSummary(long total, long females, long beginners, long spent) {
    public int remainingPoints() { return Math.toIntExact(100000L - spent); }
}
