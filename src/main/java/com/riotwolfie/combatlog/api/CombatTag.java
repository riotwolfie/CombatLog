package com.riotwolfie.combatlog.api;

import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

public record CombatTag(
        UUID playerUuid,
        @Nullable UUID opponentUuid,
        Instant startTime,
        Instant expireTime
) {

    public boolean isExpired() {
        return Instant.now().isAfter(expireTime);
    }

    public long getRemainingSeconds() {
        long remaining = expireTime.getEpochSecond() - Instant.now().getEpochSecond();
        return Math.max(0, remaining);
    }
}
