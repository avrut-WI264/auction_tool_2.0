package com.wissen.auction.auction.api;

import jakarta.validation.constraints.NotNull;

public record AuctionActionRequest(@NotNull Long playerId, @NotNull Long revision) {}
