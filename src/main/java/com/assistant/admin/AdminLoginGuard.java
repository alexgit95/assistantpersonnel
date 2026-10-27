package com.assistant.admin;

import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class AdminLoginGuard {

    private final AdminProperties properties;
    private final Clock clock;
    private int failures;
    private Instant lockedUntil;

    public AdminLoginGuard(AdminProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public synchronized boolean isLocked() {
        if (lockedUntil == null) {
            return false;
        }
        if (clock.instant().isBefore(lockedUntil)) {
            return true;
        }
        lockedUntil = null;
        failures = 0;
        return false;
    }

    public synchronized void recordFailure() {
        if (isLocked()) {
            return;
        }
        failures++;
        if (failures >= properties.maxFailedAttempts()) {
            lockedUntil = clock.instant().plusSeconds(properties.lockoutMinutes() * 60L);
            failures = 0;
        }
    }

    public synchronized void recordSuccess() {
        failures = 0;
        lockedUntil = null;
    }
}
