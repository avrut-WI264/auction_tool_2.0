package com.wissen.auction.auth;

import java.util.Arrays;

public enum AccountRole {
    ADMIN("admin"), OWNER("owner"), GUEST("guest");

    private final String value;

    AccountRole(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static AccountRole from(String value) {
        return Arrays.stream(values()).filter(role -> role.value.equals(value)).findFirst()
                .orElseThrow(() -> new SecurityException("Unknown account role."));
    }
}
