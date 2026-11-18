package com.wissen.auction.auction.application;

import com.wissen.auction.auction.LiveAuction;
import com.wissen.auction.auction.LiveAuctionSnapshot;
import com.wissen.auction.auction.domain.AuctionEventType;
import com.wissen.auction.player.Player;
import com.wissen.auction.player.PlayerDTO;
import com.wissen.auction.player.PlayerRepository;
import com.wissen.auction.team.TeamService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Maps transaction-owned entities into detached outbound snapshots. */
@Component
@RequiredArgsConstructor
public class AuctionSnapshotFactory {
    private final PlayerRepository players;
    private final TeamService teams;
    private final AuctionQueue queue;

    public LiveAuctionSnapshot create(LiveAuction state, AuctionEventType eventType) {
        PlayerDTO activePlayer = state.getActivePlayerId() == null ? null : players.findPublicAuctionPlayer(state.getActivePlayerId())
                .filter(player -> player.getStatus() == Player.PlayerStatus.UNSOLD)
                .map(player -> com.wissen.auction.player.PublicPlayers.view(player, state.getCity())).orElse(null);
        return new LiveAuctionSnapshot(state.getCity(), state.getRevision(), state.isStarted(),
                state.getSkillLevel(), state.getGender(), activePlayer, state.getCurrentBid(),
                state.getHighestBidderTeamId(), state.getHighestBidderTeam(),
                eventType == AuctionEventType.BID ? null : queue.find(state).stream()
                        .map(player -> com.wissen.auction.player.PublicPlayers.view(player, state.getCity())).toList(),
                eventType == AuctionEventType.BID ? null : teams.getTeamsByCity(state.getCity(), null), eventType);
    }
}
