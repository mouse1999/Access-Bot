package com.mouse.bet.app;

import com.mouse.bet.service.BetProcessingService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BetProcessingInitializer {

    private final BetProcessingService betProcessingService;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationStart() {
        // Recover stuck sets first
        betProcessingService.recoverStuckSets();

    }
}

