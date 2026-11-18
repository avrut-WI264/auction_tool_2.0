package com.wissen.auction.auction.api;

import jakarta.validation.constraints.NotNull;
public record AuctionRevisionRequest(@NotNull Long revision) {}
