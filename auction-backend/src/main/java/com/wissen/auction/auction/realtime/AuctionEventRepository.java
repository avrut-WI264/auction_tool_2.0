package com.wissen.auction.auction.realtime;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AuctionEventRepository extends JpaRepository<AuctionEventRecord, Long> {
    List<AuctionEventRecord> findByCityAndRevisionGreaterThanOrderByRevisionAsc(String city, long revision, Pageable page);
}
