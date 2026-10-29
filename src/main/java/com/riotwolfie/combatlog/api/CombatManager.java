package com.riotwolfie.combatlog.api;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

public interface CombatManager {

    void tagPlayer(@NotNull Player player, @Nullable Player opponent);

    void untagPlayer(@NotNull UUID playerUuid);

    default void untagPlayer(@NotNull Player player) {
        untagPlayer(player.getUniqueId());
    }

    void endCombatForVictimAndOpponents(@NotNull UUID victimUuid);

    default void endCombatForVictimAndOpponents(@NotNull Player player) {
        endCombatForVictimAndOpponents(player.getUniqueId());
    }

    boolean isInCombat(@NotNull UUID playerUuid);

    default boolean isInCombat(@NotNull Player player) {
        return isInCombat(player.getUniqueId());
    }

    @NotNull
    Optional<CombatTag> getCombatTag(@NotNull UUID playerUuid);

    long getRemainingSeconds(@NotNull UUID playerUuid);
}
