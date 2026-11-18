package com.wissen.auction.auction;

import tools.jackson.databind.ObjectMapper;
import com.wissen.auction.player.PlayerDTO;
import com.wissen.auction.auction.domain.AuctionEventType;
import com.wissen.auction.team.Team;
import com.wissen.auction.team.TeamDTO;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import static org.assertj.core.api.Assertions.*;

class AuctionSnapshotTest {
    @Test
    void delayedSerializationRetainsRevisionQueueRosterAndTotalsAfterSourceListsChange() throws Exception {
        PlayerDTO player = PlayerDTO.builder().id(1L).fullName("Sold player").gender("Female")
                .skillLevel("Beginner").soldPrice(1500).build();
        List<PlayerDTO> roster = new ArrayList<>(List.of(player));
        TeamDTO team = TeamDTO.from(Team.builder().id(10L).teamName("First").build(), roster);
        List<PlayerDTO> queue = new ArrayList<>(List.of(PlayerDTO.builder().id(2L).build()));
        List<TeamDTO> teams = new ArrayList<>(List.of(team));
        LiveAuctionSnapshot snapshot = new LiveAuctionSnapshot("pune", 7, true, "All", "All",
                queue.get(0), 1000, null, null, queue, teams, AuctionEventType.STATE);

        // Simulate producers reusing their working lists before the outbound
        // WebSocket thread gets around to serializing the committed event.
        CompletableFuture.runAsync(() -> {
            roster.clear();
            queue.clear();
            teams.clear();
        }).join();
        var payload = tools.jackson.databind.json.JsonMapper.builder().findAndAddModules().build().readTree(tools.jackson.databind.json.JsonMapper.builder().findAndAddModules().build().writeValueAsBytes(snapshot));
        assertThat(payload.path("revision").asLong()).isEqualTo(7);
        assertThat(payload.path("auctionQueue").size()).isEqualTo(1);
        assertThat(payload.path("cityTeams").size()).isEqualTo(1);
        var serializedTeam = payload.path("cityTeams").get(0);
        assertThat(serializedTeam.path("players").size()).isEqualTo(serializedTeam.path("totalPlayers").asInt()).isEqualTo(1);
        assertThat(serializedTeam.path("purseRemaining").asInt()).isEqualTo(98500);
        assertThatThrownBy(() -> snapshot.auctionQueue().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> team.getPlayers().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
}
