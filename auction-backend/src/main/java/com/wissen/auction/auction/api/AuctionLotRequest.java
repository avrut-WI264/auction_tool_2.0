package com.wissen.auction.auction.api;

import jakarta.validation.constraints.NotBlank;

public record AuctionLotRequest(@NotBlank String skillLevel, @NotBlank String gender,
                                 @jakarta.validation.constraints.NotNull Long revision) {}
