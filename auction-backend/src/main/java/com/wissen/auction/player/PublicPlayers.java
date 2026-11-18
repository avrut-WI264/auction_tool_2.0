package com.wissen.auction.player;

/** Explicit allowlist shared by guest HTTP responses and live broadcasts. */
public final class PublicPlayers {
    private PublicPlayers() {}

    public static PlayerDTO view(AuctionQueueView player, String city) {
        return PlayerDTO.builder().id(player.getId()).fullName(player.getFullName()).gender(player.getGender().name())
                .skillLevel(player.getSkillLevel().name()).basePrice(player.getBasePrice()).status(player.getStatus().name())
                .location(city).build();
    }

    public static PlayerDTO view(PlayerDTO player) {
        return PlayerDTO.builder().id(player.getId()).fullName(player.getFullName())
                .gender(player.getGender()).location(player.getLocation()).skillLevel(player.getSkillLevel())
                .basePrice(player.getBasePrice()).status(player.getStatus()).soldPrice(player.getSoldPrice())
                .soldTeam(player.getSoldTeam()).soldTeamId(player.getSoldTeamId())
                .imageUrlHash(player.getImageUrlHash()).stats(player.getStats()).build();
    }

    public static PlayerDTO view(Player player) {
        // Never materialize a photo or contact details when building a live queue.
        return PlayerDTO.builder().id(player.getId()).fullName(player.getFullName())
                .gender(player.getGender().name()).location(player.getLocation()).skillLevel(player.getSkillLevel().name())
                .basePrice(player.getBasePrice()).status(player.getStatus().name()).soldPrice(player.getSoldPrice())
                .soldTeamId(player.getSoldTeam() == null ? null : player.getSoldTeam().getId()).build();
    }
}
