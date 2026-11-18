package com.wissen.auction.team;

import com.wissen.auction.player.Player;
import org.springframework.stereotype.Component;
import java.util.List;

/** One eligibility policy for both accepting bids and settling sales. */
@Component
public class TeamPurchasePolicy {
    private static final int TEAM_SIZE_LIMIT = 10;
    private static final int MIN_FEMALES = 2;
    private static final int MIN_BEGINNERS = 2;

    public void validate(Team team, RosterSummary roster, Player player, int amount) {
        if (!team.getLocation().equalsIgnoreCase(player.getLocation())) {
            throw new SecurityException("Team belongs to another city.");
        }
        long rosterSize = roster.total();
        if (roster.remainingPoints() < amount) {
            throw new IllegalStateException("Insufficient team purse.");
        }
        if (rosterSize >= TEAM_SIZE_LIMIT) {
            throw new IllegalStateException("Team roster is full.");
        }

        long females = roster.females()
                + (player.getGender() == Player.Gender.Female ? 1 : 0);
        long beginners = roster.beginners()
                + (player.getSkillLevel() == Player.SkillLevel.Beginner ? 1 : 0);
        long requiredSlots = Math.max(Math.max(0, MIN_FEMALES - females), Math.max(0, MIN_BEGINNERS - beginners));
        if (TEAM_SIZE_LIMIT - rosterSize - 1 < requiredSlots) {
            throw new IllegalStateException("This purchase would leave too few roster slots to meet female/beginner requirements.");
        }
    }
}
