package com.mouse.bet.domain.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a complete browser identity.
 * Used to mimic specific devices and bypass bot detection.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserAgentProfile {

    private String profileName;   // e.g., "iPhone_15_Safari"
    private String userAgent;     // The full UA string
    private Viewport viewport;    // Width and Height
    private double deviceScaleFactor; // e.g., 2.0 for Retina/Mobile
    private boolean isMobile;     // Whether to enable touch events
    private HeaderProfile headers; // Comprehensive HTTP headers

    /**
     * Helper to check if this is a desktop profile
     */
    public boolean isDesktop() {
        return !isMobile;
    }
}