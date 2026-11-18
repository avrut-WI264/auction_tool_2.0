package com.wissen.auction.auth;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Table(name = "login_attempts")
@Getter @Setter @NoArgsConstructor
public class LoginAttempt {
    @Id @Column(length = 64)
    private String key;
    @Column(nullable = false)
    private Instant windowStart;
    @Column(nullable = false)
    private int attempts;
}
