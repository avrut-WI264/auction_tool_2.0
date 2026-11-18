package com.wissen.auction.auction.application;

import com.wissen.auction.auction.BidLogRepository;
import com.wissen.auction.auction.domain.AuctionLot;
import com.wissen.auction.player.Player;
import com.wissen.auction.player.PlayerRepository;
import com.wissen.auction.team.Team;
import com.wissen.auction.team.TeamPurchasePolicy;
import com.wissen.auction.team.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Roster/purse writes participate in the locked auction command transaction. */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class AuctionSettlementService {
    private final PlayerRepository players;
    private final TeamRepository teams;
    private final BidLogRepository bids;
    private final TeamPurchasePolicy purchasePolicy;

    public void sell(Player player, Long teamId, int amount) {
        Team team = teams.findById(teamId).orElseThrow(() -> new IllegalArgumentException("Team not found."));
        var roster = players.summarizeRoster(teamId);
        purchasePolicy.validate(team, roster, player, amount);
        int remainingPurse = roster.remainingPoints() - amount;
        player.setStatus(Player.PlayerStatus.SOLD);
        player.setSoldPrice(amount);
        player.setSoldTeam(team);
        team.setPurseRemaining(remainingPurse);
        players.save(player);
        teams.save(team);
    }

    public void pass(Player player) {
        bids.deleteByPlayer(player);
        player.setStatus(Player.PlayerStatus.PASSED);
        players.save(player);
    }

    public void resetPassed(String city, AuctionLot lot) {
        boolean allSkills = AuctionLot.ALL.equals(lot.skillLevel());
        boolean allGenders = AuctionLot.ALL.equals(lot.gender());
        if (!allSkills && !allGenders) {
            players.bulkResetPassedBySkillAndGender(city, Player.SkillLevel.valueOf(lot.skillLevel()), Player.Gender.valueOf(lot.gender()));
        } else if (!allSkills) {
            players.bulkResetPassedBySkill(city, Player.SkillLevel.valueOf(lot.skillLevel()));
        } else if (!allGenders) {
            players.bulkResetPassedByGender(city, Player.Gender.valueOf(lot.gender()));
        } else {
            players.bulkResetPassed(city);
        }
    }
}
