package com.wissen.auction.auction.realtime;

import com.wissen.auction.auction.domain.AuctionCity;
import org.springframework.messaging.MessagingException;

final class AuctionDestinations {
    static final String TOPIC_PREFIX = "/topic/auction/";
    static final String ENDPOINT = "/ws-auction";

    private AuctionDestinations() {}

    static String topic(String city) {
        return TOPIC_PREFIX + new AuctionCity(city).value();
    }

    static String city(String destination) {
        if (destination == null || !destination.startsWith(TOPIC_PREFIX)) {
            throw new MessagingException("Invalid auction subscription.");
        }
        try {
            String key = destination.substring(TOPIC_PREFIX.length());
            AuctionCity city = new AuctionCity(key);
            if (!key.equals(city.value())) {
                throw new IllegalArgumentException("City must be canonical.");
            }
            return city.value();
        } catch (IllegalArgumentException exception) {
            throw new MessagingException("Invalid auction subscription.", exception);
        }
    }
}
