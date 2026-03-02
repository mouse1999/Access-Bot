package com.mouse.bet.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

@Getter
@RequiredArgsConstructor
public enum OutcomeType {
    // Over/Under 1.5 Markets
    OVER_1_5("Over 1.5"),
    UNDER_1_5("Under 1.5"),

    // Over/Under 2.5 Markets
    OVER_2_5("Over 2.5"),
    UNDER_2_5("Under 2.5"),

    // BTTS Markets
    BTTS_YES("GG"),
    BTTS_NO("NG");

    private final String displayName;

    /**
     * Helper to find an enum constant by its display name.
     * Useful when parsing data from an external sports API.
     */
    public static OutcomeType fromDisplayName(String text) {
        return Arrays.stream(OutcomeType.values())
                .filter(outcome -> outcome.displayName.equalsIgnoreCase(text))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown outcome: " + text));
    }

    @Override
    public String toString() {
        return this.displayName;
    }
}
