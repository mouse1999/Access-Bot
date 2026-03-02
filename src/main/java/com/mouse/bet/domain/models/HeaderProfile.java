package com.mouse.bet.domain.models;

import lombok.Builder;
import lombok.Data;
import java.util.Map;

@Data
@Builder
public class HeaderProfile {
    // Standard headers like Accept-Language, etc.
    private Map<String, String> standardHeaders;

    // Modern Google/Edge Client Hints (Sec-CH-UA-Mobile, etc.)
    private Map<String, String> clientHintsHeaders;
}