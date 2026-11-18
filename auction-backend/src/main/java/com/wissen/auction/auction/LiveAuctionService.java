package com.wissen.auction.auction;

import com.wissen.auction.auction.application.AuctionQueue;
import com.wissen.auction.auction.application.AuctionSettlementService;
import com.wissen.auction.auction.application.AuctionSnapshotFactory;
import com.wissen.auction.auction.application.AuctionSnapshotReader;
import com.wissen.auction.auction.domain.AuctionCity;
import com.wissen.auction.auction.domain.AuctionEventType;
import com.wissen.auction.auction.domain.AuctionLot;
import com.wissen.auction.auth.AuthenticatedAccount;
import com.wissen.auction.auth.CityAccessService;
import com.wissen.auction.player.Player;
import com.wissen.auction.player.PlayerRepository;
import com.wissen.auction.team.Team;
import com.wissen.auction.team.TeamPurchasePolicy;
import com.wissen.auction.team.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

/** Transaction boundary for auction use cases; rules and transport live elsewhere. */
@Service
@RequiredArgsConstructor
@Transactional
public class LiveAuctionService implements AuctionSnapshotReader, com.wissen.auction.auction.application.AuctionNoticeReader {
    private final LiveAuctionRepository sessions;
    private final PlayerRepository players;
    private final TeamRepository teams;
    private final BidLogRepository bids;
    private final CityAccessService access;
    private final TeamPurchasePolicy purchasePolicy;
    private final AuctionQueue queue;
    private final AuctionSnapshotFactory snapshots;
    private final AuctionSettlementService settlement;
    private final com.wissen.auction.auction.realtime.AuctionEventLog events;
    private final com.wissen.auction.auction.application.AuctionLocks locks;

    @Override
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public LiveAuctionSnapshot getState(String city) {
        AuctionCity key = new AuctionCity(city);
        LiveAuction state = sessions.findById(key.value()).orElseGet(() -> LiveAuction.create(key));
        return snapshots.create(state, AuctionEventType.SNAPSHOT);
    }

    @Override @Transactional(readOnly = true)
    public com.wissen.auction.auction.realtime.AuctionNotice currentNotice(String city) {
        AuctionCity key = new AuctionCity(city);
        return com.wissen.auction.auction.realtime.AuctionNotice.from(
                sessions.findById(key.value()).orElseGet(() -> LiveAuction.create(key)), AuctionEventType.SNAPSHOT);
    }

    public LiveAuctionSnapshot start(String city, String username, String skill, String gender, Long revision) {
        access.requireAdministrator(username, city);
        AuctionLot lot = new AuctionLot(skill, gender);
        LiveAuction state = lock(city);
        state.requireRevision(revision);
        state.requirePaused();
        state.selectLot(lot);
        activateFirst(state);
        return changed(state, AuctionEventType.STATE);
    }

    public LiveAuctionSnapshot bid(String city, String username, BidRequest input) {
        AuthenticatedAccount account = access.requireBidder(username, city);
        LiveAuction state = lock(city);
        Player player = active(state, input.getPlayerId(), input.getRevision());
        int amount = state.nextBid(input.getIncrement());
        Long teamId = account.biddingTeam(input.getTeamId());
        Team team = teams.findById(teamId).orElseThrow(() -> new IllegalArgumentException("Team not found."));
        state.requireChallengingTeam(teamId);
        purchasePolicy.validate(team, players.summarizeRoster(teamId), player, amount);

        bids.save(BidLog.builder().player(player).teamName(team.getTeamName()).teamId(teamId)
                .bidAmount(amount).location(state.getCity()).auctionRound(state.getAuctionRound()).build());
        state.recordBid(teamId, team.getTeamName(), amount);
        return changed(state, AuctionEventType.BID);
    }

    public LiveAuctionSnapshot revert(String city, String username, Long playerId, Long revision) {
        access.requireAdministrator(username, city);
        LiveAuction state = lock(city);
        Player player = active(state, playerId, revision);
        List<BidLog> history = bids.findByPlayerAndAuctionRoundOrderByIdDesc(player, state.getAuctionRound());
        if (history.isEmpty()) {
            throw new IllegalStateException("No bid to undo.");
        }
        bids.delete(history.get(0));
        BidLog previous = history.size() > 1 ? history.get(1) : null;
        state.recordBid(previous == null ? null : previous.getTeamId(),
                previous == null ? null : previous.getTeamName(),
                previous == null ? player.getBasePrice() : previous.getBidAmount());
        return changed(state, AuctionEventType.BID);
    }

    public LiveAuctionSnapshot sell(String city, String username, Long playerId, Long revision, Long teamId, Integer price) {
        access.requireAdministrator(username, city);
        LiveAuction state = lock(city);
        Player player = active(state, playerId, revision);
        state.requireSale(teamId, price, player.getBasePrice());
        int position = positionBeforeChange(state, playerId);
        settlement.sell(player, teamId, price);
        activateAt(state, position);
        return changed(state, AuctionEventType.STATE);
    }

    public LiveAuctionSnapshot pass(String city, String username, Long playerId, Long revision) {
        access.requireAdministrator(username, city);
        LiveAuction state = lock(city);
        Player player = active(state, playerId, revision);
        int position = positionBeforeChange(state, playerId);
        settlement.pass(player);
        activateAt(state, position);
        return changed(state, AuctionEventType.STATE);
    }

    public LiveAuctionSnapshot next(String city, String username, Long playerId, Long revision) {
        access.requireAdministrator(username, city);
        LiveAuction state = lock(city);
        active(state, playerId, revision);
        activateAt(state, positionBeforeChange(state, playerId) + 1);
        return changed(state, AuctionEventType.STATE);
    }

    public LiveAuctionSnapshot reAuction(String city, String username, String skill, String gender, Long revision) {
        access.requireAdministrator(username, city);
        AuctionLot lot = new AuctionLot(skill, gender);
        LiveAuction state = lock(city);
        state.requireRevision(revision);
        state.requirePaused();
        settlement.resetPassed(state.getCity(), lot);
        state.selectLot(lot);
        activateFirst(state);
        return changed(state, AuctionEventType.STATE);
    }

    public LiveAuctionSnapshot pause(String city, String username, Long revision) {
        access.requireAdministrator(username, city);
        LiveAuction state = lock(city);
        state.requireRevision(revision);
        state.pause();
        return changed(state, AuctionEventType.STATE);
    }

    public LiveAuctionSnapshot resume(String city, String username, Long revision) {
        access.requireAdministrator(username, city);
        LiveAuction state = lock(city);
        state.requireRevision(revision);
        activeForResume(state);
        state.resume();
        return changed(state, AuctionEventType.STATE);
    }

    private void activeForResume(LiveAuction state) {
        if (state.getActivePlayerId() == null || queue.find(state).stream().noneMatch(player -> player.getId().equals(state.getActivePlayerId()))) {
            throw new IllegalStateException("The selected player changed. Start a new lot.");
        }
    }

    private LiveAuction lock(String city) {
        return locks.acquire(city);
    }

    private Player active(LiveAuction state, Long playerId, Long revision) {
        state.requireActivePlayer(playerId, revision);
        Player player = players.findByIdForUpdate(playerId)
                .orElseThrow(() -> new IllegalArgumentException("Player not found."));
        if (!player.getLocation().equalsIgnoreCase(state.getCity()) || player.getStatus() != Player.PlayerStatus.UNSOLD) {
            throw new IllegalStateException("Player is unavailable for bidding.");
        }
        return player;
    }

    private int positionBeforeChange(LiveAuction state, Long playerId) {
        // Snapshot IDs before managed entities change during sale/pass.
        List<Long> playerIds = queue.find(state).stream().map(com.wissen.auction.player.AuctionQueueView::getId).toList();
        int position = playerIds.indexOf(playerId);
        if (position < 0) {
            throw new IllegalStateException("The active player is no longer in this lot.");
        }
        return position;
    }

    private void activateFirst(LiveAuction state) {
        activateAt(state, 0);
    }

    private void activateAt(LiveAuction state, int position) {
        List<com.wissen.auction.player.AuctionQueueView> remaining = queue.find(state);
        com.wissen.auction.player.AuctionQueueView player = position < remaining.size() ? remaining.get(position) : null;
        state.activate(player == null ? null : player.getId(), player == null ? 0 : player.getBasePrice());
    }

    private LiveAuctionSnapshot changed(LiveAuction state, AuctionEventType eventType) {
        state.incrementRevision();
        if (eventType == AuctionEventType.STATE) state.catalogChanged();
        sessions.save(state);
        LiveAuctionSnapshot snapshot = snapshots.create(state, eventType);
        events.append(state, eventType);
        return snapshot;
    }
}
