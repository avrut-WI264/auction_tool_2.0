package com.wissen.auction.auth;

/** Authorization data without credentials or a mutable persistence entity. */
public record AuthenticatedAccount(String username, String city, AccountRole role, Long teamId) {
    public Long biddingTeam(Long requestedTeamId) {
        if (role == AccountRole.OWNER) {
            if (teamId == null) {
                throw new SecurityException("No team is assigned to this account.");
            }
            if (requestedTeamId != null && !requestedTeamId.equals(teamId)) {
                throw new SecurityException("You can bid only for your assigned team.");
            }
            return teamId;
        }
        if (role != AccountRole.ADMIN) {
            throw new SecurityException("You cannot bid in this auction.");
        }
        if (requestedTeamId == null) {
            throw new IllegalArgumentException("Select a bidding team.");
        }
        return requestedTeamId;
    }
}
