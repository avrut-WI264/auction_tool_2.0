package com.wissen.auction.auction.realtime;

import com.wissen.auction.auction.LiveAuction;
import com.wissen.auction.auction.domain.AuctionEventType;

/** Small wire event; no photos, contact details, full queue, or rosters. */
public record AuctionNotice(String location, long revision, AuctionEventType eventType, boolean started,
                            Long activePlayerId, int currentBid, Long highestBidderTeamId, String highestBidderTeam) {
    public static AuctionNotice from(LiveAuction state, AuctionEventType type) {
        return new AuctionNotice(state.getCity(), state.getRevision(), type, state.isStarted(), state.getActivePlayerId(),
                state.getCurrentBid(), state.getHighestBidderTeamId(), state.getHighestBidderTeam());
    }
}
