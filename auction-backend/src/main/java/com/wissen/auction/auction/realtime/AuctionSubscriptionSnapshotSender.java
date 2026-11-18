package com.wissen.auction.auction.realtime;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.wissen.auction.auction.application.AuctionNoticeReader;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;

/** Outbound adapter; lazy channel injection breaks the broker's configuration cycle. */
@Component
public class AuctionSubscriptionSnapshotSender {
    private final AuctionNoticeReader snapshots;
    private final ObjectMapper mapper;
    private final MessageChannel outbound;

    public AuctionSubscriptionSnapshotSender(AuctionNoticeReader snapshots, ObjectMapper mapper,
            @Lazy @Qualifier("clientOutboundChannel") MessageChannel outbound) {
        this.snapshots = snapshots;
        this.mapper = mapper;
        this.outbound = outbound;
    }

    public void send(String sessionId, String subscriptionId, String destination) {
        var snapshot = snapshots.currentNotice(AuctionDestinations.city(destination));
        SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        headers.setSessionId(sessionId);
        headers.setSubscriptionId(subscriptionId);
        headers.setDestination(destination);
        headers.setContentType(MimeTypeUtils.APPLICATION_JSON);
        try {
            outbound.send(MessageBuilder.createMessage(mapper.writeValueAsBytes(snapshot), headers.getMessageHeaders()));
        } catch (JacksonException exception) {
            throw new MessagingException("Could not serialize the initial auction state.", exception);
        }
    }
}
