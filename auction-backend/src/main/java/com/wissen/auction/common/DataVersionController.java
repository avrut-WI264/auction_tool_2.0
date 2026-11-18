package com.wissen.auction.common;

import com.wissen.auction.auction.LiveAuctionRepository;
import com.wissen.auction.auction.domain.AuctionCity;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/{city}")
@RequiredArgsConstructor
public class DataVersionController {
    private final LiveAuctionRepository sessions;

    @GetMapping("/data-version")
    public Map<String, String> version(@PathVariable String city) {
        String key = new AuctionCity(city).value();
        long revision = sessions.findById(key).map(state -> state.getCatalogRevision()).orElse(0L);
        return Map.of("hash", "v" + revision, "timestamp", java.time.Instant.now().toString());
    }
}
