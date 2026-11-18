package com.wissen.auction.auction.realtime;

import tools.jackson.databind.ObjectMapper;
import com.wissen.auction.auction.LiveAuctionRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Each node independently tails committed events and feeds its local sockets. */
@Component @RequiredArgsConstructor @Slf4j
public class AuctionEventDispatcher {
    private final AuctionEventRepository events;
    private final LiveAuctionRepository sessions;
    private final SimpMessagingTemplate messaging;
    private final ObjectMapper mapper;
    private final io.micrometer.core.instrument.MeterRegistry metrics;
    private final Map<String, Long> cursors = new ConcurrentHashMap<>();
    private final Map<String, Long> lastError = new ConcurrentHashMap<>();

    @PostConstruct
    public void initialize() {
        // Before this node accepts subscriptions; new sockets receive a full snapshot.
        sessions.findAll().forEach(state -> cursors.put(state.getCity(), state.getRevision()));
    }

    @Scheduled(fixedDelayString = "${app.events.poll-ms:200}", scheduler = "auctionEventScheduler")
    public synchronized void dispatch() {
        for (String city : java.util.List.of("pune", "mumbai", "bangalore")) {
            try {
                for (AuctionEventRecord event : events.findByCityAndRevisionGreaterThanOrderByRevisionAsc(
                        city, cursors.getOrDefault(city, 0L), PageRequest.of(0, 100))) {
                    messaging.convertAndSend(AuctionDestinations.topic(city), mapper.readValue(event.getPayload(), AuctionNotice.class));
                    cursors.put(city, event.getRevision());
                    metrics.counter("auction.events.enqueued", "city", city).increment();
                }
            } catch (Exception exception) {
                // Keep the cursor at the last successful enqueue; retry next cycle.
                metrics.counter("auction.events.dispatch.errors", "city", city).increment();
                long now = System.currentTimeMillis();
                if (now - lastError.getOrDefault(city, 0L) > 30_000) {
                    log.error("Auction event dispatch failed for city {}", city, exception);
                    lastError.put(city, now);
                }
            }
        }
    }

}
