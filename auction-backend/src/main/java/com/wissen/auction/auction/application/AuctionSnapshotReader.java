package com.wissen.auction.auction.application;

import com.wissen.auction.auction.LiveAuctionSnapshot;

/** Read-only port for consumers that must not invoke auction commands. */
public interface AuctionSnapshotReader {
    LiveAuctionSnapshot getState(String city);
}
