package com.riotwolfie.combatlog.trust;

import com.riotwolfie.combatlog.CombatLogPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class TrustManager {

    private final CombatLogPlugin plugin;
    private final File file;
    private final Map<UUID, Set<UUID>> trustedMap = new ConcurrentHashMap<>();

    public TrustManager(CombatLogPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "trust_data.yml");
        load();
    }

    public boolean isTrusted(UUID attackerUuid, UUID victimUuid) {
        Set<UUID> trusted = trustedMap.get(attackerUuid);
        return trusted != null && trusted.contains(victimUuid);
    }

    public boolean addTrust(UUID playerUuid, UUID targetUuid) {
        Set<UUID> set = trustedMap.computeIfAbsent(playerUuid, k -> ConcurrentHashMap.newKeySet());
        boolean added = set.add(targetUuid);
        if (added) {
            saveAsync();
        }
        return added;
    }

    public boolean removeTrust(UUID playerUuid, UUID targetUuid) {
        Set<UUID> set = trustedMap.get(playerUuid);
        if (set == null) {
            return false;
        }
        boolean removed = set.remove(targetUuid);
        if (removed) {
            if (set.isEmpty()) {
                trustedMap.remove(playerUuid);
            }
            saveAsync();
        }
        return removed;
    }

    public Set<UUID> getTrusted(UUID playerUuid) {
        Set<UUID> set = trustedMap.get(playerUuid);
        return set != null ? Collections.unmodifiableSet(set) : Collections.emptySet();
    }

    public void load() {
        trustedMap.clear();
        if (!file.exists()) {
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = config.getConfigurationSection("trusts");
        if (section == null) {
            return;
        }

        for (String key : section.getKeys(false)) {
            try {
                UUID playerUuid = UUID.fromString(key);
                List<String> rawTargets = section.getStringList(key);
                Set<UUID> targets = ConcurrentHashMap.newKeySet();
                for (String rawTarget : rawTargets) {
                    try {
                        targets.add(UUID.fromString(rawTarget));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
                if (!targets.isEmpty()) {
                    trustedMap.put(playerUuid, targets);
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public void saveSync() {
        YamlConfiguration config = new YamlConfiguration();
        for (Map.Entry<UUID, Set<UUID>> entry : trustedMap.entrySet()) {
            List<String> list = entry.getValue().stream().map(UUID::toString).toList();
            config.set("trusts." + entry.getKey().toString(), list);
        }
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save trust_data.yml", e);
        }
    }

    private void saveAsync() {
        CompletableFuture.runAsync(this::saveSync);
    }
}
