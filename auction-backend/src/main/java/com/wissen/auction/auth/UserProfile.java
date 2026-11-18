package com.wissen.auction.auth;

import com.wissen.auction.user.AppUser;

/** Public authentication response: credentials and JWTs cannot be serialized here. */
public record UserProfile(String role, String city, String username, Long teamId, String teamName) {
    public static UserProfile from(AppUser user) {
        return new UserProfile(user.getRole(), user.getCity(), user.getUsername(),
                user.getTeam() == null ? null : user.getTeam().getId(),
                user.getTeam() == null ? null : user.getTeam().getTeamName());
    }
}
