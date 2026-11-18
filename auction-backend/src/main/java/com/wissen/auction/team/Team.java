package com.wissen.auction.team;

import com.wissen.auction.player.Player;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**
 * Represents a franchise team in the auction.
 * Each team belongs to a city. Tracks purse balance, player roster, and compliance metrics.
 */
@Entity
@Table(name = "teams", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"team_name", "location"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Team {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "team_name", nullable = false)
    @NotBlank
    private String teamName;

    @Column(name = "owner_name", nullable = false)
    @NotBlank
    private String ownerName;

    @Column(name = "theme_color")
    private String themeColor;

    /** City this team belongs to */
    @Column(nullable = false)
    private String location;

    /** Available auction budget */
    @Column(name = "purse_remaining", nullable = false)
    @Builder.Default
    private Integer purseRemaining = 100000;

    @Column(name = "logo_url", columnDefinition = "TEXT")
    private String logoUrl;

    @Column(name = "logo_svg", columnDefinition = "TEXT")
    private String logoSvg;

    /**
     * Players owned by this team.
     * Mapped via Player.soldTeam foreign key.
     */
    @OneToMany(mappedBy = "soldTeam", fetch = FetchType.LAZY)
    @Builder.Default
    private List<Player> players = new ArrayList<>();

    @org.hibernate.annotations.UpdateTimestamp
    @Column(name = "updated_at")
    private java.time.LocalDateTime updatedAt;

    public Integer getPurseRemaining() {
        if (this.players == null || !org.hibernate.Hibernate.isInitialized(this.players)) {
            return this.purseRemaining;
        }
        // Do not hide iteration/data errors by returning a possibly stale stored
        // balance. Collection traversal failures must abort the transaction.
        int spent = this.players.stream()
                .filter(p -> p.getStatus() == Player.PlayerStatus.SOLD)
                .mapToInt(p -> p.getSoldPrice() != null ? p.getSoldPrice() : 0)
                .sum();
        return 100000 - spent;
    }

    /**
     * Mutate the managed roster through its iterator, preserving ORM tracking.
     * Ordinary list iterators are fail-fast; Iterator.remove() is the supported
     * way to remove entries while traversing. This entity is transaction-confined,
     * not a shared cross-thread collection.
     */
    public void removePlayerFromRoster(Long playerId) {
        Objects.requireNonNull(playerId, "playerId");
        if (players == null) return;
        Iterator<Player> iterator = players.iterator();
        while (iterator.hasNext()) {
            if (playerId.equals(iterator.next().getId())) iterator.remove();
        }
    }

    public Integer getRawPurseRemaining() {
        return this.purseRemaining;
    }

}
