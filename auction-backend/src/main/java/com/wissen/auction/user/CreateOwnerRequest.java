package com.wissen.auction.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateOwnerRequest(@NotBlank @Pattern(regexp = "[A-Za-z0-9_.-]{3,60}") String username,
                                 @NotBlank @Size(min = 12, max = 72) String password,
                                 @NotNull Long teamId) {}
