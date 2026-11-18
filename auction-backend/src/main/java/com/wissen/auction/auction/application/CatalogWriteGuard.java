package com.wissen.auction.auction.application;

import com.wissen.auction.auction.domain.AuctionEventType;
import com.wissen.auction.auction.realtime.AuctionEventLog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component @RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class CatalogWriteGuard {
    private final AuctionLocks locks;
    private final AuctionEventLog events;

    public void beforeChange(String city) {
        var state = locks.acquire(city);
        state.requirePaused();
        // Editing a paused catalog invalidates its old player/price selection.
        state.activate(null, 0);
        state.pause();
        state.catalogChanged();
        state.incrementRevision();
        events.append(state, AuctionEventType.STATE);
    }
}
