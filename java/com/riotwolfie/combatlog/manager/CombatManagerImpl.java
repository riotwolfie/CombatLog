package com.riotwolfie.combatlog.manager;

import com.riotwolfie.combatlog.CombatLogPlugin;
import com.riotwolfie.combatlog.api.CombatManager;
import com.riotwolfie.combatlog.api.CombatTag;
import com.riotwolfie.combatlog.config.ConfigManager;
import com.riotwolfie.combatlog.util.ChatColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CombatManagerImpl implements CombatManager {

    private final CombatLogPlugin plugin;
    private final ConfigManager configManager;
    private final Map<UUID, CombatTag> combatTags = new ConcurrentHashMap<>();
    private BukkitTask tickTask;

    public CombatManagerImpl(CombatLogPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        startCleanupTask();
    }

    private void startCleanupTask() {
        this.tickTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (combatTags.isEmpty()) {
                return;
            }
            for (Map.Entry<UUID, CombatTag> entry : combatTags.entrySet()) {
                CombatTag tag = entry.getValue();
                if (tag.isExpired()) {
                    combatTags.remove(entry.getKey());
                    Player player = Bukkit.getPlayer(entry.getKey());
                    if (player != null && player.isOnline()) {
                        String msg = configManager.getCombatExpiredMessage();
                        if (msg != null && !msg.isBlank()) {
                            player.sendMessage(ChatColorUtil.format(msg));
                        }
                    }
                }
            }
        }, 20L, 20L);
    }

    @Override
    public void tagPlayer(@NotNull Player player, @Nullable Player opponent) {
        UUID playerUuid = player.getUniqueId();
        UUID opponentUuid = opponent != null ? opponent.getUniqueId() : null;
        int durationSeconds = configManager.getCombatDurationSeconds();

        Instant now = Instant.now();
        Instant expireTime = now.plusSeconds(durationSeconds);
        boolean wasInCombat = isInCombat(playerUuid);

        CombatTag tag = new CombatTag(playerUuid, opponentUuid, now, expireTime);
        combatTags.put(playerUuid, tag);

        if (!wasInCombat) {
            String opponentName = opponent != null ? opponent.getName() : "Combat";
            String msg = configManager.getCombatEnterMessage()
                    .replace("%opponent%", opponentName)
                    .replace("%seconds%", String.valueOf(durationSeconds));
            player.sendMessage(ChatColorUtil.format(msg));
        }
    }

    @Override
    public void untagPlayer(@NotNull UUID playerUuid) {
        combatTags.remove(playerUuid);
    }

    @Override
    public void endCombatForVictimAndOpponents(@NotNull UUID victimUuid) {
        CombatTag victimTag = combatTags.remove(victimUuid);
        UUID directOpponent = (victimTag != null) ? victimTag.opponentUuid() : null;

        if (directOpponent != null) {
            combatTags.remove(directOpponent);
            Player oppPlayer = Bukkit.getPlayer(directOpponent);
            if (oppPlayer != null && oppPlayer.isOnline()) {
                String msg = configManager.getCombatExpiredMessage();
                if (msg != null && !msg.isBlank()) {
                    oppPlayer.sendMessage(ChatColorUtil.format(msg));
                }
            }
        }

        for (Map.Entry<UUID, CombatTag> entry : combatTags.entrySet()) {
            if (victimUuid.equals(entry.getValue().opponentUuid())) {
                combatTags.remove(entry.getKey());
                Player player = Bukkit.getPlayer(entry.getKey());
                if (player != null && player.isOnline()) {
                    String msg = configManager.getCombatExpiredMessage();
                    if (msg != null && !msg.isBlank()) {
                        player.sendMessage(ChatColorUtil.format(msg));
                    }
                }
            }
        }
    }

    @Override
    public boolean isInCombat(@NotNull UUID playerUuid) {
        CombatTag tag = combatTags.get(playerUuid);
        if (tag == null) {
            return false;
        }
        if (tag.isExpired()) {
            combatTags.remove(playerUuid);
            return false;
        }
        return true;
    }

    @NotNull
    @Override
    public Optional<CombatTag> getCombatTag(@NotNull UUID playerUuid) {
        CombatTag tag = combatTags.get(playerUuid);
        if (tag != null && tag.isExpired()) {
            combatTags.remove(playerUuid);
            return Optional.empty();
        }
        return Optional.ofNullable(tag);
    }

    @Override
    public long getRemainingSeconds(@NotNull UUID playerUuid) {
        return getCombatTag(playerUuid)
                .map(CombatTag::getRemainingSeconds)
                .orElse(0L);
    }

    public void shutdown() {
        if (tickTask != null && !tickTask.isCancelled()) {
            tickTask.cancel();
        }
        combatTags.clear();
    }
}
