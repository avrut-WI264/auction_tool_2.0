package com.wissen.auction.ops;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component @RequiredArgsConstructor
public class ExpiredDataCleanup {
    private final JdbcTemplate jdbc;
    @Value("${app.events.retention-days:7}") private long eventDays;

    @Scheduled(fixedDelay = 3_600_000, initialDelay = 3_600_000, scheduler = "auctionEventScheduler")
    @Transactional
    public void cleanup() {
        Instant now = Instant.now();
        jdbc.update("DELETE FROM auth_sessions WHERE expires_at < ?", Timestamp.from(now));
        jdbc.update("DELETE FROM login_attempts WHERE window_start < ?", Timestamp.from(now.minus(1, ChronoUnit.DAYS)));
        jdbc.update("DELETE FROM auction_events WHERE created_at < ?", Timestamp.from(now.minus(Math.max(1, eventDays), ChronoUnit.DAYS)));
    }
}
