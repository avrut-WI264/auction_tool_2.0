package com.wissen.auction.auction.domain;

import com.wissen.auction.player.Player;
import java.util.Set;

/** Validated lot selection; wire/storage values remain backward compatible. */
public record AuctionLot(String skillLevel, String gender) {
    public static final String ALL = "All";
    private static final Set<String> SKILLS = Set.of(ALL, "Beginner", "Intermediate", "Advanced");
    private static final Set<String> GENDERS = Set.of(ALL, "Female", "Male");

    public AuctionLot {
        if (skillLevel == null || gender == null || !SKILLS.contains(skillLevel) || !GENDERS.contains(gender)) {
            throw new IllegalArgumentException("Invalid lot filters.");
        }
    }

    public boolean includes(Player.SkillLevel skill, Player.Gender playerGender) {
        return (ALL.equals(skillLevel) || skill.name().equals(skillLevel))
                && (ALL.equals(gender) || playerGender.name().equals(gender));
    }
}
