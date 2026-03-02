package com.mouse.bet.enums;

import lombok.Getter;

@Getter
public enum MarketType {
    OVER_UNDER("Over/Under"),
    BTTS("GG/NG");

    // Getter to retrieve the human-readable name
    private final String displayName;

    // Constructor
    MarketType(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return this.displayName;
    }
}