package com.wissen.auction.auction.realtime;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ExecutorChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuctionSubscriptionInterceptor implements ExecutorChannelInterceptor {
    private final AuctionSubscriptionSnapshotSender snapshots;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor headers = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (headers == null) {
            return message;
        }
        if (headers.getCommand() == StompCommand.SEND) {
            throw new MessagingException("Place bids through the authenticated REST API.");
        }
        if (headers.getCommand() == StompCommand.SUBSCRIBE) {
            AuctionDestinations.city(headers.getDestination());
        }
        return message;
    }

    @Override
    public void afterMessageHandled(Message<?> message, MessageChannel channel, MessageHandler handler, Exception error) {
        StompHeaderAccessor headers = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (error != null || !(handler instanceof SimpleBrokerMessageHandler) || headers == null
                || headers.getCommand() != StompCommand.SUBSCRIBE) {
            return;
        }
        // Send only after registration in the broker, closing the reconnect gap.
        snapshots.send(headers.getSessionId(), headers.getSubscriptionId(), headers.getDestination());
    }
}
