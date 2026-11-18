package com.wissen.auction.auction;

import com.wissen.auction.auction.domain.AuctionCity;
import com.wissen.auction.auction.domain.AuctionLot;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.util.UUID;

/** One durable, exclusively locked auction session per city. */
@Entity
@Table(name = "live_auctions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LiveAuction {
    @Id
    private String city;
    private Long activePlayerId;
    private Integer currentBid = 0;
    private Long highestBidderTeamId;
    private String highestBidderTeam;
    private String skillLevel = "All";
    private String gender = "All";
    private boolean started;
    private String auctionRound;
    private long revision;
    @jakarta.persistence.Column(nullable = false)
    private long catalogRevision;

    public static LiveAuction create(AuctionCity city) {
        LiveAuction auction = new LiveAuction();
        auction.city = city.value();
        return auction;
    }

    public void selectLot(AuctionLot lot) {
        skillLevel = lot.skillLevel();
        gender = lot.gender();
        started = true;
    }

    public void activate(Long playerId, int basePrice) {
        activePlayerId = playerId;
        currentBid = basePrice;
        highestBidderTeamId = null;
        highestBidderTeam = null;
        auctionRound = UUID.randomUUID().toString();
    }

    public void requireActivePlayer(Long playerId, Long expectedRevision) {
        if (!started || playerId == null || !playerId.equals(activePlayerId)) {
            throw new IllegalStateException("This player is no longer active. Refresh the auction.");
        }
        requireRevision(expectedRevision);
    }

    public void requireRevision(Long expectedRevision) {
        if (expectedRevision == null || expectedRevision.longValue() != revision) {
            throw new IllegalStateException("The auction changed. Review the latest bid and try again.");
        }
    }

    public void requirePaused() {
        if (started && activePlayerId != null) {
            throw new IllegalStateException("Pause the live auction before editing data or starting another lot.");
        }
    }

    public void pause() { started = false; }

    public void resume() {
        if (activePlayerId == null) throw new IllegalStateException("Select a lot before resuming.");
        started = true;
    }

    public void catalogChanged() { catalogRevision++; }

    public int nextBid(Integer increment) {
        if (increment == null || (increment != 500 && increment != 1000)) {
            throw new IllegalArgumentException("Increment must be 500 or 1000.");
        }
        return Math.addExact(currentBid, increment);
    }

    public void requireChallengingTeam(Long teamId) {
        if (teamId.equals(highestBidderTeamId)) {
            throw new IllegalStateException("Your team already holds the highest bid.");
        }
    }

    public void recordBid(Long teamId, String teamName, int amount) {
        currentBid = amount;
        highestBidderTeamId = teamId;
        highestBidderTeam = teamName;
    }

    public void requireSale(Long teamId, Integer amount, int basePrice) {
        if (teamId == null || amount == null || amount.intValue() != currentBid || amount < basePrice) {
            throw new IllegalArgumentException("Sale must use the current auction amount.");
        }
        if (highestBidderTeamId != null && !highestBidderTeamId.equals(teamId)) {
            throw new IllegalStateException("Sell only to the highest bidding team, or undo the bid first.");
        }
    }

    public void incrementRevision() {
        revision++;
    }
}
