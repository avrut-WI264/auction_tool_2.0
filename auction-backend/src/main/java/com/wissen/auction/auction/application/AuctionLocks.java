package com.wissen.auction.auction.application;

import com.wissen.auction.auction.LiveAuction;
import com.wissen.auction.auction.LiveAuctionRepository;
import com.wissen.auction.auction.domain.AuctionCity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Every city write takes this lock before player/team row locks. */
@Component @RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class AuctionLocks {
    private final LiveAuctionRepository sessions;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    public LiveAuction acquire(String city) {
        jdbc.execute("SET LOCAL lock_timeout = '3s'");
        jdbc.execute("SET LOCAL statement_timeout = '10s'");
        String key = new AuctionCity(city).value();
        sessions.initialize(key);
        return sessions.lock(key).orElseThrow();
    }
}
