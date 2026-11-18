package com.wissen.auction.auth;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.Optional;

public interface AuthSessionRepository extends JpaRepository<AuthSession, String> {
    @EntityGraph(attributePaths = "user")
    Optional<AuthSession> findByIdAndRevokedFalseAndExpiresAtAfter(String id, Instant now);

    @Modifying
    @Query("update AuthSession s set s.revoked = true where s.id = :id")
    void revoke(@Param("id") String id);

    @Modifying
    @Query("update AuthSession s set s.revoked = true where s.user.id = :userId")
    void revokeAll(@Param("userId") Long userId);
}
