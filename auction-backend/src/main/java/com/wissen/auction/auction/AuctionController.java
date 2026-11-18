package com.wissen.auction.auction;

import com.wissen.auction.auction.api.AuctionActionRequest;
import com.wissen.auction.auction.api.AuctionLotRequest;
import com.wissen.auction.auction.api.SellPlayerRequest;
import com.wissen.auction.auction.application.AuctionHistoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/{city}/auction")
@RequiredArgsConstructor
public class AuctionController {
    private final LiveAuctionService auctions;
    private final AuctionHistoryService history;

    @GetMapping("/state")
    public LiveAuctionSnapshot state(@PathVariable String city) {
        return auctions.getState(city);
    }

    @GetMapping("/bids")
    public List<BidLogDTO> history(@PathVariable String city) {
        return history.findByCity(city);
    }

    @GetMapping("/revision")
    public com.wissen.auction.auction.realtime.AuctionNotice revision(@PathVariable String city) {
        return auctions.currentNotice(city);
    }

    @PostMapping("/start")
    public LiveAuctionSnapshot start(@PathVariable String city, @Valid @RequestBody AuctionLotRequest request,
                                     Principal principal) {
        return auctions.start(city, principal.getName(), request.skillLevel(), request.gender(), request.revision());
    }

    @PostMapping("/bid")
    public LiveAuctionSnapshot bid(@PathVariable String city, @Valid @RequestBody BidRequest request,
                                   Principal principal) {
        return auctions.bid(city, principal.getName(), request);
    }

    @PostMapping("/revert")
    public LiveAuctionSnapshot revert(@PathVariable String city, @Valid @RequestBody AuctionActionRequest request,
                                      Principal principal) {
        return auctions.revert(city, principal.getName(), request.playerId(), request.revision());
    }

    @PostMapping("/sell")
    public LiveAuctionSnapshot sell(@PathVariable String city, @Valid @RequestBody SellPlayerRequest request,
                                    Principal principal) {
        return auctions.sell(city, principal.getName(), request.playerId(), request.revision(), request.teamId(), request.finalPrice());
    }

    @PostMapping("/pass")
    public LiveAuctionSnapshot pass(@PathVariable String city, @Valid @RequestBody AuctionActionRequest request,
                                    Principal principal) {
        return auctions.pass(city, principal.getName(), request.playerId(), request.revision());
    }

    @PostMapping("/next")
    public LiveAuctionSnapshot next(@PathVariable String city, @Valid @RequestBody AuctionActionRequest request,
                                    Principal principal) {
        return auctions.next(city, principal.getName(), request.playerId(), request.revision());
    }

    @PostMapping("/pause")
    public LiveAuctionSnapshot pause(@PathVariable String city, Principal principal,
                                      @Valid @RequestBody com.wissen.auction.auction.api.AuctionRevisionRequest request) {
        return auctions.pause(city, principal.getName(), request.revision());
    }

    @PostMapping("/resume")
    public LiveAuctionSnapshot resume(@PathVariable String city, Principal principal,
                                       @Valid @RequestBody com.wissen.auction.auction.api.AuctionRevisionRequest request) {
        return auctions.resume(city, principal.getName(), request.revision());
    }

    @PostMapping("/re-auction")
    public LiveAuctionSnapshot reAuction(@PathVariable String city, Principal principal,
                                        @RequestParam(defaultValue = "All") String skillLevel,
                                        @RequestParam(defaultValue = "All") String gender,
                                        @Valid @RequestBody com.wissen.auction.auction.api.AuctionRevisionRequest request) {
        return auctions.reAuction(city, principal.getName(), skillLevel, gender, request.revision());
    }
}
