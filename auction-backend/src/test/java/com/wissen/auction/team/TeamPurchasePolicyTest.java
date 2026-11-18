package com.wissen.auction.team;

import com.wissen.auction.player.Player;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TeamPurchasePolicyTest {
    private final TeamPurchasePolicy policy = new TeamPurchasePolicy();

    @Test
    void finalRosterSlotMaySatisfyBothFemaleAndBeginnerRequirements() {
        List<Player> roster = new ArrayList<>();
        roster.add(player(Player.Gender.Female, Player.SkillLevel.Beginner));
        for (int i = 0; i < 8; i++) roster.add(player(Player.Gender.Male, Player.SkillLevel.Advanced));
        Team team = Team.builder().location("Pune").players(roster).build();

        assertThatThrownBy(() -> policy.validate(team, new RosterSummary(9, 1, 1, 9000), player(Player.Gender.Male, Player.SkillLevel.Advanced), 1500))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("roster slots");
        assertThatCode(() -> policy.validate(team, new RosterSummary(9, 1, 1, 9000), player(Player.Gender.Female, Player.SkillLevel.Beginner), 1500))
                .doesNotThrowAnyException();
    }

    private Player player(Player.Gender gender, Player.SkillLevel skill) {
        return Player.builder().location("Pune").gender(gender).skillLevel(skill)
                .status(Player.PlayerStatus.SOLD).soldPrice(1000).build();
    }
}
