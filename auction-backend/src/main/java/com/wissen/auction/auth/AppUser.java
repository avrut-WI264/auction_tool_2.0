package com.wissen.auction.auth;

import jakarta.persistence.*;
import lombok.*;

/**
 * Application user – stores credentials and city/role binding.
 * Seeded via DataInitializer on first run.
 */
@Entity
@Table(name = "app_users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String password;       // BCrypt-hashed

    /** 'admin', 'owner', or 'guest' */
    @Column(nullable = false)
    private String role;

    /** 'Pune' | 'Mumbai' | 'Bangalore' */
    @Column(nullable = false)
    private String city;

    @Builder.Default
    @Column(nullable = false)
    private boolean enabled = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private com.wissen.auction.team.Team team;
}
