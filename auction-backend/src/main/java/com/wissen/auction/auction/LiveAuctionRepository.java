package com.wissen.auction.auction;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface LiveAuctionRepository extends JpaRepository<LiveAuction, String> {
    @Modifying
    @Query(value = "INSERT INTO live_auctions (city, current_bid, skill_level, gender, started, revision, catalog_revision) VALUES (:city, 0, 'All', 'All', false, 0, 0) ON CONFLICT (city) DO NOTHING", nativeQuery = true)
    void initialize(@Param("city") String city);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from LiveAuction a where a.city = :city")
    Optional<LiveAuction> lock(@Param("city") String city);
}
