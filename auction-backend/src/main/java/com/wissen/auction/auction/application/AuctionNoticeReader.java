package com.wissen.auction.auction.application;

import com.wissen.auction.auction.realtime.AuctionNotice;

/** Small synchronization query; does not load queues, rosters, or photos. */
public interface AuctionNoticeReader {
    AuctionNotice currentNotice(String city);
}
