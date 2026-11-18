package com.wissen.auction.auction.application;

import com.wissen.auction.auction.LiveAuction;
import com.wissen.auction.auction.domain.AuctionLot;
import com.wissen.auction.player.Player;
import com.wissen.auction.player.PlayerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
@RequiredArgsConstructor
public class AuctionQueue {
    private final PlayerRepository players;

    public List<com.wissen.auction.player.AuctionQueueView> find(LiveAuction state) {
        AuctionLot lot = new AuctionLot(state.getSkillLevel(), state.getGender());
        return players.findAuctionQueueSlim(state.getCity()).stream()
                .filter(player -> lot.includes(player.getSkillLevel(), player.getGender())).toList();
    }
}
