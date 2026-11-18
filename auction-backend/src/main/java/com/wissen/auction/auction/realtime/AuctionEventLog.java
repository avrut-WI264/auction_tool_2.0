package com.wissen.auction.auction.realtime;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.wissen.auction.auction.LiveAuction;
import com.wissen.auction.auction.domain.AuctionEventType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;

/** The durable event is committed atomically with the auction state. */
@Component @RequiredArgsConstructor
public class AuctionEventLog {
    private final AuctionEventRepository events;
    private final ObjectMapper mapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public void append(LiveAuction state, AuctionEventType type) {
        try {
            events.save(AuctionEventRecord.builder().city(state.getCity()).revision(state.getRevision())
                    .payload(mapper.writeValueAsString(AuctionNotice.from(state, type))).createdAt(Instant.now()).build());
        } catch (JacksonException exception) {
            throw new IllegalStateException("Cannot record the auction event.", exception);
        }
    }
}
