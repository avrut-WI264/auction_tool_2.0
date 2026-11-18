package com.wissen.auction.auth;

import java.time.Instant;

import com.wissen.auction.user.AppUser;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "auth_sessions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AuthSession {
    @Id @Column(length = 64)
    private String id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;
    @Column(nullable = false)
    private Instant expiresAt;
    @Column(nullable = false)
    private boolean revoked;
}
