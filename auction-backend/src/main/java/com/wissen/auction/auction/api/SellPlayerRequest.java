package com.wissen.auction.auction.api;

import jakarta.validation.constraints.NotNull;

public record SellPlayerRequest(@NotNull Long playerId, @NotNull Long revision,
                                @NotNull Long teamId, @NotNull Integer finalPrice) {}
