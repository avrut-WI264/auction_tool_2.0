package com.wissen.auction.auction.realtime;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Table(name = "auction_events", uniqueConstraints = @UniqueConstraint(columnNames = {"city", "revision"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AuctionEventRecord {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String city;
    @Column(nullable = false)
    private long revision;
    @Column(nullable = false, columnDefinition = "text")
    private String payload;
    @Column(nullable = false)
    private Instant createdAt;
}
