package com.wissen.auction.auction;

import com.wissen.auction.player.PlayerDTO;
import com.wissen.auction.team.TeamDTO;
import com.wissen.auction.auction.domain.AuctionEventType;
import java.util.List;

public record LiveAuctionSnapshot(String location, long revision, boolean started,
        String skillLevel, String gender, PlayerDTO activePlayer, int currentBid,
        Long highestBidderTeamId, String highestBidderTeam,
        List<PlayerDTO> auctionQueue, List<TeamDTO> cityTeams, AuctionEventType eventType) {
    public LiveAuctionSnapshot {
        // Copy while the transaction owns these lists, before crossing the
        // after-commit/WebSocket boundary. Later source-list edits cannot alter
        // traversal of this revision. DTO elements themselves are not deep-frozen.
        auctionQueue = auctionQueue == null ? null : List.copyOf(auctionQueue);
        cityTeams = cityTeams == null ? null : List.copyOf(cityTeams);
    }
}
