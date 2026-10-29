package com.riotwolfie.combatlog.npc;

import com.destroystokyo.paper.event.player.PlayerUseUnknownEntityEvent;
import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.riotwolfie.combatlog.CombatLogPlugin;
import com.riotwolfie.combatlog.api.CombatManager;
import com.riotwolfie.combatlog.api.CombatTag;
import com.riotwolfie.combatlog.config.ConfigManager;
import com.riotwolfie.combatlog.util.ChatColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CombatNpcManager implements Listener {

    private final CombatLogPlugin plugin;
    private final ConfigManager configManager;
    private final CombatManager combatManager;

    private final Map<UUID, CombatNpc> npcsByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, CombatNpc> npcsByEntityUuid = new ConcurrentHashMap<>();
    private final Map<Integer, CombatNpc> npcsByEntityId = new ConcurrentHashMap<>();
    private final Map<UUID, CombatNpc> npcsByInteractionUuid = new ConcurrentHashMap<>();
    private final Map<UUID, PendingSyncData> pendingSyncByPlayer = new ConcurrentHashMap<>();
    private final Set<UUID> pendingKillOnJoin = ConcurrentHashMap.newKeySet();
    private BukkitTask tickerTask;

    public CombatNpcManager(CombatLogPlugin plugin, ConfigManager configManager, CombatManager combatManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.combatManager = combatManager;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        startTicking();
    }

    private void startTicking() {
        this.tickerTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (npcsByPlayer.isEmpty()) {
                return;
            }
            for (CombatNpc combatNpc : npcsByPlayer.values()) {
                if (combatNpc.isExpired()) {
                    despawnNpc(combatNpc.getPlayerUuid());
                    continue;
                }
                if (combatNpc.getFireTicks() > 0) {
                    combatNpc.setFireTicks(Math.max(0, combatNpc.getFireTicks() - 20));
                    double remaining = combatNpc.getHealth() - 1.0;
                    combatNpc.setHealth(remaining);
                    updateHpOverlay(combatNpc);
                    pendingSyncByPlayer.put(combatNpc.getPlayerUuid(), new PendingSyncData(
                            remaining,
                            combatNpc.getMaxHealth(),
                            combatNpc.getAbsorption(),
                            combatNpc.getFireTicks(),
                            new ArrayList<>(combatNpc.getActiveEffects()),
                            combatNpc.getOpponentUuid(),
                            true
                    ));
                    if (remaining <= 0.0) {
                        handleNpcDeath(combatNpc, null);
                        continue;
                    }
                }
                Object nmsPlayer = combatNpc.getNmsPlayer();
                if (nmsPlayer != null) {
                    Location loc = combatNpc.getLocation();
                    if (loc.getWorld() != null) {
                        for (Player viewer : loc.getWorld().getPlayers()) {
                            if (viewer.isOnline() && viewer.getLocation().distanceSquared(loc) <= 16384.0) {
                                sendNpcToViewer(viewer, nmsPlayer, loc, combatNpc);
                            }
                        }
                    }
                }
            }
        }, 20L, 20L);
    }

    public static Attribute resolveMaxHealthAttribute() {
        try {
            org.bukkit.Registry<Attribute> registry = org.bukkit.Registry.ATTRIBUTE;
            Attribute attr = registry.get(org.bukkit.NamespacedKey.minecraft("max_health"));
            if (attr != null) {
                return attr;
            }
            attr = registry.get(org.bukkit.NamespacedKey.minecraft("generic.max_health"));
            if (attr != null) {
                return attr;
            }
        } catch (Throwable ignored) {
        }
        try {
            return Attribute.valueOf("MAX_HEALTH");
        } catch (Throwable t) {
            try {
                return Attribute.valueOf("GENERIC_MAX_HEALTH");
            } catch (Throwable ignored) {
                return null;
            }
        }
    }

    public static Attribute resolveAttackDamageAttribute() {
        try {
            org.bukkit.Registry<Attribute> registry = org.bukkit.Registry.ATTRIBUTE;
            Attribute attr = registry.get(org.bukkit.NamespacedKey.minecraft("attack_damage"));
            if (attr != null) {
                return attr;
            }
            attr = registry.get(org.bukkit.NamespacedKey.minecraft("generic.attack_damage"));
            if (attr != null) {
                return attr;
            }
        } catch (Throwable ignored) {
        }
        try {
            return Attribute.valueOf("ATTACK_DAMAGE");
        } catch (Throwable t) {
            try {
                return Attribute.valueOf("GENERIC_ATTACK_DAMAGE");
            } catch (Throwable ignored) {
                return null;
            }
        }
    }

    public String formatNpcName(String playerName) {
        String format = configManager.getNpcNameFormat();
        if (format == null || format.isBlank() || format.equalsIgnoreCase("%player%")) {
            return sanitizePlayerName(playerName);
        }
        String formatted = format.replace("%player%", playerName);
        return sanitizePlayerName(formatted);
    }

    public static String sanitizePlayerName(String raw) {
        if (raw == null) {
            return "";
        }
        String cleaned = raw.replaceAll("(?i)\\s*[\\[\\(]?\\bnpc\\b[\\]\\)]?", "");
        cleaned = cleaned.replaceAll("(?i)\\s+npc\\b", "");
        cleaned = cleaned.trim();
        return cleaned.isEmpty() ? raw.trim() : cleaned;
    }

    @Nullable
    public CombatNpc spawnNpc(
            @NotNull UUID playerUuid,
            @NotNull String playerName,
            @NotNull Location location,
            @Nullable PlayerProfile playerProfile,
            @NotNull ItemStack[] storage,
            @NotNull ItemStack[] armor,
            @Nullable ItemStack offHand,
            int exp,
            double health,
            double maxHealth,
            double absorption,
            @Nullable Collection<PotionEffect> effects,
            int fireTicks
    ) {
        return spawnNpc(playerUuid, playerName, location, playerProfile, null, null, null, storage, armor, offHand, exp, health, maxHealth, absorption, effects, fireTicks);
    }

    @Nullable
    public CombatNpc spawnNpc(
            @NotNull UUID playerUuid,
            @NotNull String playerName,
            @NotNull Location location,
            @Nullable PlayerProfile playerProfile,
            @Nullable String skinTexture,
            @Nullable String skinSignature,
            @Nullable CombatTag tag,
            @NotNull ItemStack[] storage,
            @NotNull ItemStack[] armor,
            @Nullable ItemStack offHand,
            int exp,
            double health,
            double maxHealth,
            double absorption,
            @Nullable Collection<PotionEffect> effects,
            int fireTicks
    ) {
        despawnNpc(playerUuid);

        int npcDuration = configManager.getNpcDurationSeconds();
        Instant expireTime = (npcDuration > 0)
                ? Instant.now().plusSeconds(npcDuration)
                : (tag != null ? tag.expireTime() : Instant.now().plusSeconds(15));
        UUID opponentUuid = (tag != null) ? tag.opponentUuid() : null;

        String displayName = formatNpcName(playerName);

        if (location.getWorld() == null) {
            return null;
        }
        if (!location.getChunk().isLoaded()) {
            location.getChunk().load();
        }

        Object nmsPlayer = spawnNmsPlayer(playerUuid, displayName, location, playerProfile, skinTexture, skinSignature, storage, armor, offHand, health, maxHealth, effects, fireTicks);
        if (nmsPlayer == null) {
            return null;
        }

        int entityId = 0;
        UUID playerEntityUuid = UUID.randomUUID();
        try {
            entityId = (int) nmsPlayer.getClass().getMethod("getId").invoke(nmsPlayer);
            playerEntityUuid = (UUID) nmsPlayer.getClass().getMethod("getUUID").invoke(nmsPlayer);
        } catch (Throwable ignored) {
        }

        Interaction interaction = null;
        try {
            interaction = location.getWorld().spawn(location, Interaction.class, ent -> {
                ent.setInteractionWidth(0.6f);
                ent.setInteractionHeight(1.8f);
                ent.setResponsive(true);
            });
        } catch (Throwable ignored) {
        }

        CombatNpc combatNpc = new CombatNpc(
                playerUuid,
                displayName,
                location,
                storage,
                armor,
                offHand,
                exp,
                expireTime,
                opponentUuid,
                health,
                maxHealth,
                absorption,
                effects,
                fireTicks,
                playerEntityUuid
        );
        combatNpc.setNmsPlayer(nmsPlayer);
        combatNpc.setEntityId(entityId);
        if (interaction != null) {
            combatNpc.setInteractionUuid(interaction.getUniqueId());
            npcsByInteractionUuid.put(interaction.getUniqueId(), combatNpc);
        }

        UUID overlayUuid = spawnHpOverlay(location, displayName, health, absorption);
        combatNpc.setHpOverlayUuid(overlayUuid);
        updateHpOverlay(combatNpc);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            updateHpOverlay(combatNpc);
        }, 1L);

        npcsByPlayer.put(playerUuid, combatNpc);
        npcsByEntityUuid.put(playerEntityUuid, combatNpc);
        if (entityId != 0) {
            npcsByEntityId.put(entityId, combatNpc);
        }
        pendingSyncByPlayer.put(playerUuid, new PendingSyncData(
                health,
                maxHealth,
                absorption,
                fireTicks,
                new ArrayList<>(combatNpc.getActiveEffects()),
                opponentUuid,
                false
        ));

        hidePlayerNametag(displayName);
        hidePlayerNametag(sanitizePlayerName(displayName));
        hidePlayerNametag(playerName);

        for (Player viewer : location.getWorld().getPlayers()) {
            if (viewer.isOnline() && viewer.getLocation().distanceSquared(location) <= 16384.0) {
                sendNpcToViewer(viewer, nmsPlayer, location, combatNpc);
            }
        }

        plugin.getLogger().info("Combat NPC spawned for " + displayName + " at " + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ());
        return combatNpc;
    }

    private Object spawnNmsPlayer(
            UUID playerUuid,
            String playerName,
            Location location,
            PlayerProfile playerProfile,
            String skinTexture,
            String skinSignature,
            ItemStack[] storage,
            ItemStack[] armor,
            ItemStack offHand,
            double health,
            double maxHealth,
            Collection<PotionEffect> effects,
            int fireTicks
    ) {
        try {
            Class<?> craftServerClass = resolveClass("org.bukkit.craftbukkit.CraftServer", "org.bukkit.craftbukkit.v1_21_R1.CraftServer", "org.bukkit.craftbukkit.v1_21_R2.CraftServer", "org.bukkit.craftbukkit.v1_21_R3.CraftServer");
            Object craftServer = craftServerClass.cast(Bukkit.getServer());
            Object minecraftServer = craftServerClass.getMethod("getServer").invoke(craftServer);

            Class<?> craftWorldClass = resolveClass("org.bukkit.craftbukkit.CraftWorld", "org.bukkit.craftbukkit.v1_21_R1.CraftWorld", "org.bukkit.craftbukkit.v1_21_R2.CraftWorld", "org.bukkit.craftbukkit.v1_21_R3.CraftWorld");
            Object craftWorld = craftWorldClass.cast(location.getWorld());
            Object serverLevel = craftWorldClass.getMethod("getHandle").invoke(craftWorld);

            String cleanName = sanitizePlayerName(playerName);
            Class<?> gameProfileClass = Class.forName("com.mojang.authlib.GameProfile");
            UUID npcUuid = UUID.randomUUID();

            String finalTexture = skinTexture;
            String finalSig = skinSignature;
            if (finalTexture == null || finalTexture.isBlank()) {
                PlayerProfile effectiveProfile = playerProfile;
                if (effectiveProfile == null || effectiveProfile.getProperties().isEmpty()) {
                    Player onlinePlayer = Bukkit.getPlayer(playerUuid);
                    if (onlinePlayer != null) {
                        effectiveProfile = onlinePlayer.getPlayerProfile();
                    }
                }
                if (effectiveProfile != null) {
                    for (ProfileProperty prop : effectiveProfile.getProperties()) {
                        if ("textures".equalsIgnoreCase(prop.getName())) {
                            finalTexture = prop.getValue();
                            finalSig = prop.getSignature();
                            break;
                        }
                    }
                }
            }

            Object gameProfile = null;
            if (finalTexture != null && !finalTexture.isBlank()) {
                try {
                    Class<?> propertyClass = Class.forName("com.mojang.authlib.properties.Property");
                    Object authProp = null;
                    if (finalSig != null && !finalSig.isBlank()) {
                        for (Constructor<?> c : propertyClass.getConstructors()) {
                            Class<?>[] pt = c.getParameterTypes();
                            if (pt.length == 3 && pt[0].equals(String.class) && pt[1].equals(String.class) && pt[2].equals(String.class)) {
                                authProp = c.newInstance("textures", finalTexture, finalSig);
                                break;
                            }
                        }
                    }
                    if (authProp == null) {
                        for (Constructor<?> c : propertyClass.getConstructors()) {
                            Class<?>[] pt = c.getParameterTypes();
                            if (pt.length == 2 && pt[0].equals(String.class) && pt[1].equals(String.class)) {
                                authProp = c.newInstance("textures", finalTexture);
                                break;
                            }
                        }
                    }

                    if (authProp != null) {
                        try {
                            Class<?> propMapClass = Class.forName("com.mojang.authlib.properties.PropertyMap");
                            Class<?> multimapClass = Class.forName("com.google.common.collect.Multimap");
                            Class<?> linkedHashMultimapClass = Class.forName("com.google.common.collect.LinkedHashMultimap");
                            Method createMultimap = linkedHashMultimapClass.getMethod("create");
                            Object multimap = createMultimap.invoke(null);
                            Method putMethod = multimapClass.getMethod("put", Object.class, Object.class);
                            putMethod.invoke(multimap, "textures", authProp);

                            Constructor<?> propMapCtor = propMapClass.getConstructor(multimapClass);
                            Object propMap = propMapCtor.newInstance(multimap);

                            Constructor<?> gpCtor = gameProfileClass.getConstructor(UUID.class, String.class, propMapClass);
                            gameProfile = gpCtor.newInstance(npcUuid, cleanName, propMap);
                        } catch (Throwable tRecord) {
                            Constructor<?> gpCtor = gameProfileClass.getConstructor(UUID.class, String.class);
                            gameProfile = gpCtor.newInstance(npcUuid, cleanName);
                            Method propsMethod = null;
                            try {
                                propsMethod = gameProfile.getClass().getMethod("properties");
                            } catch (NoSuchMethodException e) {
                                try {
                                    propsMethod = gameProfile.getClass().getMethod("getProperties");
                                } catch (NoSuchMethodException ignored) {
                                }
                            }
                            if (propsMethod != null) {
                                Object properties = propsMethod.invoke(gameProfile);
                                Method putMethod = properties.getClass().getMethod("put", Object.class, Object.class);
                                putMethod.invoke(properties, "textures", authProp);
                            }
                        }
                    }
                } catch (Throwable t) {
                    plugin.getLogger().warning("Failed to prepare GameProfile properties: " + t.getMessage());
                }
            }
            if (gameProfile == null) {
                gameProfile = gameProfileClass.getConstructor(UUID.class, String.class).newInstance(npcUuid, cleanName);
            }

            Class<?> clientInfoClass = Class.forName("net.minecraft.server.level.ClientInformation");
            Object clientInfo = null;
            try {
                Method createDefault = clientInfoClass.getMethod("createDefault");
                clientInfo = createDefault.invoke(null);
            } catch (Throwable ignored) {
            }
            if (clientInfo == null) {
                for (Constructor<?> c : clientInfoClass.getConstructors()) {
                    try {
                        Class<?>[] pt = c.getParameterTypes();
                        Object[] dummy = new Object[pt.length];
                        for (int i = 0; i < pt.length; i++) {
                            if (pt[i].equals(String.class)) dummy[i] = "en_us";
                            else if (pt[i].equals(int.class)) dummy[i] = 127;
                            else if (pt[i].equals(byte.class)) dummy[i] = (byte) 127;
                            else if (pt[i].equals(boolean.class)) dummy[i] = true;
                            else if (pt[i].isEnum()) dummy[i] = pt[i].getEnumConstants()[0];
                        }
                        clientInfo = c.newInstance(dummy);
                        break;
                    } catch (Throwable ignored) {
                    }
                }
            }

            Class<?> serverPlayerClass = Class.forName("net.minecraft.server.level.ServerPlayer");
            Constructor<?> playerCtor = null;
            for (Constructor<?> c : serverPlayerClass.getConstructors()) {
                Class<?>[] pt = c.getParameterTypes();
                if (pt.length == 4 && pt[2].equals(gameProfileClass) && pt[3].equals(clientInfoClass)) {
                    playerCtor = c;
                    break;
                }
            }
            if (playerCtor == null) {
                for (Constructor<?> c : serverPlayerClass.getConstructors()) {
                    if (c.getParameterCount() == 4) {
                        playerCtor = c;
                        break;
                    }
                }
            }
            if (playerCtor == null) {
                plugin.getLogger().warning("ServerPlayer 4-arg constructor not found.");
                return null;
            }
            Object nmsPlayer = playerCtor.newInstance(minecraftServer, serverLevel, gameProfile, clientInfo);

            try {
                serverPlayerClass.getMethod("setPos", double.class, double.class, double.class)
                        .invoke(nmsPlayer, location.getX(), location.getY(), location.getZ());
                serverPlayerClass.getMethod("setRot", float.class, float.class)
                        .invoke(nmsPlayer, location.getYaw(), location.getPitch());
                serverPlayerClass.getMethod("setYHeadRot", float.class).invoke(nmsPlayer, location.getYaw());
            } catch (Throwable ignored) {
            }

            try {
                Object synchedData = nmsPlayer.getClass().getMethod("getEntityData").invoke(nmsPlayer);
                Class<?> playerNmsClass = Class.forName("net.minecraft.world.entity.player.Player");
                for (Field f : playerNmsClass.getDeclaredFields()) {
                    String fn = f.getName().toUpperCase();
                    if (fn.contains("CUSTOMISATION") || fn.contains("CUSTOMIZATION") || fn.contains("SKIN_PARTS")) {
                        f.setAccessible(true);
                        Object accessor = f.get(null);
                        Method setMethod = synchedData.getClass().getMethod("set", accessor.getClass(), Object.class);
                        setMethod.invoke(synchedData, accessor, (byte) 127);
                        break;
                    }
                }
            } catch (Throwable ignored) {
            }

            try {
                Player bukkitPlayer = (Player) serverPlayerClass.getMethod("getBukkitEntity").invoke(nmsPlayer);
                equipLivingEntity(bukkitPlayer, playerUuid, cleanName, storage, armor, offHand, health, maxHealth, effects, fireTicks);
            } catch (Throwable ignored) {
            }

            return nmsPlayer;
        } catch (Throwable t) {
            plugin.getLogger().warning("spawnNmsPlayer failed: " + t.getClass().getSimpleName() + " - " + t.getMessage());
            return null;
        }
    }

    private Object createPlayerInfoUpdatePacket(Object nmsPlayer) {
        if (nmsPlayer == null) {
            return null;
        }
        try {
            Class<?> infoPacketClass = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket");
            Class<?> actionClass = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket$Action");
            Class<?> entryClass = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket$Entry");
            Class<?> gameTypeClass = Class.forName("net.minecraft.world.level.GameType");

            UUID uuid = (UUID) nmsPlayer.getClass().getMethod("getUUID").invoke(nmsPlayer);
            Object profile = nmsPlayer.getClass().getMethod("getGameProfile").invoke(nmsPlayer);
            Object gameType = Enum.valueOf((Class<Enum>) gameTypeClass, "SURVIVAL");

            Constructor<?> entryCtor = null;
            for (Constructor<?> c : entryClass.getConstructors()) {
                if (c.getParameterCount() == 7) {
                    entryCtor = c;
                    break;
                }
            }
            if (entryCtor == null) {
                for (Constructor<?> c : entryClass.getDeclaredConstructors()) {
                    if (c.getParameterCount() == 7) {
                        entryCtor = c;
                        c.setAccessible(true);
                        break;
                    }
                }
            }
            if (entryCtor == null) {
                return null;
            }

            Object entry = entryCtor.newInstance(uuid, profile, true, 0, gameType, null, null);

            Class<?> enumSetClass = Class.forName("java.util.EnumSet");
            Object addPlayer = Enum.valueOf((Class<Enum>) actionClass, "ADD_PLAYER");
            Object updateListed = Enum.valueOf((Class<Enum>) actionClass, "UPDATE_LISTED");
            Method ofMethod = enumSetClass.getMethod("of", Enum.class, Enum.class);
            Object actions = ofMethod.invoke(null, addPlayer, updateListed);

            for (Constructor<?> c : infoPacketClass.getConstructors()) {
                Class<?>[] pt = c.getParameterTypes();
                if (pt.length == 2 && pt[0].equals(enumSetClass) && pt[1].equals(entryClass)) {
                    return c.newInstance(actions, entry);
                }
            }
            for (Constructor<?> c : infoPacketClass.getConstructors()) {
                Class<?>[] pt = c.getParameterTypes();
                if (pt.length == 2 && pt[0].equals(enumSetClass) && pt[1].equals(List.class)) {
                    return c.newInstance(actions, Collections.singletonList(entry));
                }
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("createPlayerInfoUpdatePacket failed: " + t.getClass().getSimpleName() + " - " + t.getMessage());
        }
        return null;
    }

    private Object createAddEntityPacket(Object nmsPlayer, Location location) {
        if (nmsPlayer == null || location == null) {
            return null;
        }
        try {
            Class<?> addPacketClass = Class.forName("net.minecraft.network.protocol.game.ClientboundAddEntityPacket");
            Class<?> entityClass = Class.forName("net.minecraft.world.entity.Entity");
            Class<?> blockPosClass = Class.forName("net.minecraft.core.BlockPos");

            Constructor<?> blockPosCtor = blockPosClass.getConstructor(int.class, int.class, int.class);
            Object blockPos = blockPosCtor.newInstance(location.getBlockX(), location.getBlockY(), location.getBlockZ());

            for (Constructor<?> c : addPacketClass.getConstructors()) {
                Class<?>[] pt = c.getParameterTypes();
                if (pt.length == 3 && pt[0].isAssignableFrom(entityClass) && pt[1].equals(int.class) && pt[2].equals(blockPosClass)) {
                    Object packet = c.newInstance(nmsPlayer, 0, blockPos);
                    try {
                        Field fx = addPacketClass.getDeclaredField("x");
                        fx.setAccessible(true);
                        fx.set(packet, location.getX());

                        Field fy = addPacketClass.getDeclaredField("y");
                        fy.setAccessible(true);
                        fy.set(packet, location.getY());

                        Field fz = addPacketClass.getDeclaredField("z");
                        fz.setAccessible(true);
                        fz.set(packet, location.getZ());

                        Field fxRot = addPacketClass.getDeclaredField("xRot");
                        fxRot.setAccessible(true);
                        fxRot.set(packet, (byte) ((location.getPitch() * 256.0F) / 360.0F));

                        Field fyRot = addPacketClass.getDeclaredField("yRot");
                        fyRot.setAccessible(true);
                        fyRot.set(packet, (byte) ((location.getYaw() * 256.0F) / 360.0F));

                        Field fyHeadRot = addPacketClass.getDeclaredField("yHeadRot");
                        fyHeadRot.setAccessible(true);
                        fyHeadRot.set(packet, (byte) ((location.getYaw() * 256.0F) / 360.0F));
                    } catch (Throwable ignored) {
                    }
                    return packet;
                }
            }
        } catch (Throwable ignored) {
        }

        try {
            Class<?> addPacketClass = Class.forName("net.minecraft.network.protocol.game.ClientboundAddEntityPacket");
            int id = (int) nmsPlayer.getClass().getMethod("getId").invoke(nmsPlayer);
            UUID uuid = (UUID) nmsPlayer.getClass().getMethod("getUUID").invoke(nmsPlayer);
            Object playerType = nmsPlayer.getClass().getMethod("getType").invoke(nmsPlayer);
            Object deltaMovement = null;
            try {
                deltaMovement = nmsPlayer.getClass().getMethod("getDeltaMovement").invoke(nmsPlayer);
            } catch (Throwable ignored) {
            }
            if (deltaMovement == null) {
                Class<?> vec3Class = Class.forName("net.minecraft.world.phys.Vec3");
                deltaMovement = vec3Class.getField("ZERO").get(null);
            }

            for (Constructor<?> c : addPacketClass.getDeclaredConstructors()) {
                Class<?>[] pt = c.getParameterTypes();
                if (pt.length == 11) {
                    c.setAccessible(true);
                    Object[] args = new Object[11];
                    args[0] = id;
                    args[1] = uuid;
                    args[2] = location.getX();
                    args[3] = location.getY();
                    args[4] = location.getZ();
                    args[5] = pt[5].equals(float.class) ? (float) location.getPitch() : (double) location.getPitch();
                    args[6] = pt[6].equals(float.class) ? (float) location.getYaw() : (double) location.getYaw();
                    args[7] = playerType;
                    args[8] = 0;
                    args[9] = deltaMovement;
                    args[10] = pt[10].equals(double.class) ? (double) location.getYaw() : (float) location.getYaw();
                    return c.newInstance(args);
                }
            }
        } catch (Throwable t2) {
            plugin.getLogger().warning("createAddEntityPacket failed: " + t2.getClass().getSimpleName() + " - " + t2.getMessage());
        }
        return null;
    }

    private Object createMetadataPacket(Object nmsPlayer) {
        if (nmsPlayer == null) {
            return null;
        }
        try {
            Class<?> metaPacketClass = Class.forName("net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket");
            Object synchedData = nmsPlayer.getClass().getMethod("getEntityData").invoke(nmsPlayer);
            Object packedItems = null;
            try {
                Method packMethod = synchedData.getClass().getMethod("packAll");
                packedItems = packMethod.invoke(synchedData);
            } catch (Throwable ignored) {
            }
            if (packedItems == null) {
                try {
                    Method packDirtyMethod = synchedData.getClass().getMethod("packDirty");
                    packedItems = packDirtyMethod.invoke(synchedData);
                } catch (Throwable ignored) {
                }
            }
            if (packedItems == null) {
                try {
                    Method getAllMethod = synchedData.getClass().getMethod("getAll");
                    packedItems = getAllMethod.invoke(synchedData);
                } catch (Throwable ignored) {
                }
            }
            if (packedItems == null) {
                return null;
            }

            int entityId = (int) nmsPlayer.getClass().getMethod("getId").invoke(nmsPlayer);
            for (Constructor<?> c : metaPacketClass.getConstructors()) {
                Class<?>[] pt = c.getParameterTypes();
                if (pt.length == 2 && pt[0].equals(int.class)) {
                    c.setAccessible(true);
                    return c.newInstance(entityId, packedItems);
                }
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("createMetadataPacket failed: " + t.getMessage());
        }
        return null;
    }

    private Object createRotateHeadPacket(Object nmsPlayer, float yaw) {
        if (nmsPlayer == null) {
            return null;
        }
        try {
            Class<?> rotateHeadPacketClass = Class.forName("net.minecraft.network.protocol.game.ClientboundRotateHeadPacket");
            Class<?> entityClass = Class.forName("net.minecraft.world.entity.Entity");
            byte yawByte = (byte) ((yaw * 256.0F) / 360.0F);

            for (Constructor<?> c : rotateHeadPacketClass.getDeclaredConstructors()) {
                Class<?>[] pt = c.getParameterTypes();
                if (pt.length == 2 && pt[0].isAssignableFrom(entityClass) && pt[1].equals(byte.class)) {
                    c.setAccessible(true);
                    return c.newInstance(nmsPlayer, yawByte);
                }
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("createRotateHeadPacket failed: " + t.getMessage());
        }
        return null;
    }

    private Object createEquipmentPacket(Object nmsPlayer, ItemStack[] storage, ItemStack[] armor, ItemStack offHand) {
        if (nmsPlayer == null) {
            return null;
        }
        try {
            Class<?> equipPacketClass = Class.forName("net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket");
            Class<?> slotClass = Class.forName("net.minecraft.world.entity.EquipmentSlot");
            Class<?> pairClass = Class.forName("com.mojang.datafixers.util.Pair");
            Class<?> craftItemClass = resolveClass(
                    "org.bukkit.craftbukkit.inventory.CraftItemStack",
                    "org.bukkit.craftbukkit.v1_21_R1.inventory.CraftItemStack",
                    "org.bukkit.craftbukkit.v1_21_R2.inventory.CraftItemStack",
                    "org.bukkit.craftbukkit.v1_21_R3.inventory.CraftItemStack"
            );
            Method asNmsCopy = craftItemClass.getMethod("asNMSCopy", ItemStack.class);
            Constructor<?> pairCtor = pairClass.getConstructor(Object.class, Object.class);

            List<Object> pairs = new ArrayList<>();

            Object mainHandSlot = Enum.valueOf((Class<Enum>) slotClass, "MAINHAND");
            Object offHandSlot = Enum.valueOf((Class<Enum>) slotClass, "OFFHAND");
            Object feetSlot = Enum.valueOf((Class<Enum>) slotClass, "FEET");
            Object legsSlot = Enum.valueOf((Class<Enum>) slotClass, "LEGS");
            Object chestSlot = Enum.valueOf((Class<Enum>) slotClass, "CHEST");
            Object headSlot = Enum.valueOf((Class<Enum>) slotClass, "HEAD");

            if (storage != null && storage.length > 0 && storage[0] != null && !storage[0].getType().isAir()) {
                pairs.add(pairCtor.newInstance(mainHandSlot, asNmsCopy.invoke(null, storage[0])));
            }
            if (offHand != null && !offHand.getType().isAir()) {
                pairs.add(pairCtor.newInstance(offHandSlot, asNmsCopy.invoke(null, offHand)));
            }
            if (armor != null && armor.length > 0 && armor[0] != null && !armor[0].getType().isAir()) {
                pairs.add(pairCtor.newInstance(feetSlot, asNmsCopy.invoke(null, armor[0])));
            }
            if (armor != null && armor.length > 1 && armor[1] != null && !armor[1].getType().isAir()) {
                pairs.add(pairCtor.newInstance(legsSlot, asNmsCopy.invoke(null, armor[1])));
            }
            if (armor != null && armor.length > 2 && armor[2] != null && !armor[2].getType().isAir()) {
                pairs.add(pairCtor.newInstance(chestSlot, asNmsCopy.invoke(null, armor[2])));
            }
            if (armor != null && armor.length > 3 && armor[3] != null && !armor[3].getType().isAir()) {
                pairs.add(pairCtor.newInstance(headSlot, asNmsCopy.invoke(null, armor[3])));
            }

            if (pairs.isEmpty()) {
                return null;
            }

            int entityId = (int) nmsPlayer.getClass().getMethod("getId").invoke(nmsPlayer);
            Constructor<?> equipCtor = equipPacketClass.getConstructor(int.class, List.class);
            return equipCtor.newInstance(entityId, pairs);
        } catch (Throwable t) {
            plugin.getLogger().warning("createEquipmentPacket failed: " + t.getMessage());
            return null;
        }
    }

    private void broadcastPacket(Location location, Object packet) {
        if (location == null || location.getWorld() == null || packet == null) {
            return;
        }
        for (Player online : location.getWorld().getPlayers()) {
            sendPacketToViewer(online, packet);
        }
    }

    private void sendPacketToViewer(Player viewer, Object packet) {
        if (viewer == null || !viewer.isOnline() || packet == null) {
            return;
        }
        try {
            Object nmsViewer = viewer.getClass().getMethod("getHandle").invoke(viewer);
            Field connField = null;
            for (Field f : nmsViewer.getClass().getFields()) {
                if (f.getName().equals("connection")) {
                    connField = f;
                    break;
                }
            }
            if (connField == null) {
                for (Field f : nmsViewer.getClass().getDeclaredFields()) {
                    if (f.getName().equals("connection") || f.getType().getSimpleName().contains("PacketListener")) {
                        connField = f;
                        break;
                    }
                }
            }
            if (connField != null) {
                connField.setAccessible(true);
                Object conn = connField.get(nmsViewer);
                if (conn != null) {
                    Class<?> packetClass = Class.forName("net.minecraft.network.protocol.Packet");
                    Method sendMethod = null;
                    try {
                        sendMethod = conn.getClass().getMethod("send", packetClass);
                    } catch (NoSuchMethodException e) {
                        try {
                            sendMethod = conn.getClass().getMethod("sendPacket", packetClass);
                        } catch (NoSuchMethodException ignored) {
                        }
                    }
                    if (sendMethod == null) {
                        for (Method m : conn.getClass().getMethods()) {
                            if ((m.getName().equals("send") || m.getName().equals("sendPacket")) && m.getParameterCount() == 1) {
                                sendMethod = m;
                                break;
                            }
                        }
                    }
                    if (sendMethod != null) {
                        sendMethod.invoke(conn, packet);
                    }
                }
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("Failed to send packet to " + viewer.getName() + ": " + t.getMessage());
        }
    }

    private void sendNpcToViewer(Player viewer, Object nmsPlayer, Location location, CombatNpc combatNpc) {
        if (viewer == null || !viewer.isOnline() || nmsPlayer == null || location == null || location.getWorld() == null) {
            return;
        }
        Scoreboard sb = viewer.getScoreboard();
        if (sb != null) {
            Team pt = ensureHiddenTeam(sb);
            if (pt != null) {
                pt.addEntry(combatNpc.getPlayerName());
                pt.addEntry(sanitizePlayerName(combatNpc.getPlayerName()));
            }
        }
        Object infoPacket = createPlayerInfoUpdatePacket(nmsPlayer);
        if (infoPacket != null) {
            sendPacketToViewer(viewer, infoPacket);
        }

        Object addPacket = createAddEntityPacket(nmsPlayer, location);
        if (addPacket != null) {
            sendPacketToViewer(viewer, addPacket);
        }

        Object metaPacket = createMetadataPacket(nmsPlayer);
        if (metaPacket != null) {
            sendPacketToViewer(viewer, metaPacket);
        }

        Object rotatePacket = createRotateHeadPacket(nmsPlayer, location.getYaw());
        if (rotatePacket != null) {
            sendPacketToViewer(viewer, rotatePacket);
        }

        Object equipPacket = createEquipmentPacket(nmsPlayer, combatNpc.getInventoryContents(), combatNpc.getArmorContents(), combatNpc.getOffHandItem());
        if (equipPacket != null) {
            sendPacketToViewer(viewer, equipPacket);
        }
    }

    private void sendRemovePlayerInfoPacket(Location location, UUID npcUuid) {
        try {
            Class<?> removePacketClass = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket");
            Constructor<?> ctor = removePacketClass.getConstructor(List.class);
            Object packet = ctor.newInstance(Collections.singletonList(npcUuid));
            broadcastPacket(location, packet);
        } catch (Throwable ignored) {
        }
    }

    private void sendRemoveEntitiesPacket(Location location, int entityId) {
        try {
            Class<?> removePacketClass = Class.forName("net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket");
            for (Constructor<?> c : removePacketClass.getConstructors()) {
                Class<?>[] pt = c.getParameterTypes();
                if (pt.length == 1) {
                    if (pt[0].equals(int[].class)) {
                        Object packet = c.newInstance(new int[]{entityId});
                        broadcastPacket(location, packet);
                        return;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private void broadcastHurtAnimation(Location loc, Object nmsPlayer, int entityId) {
        try {
            Class<?> animPacketClass = Class.forName("net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket");
            Constructor<?> ctor = animPacketClass.getConstructor(int.class, float.class);
            Object pkt = ctor.newInstance(entityId, 0.0f);
            broadcastPacket(loc, pkt);
            return;
        } catch (Throwable ignored) {
        }
        if (nmsPlayer != null) {
            try {
                Class<?> animPacketClass = Class.forName("net.minecraft.network.protocol.game.ClientboundAnimatePacket");
                Class<?> entityClass = Class.forName("net.minecraft.world.entity.Entity");
                Constructor<?> ctor = animPacketClass.getConstructor(entityClass, int.class);
                Object pkt = ctor.newInstance(nmsPlayer, 1);
                broadcastPacket(loc, pkt);
            } catch (Throwable ignored) {
            }
        }
    }

    private Class<?> resolveClass(String... classNames) throws ClassNotFoundException {
        for (String className : classNames) {
            try {
                return Class.forName(className);
            } catch (ClassNotFoundException ignored) {
            }
        }
        throw new ClassNotFoundException(classNames.length > 0 ? classNames[0] : "UnknownClass");
    }

    private void equipLivingEntity(
            LivingEntity living,
            UUID playerUuid,
            String playerName,
            ItemStack[] storage,
            ItemStack[] armor,
            ItemStack offHand,
            double health,
            double maxHealth,
            @Nullable Collection<PotionEffect> effects,
            int fireTicks
    ) {
        String cleanName = sanitizePlayerName(playerName);
        living.customName(ChatColorUtil.format(cleanName));
        living.setCustomNameVisible(false);

        Attribute healthAttr = resolveMaxHealthAttribute();
        if (healthAttr != null) {
            AttributeInstance instance = living.getAttribute(healthAttr);
            if (instance != null) {
                instance.setBaseValue(Math.max(20.0, maxHealth));
            }
        }
        living.setHealth(Math.max(1.0, Math.min(living.getMaxHealth(), health)));

        if (effects != null && !effects.isEmpty()) {
            living.addPotionEffects(effects);
        }
        if (fireTicks > 0) {
            living.setFireTicks(fireTicks);
        }

        EntityEquipment eq = living.getEquipment();
        if (eq != null) {
            if (armor != null && armor.length > 3 && armor[3] != null && !armor[3].getType().isAir()) {
                eq.setHelmet(armor[3].clone());
            }
            if (armor != null && armor.length > 2) eq.setChestplate(cloneOrNull(armor[2]));
            if (armor != null && armor.length > 1) eq.setLeggings(cloneOrNull(armor[1]));
            if (armor != null && armor.length > 0) eq.setBoots(cloneOrNull(armor[0]));
            if (storage != null && storage.length > 0) eq.setItemInMainHand(cloneOrNull(storage[0]));
            eq.setItemInOffHand(cloneOrNull(offHand));
        }
    }

    public static String buildHpDisplay(double currentHealth, double maxHealth, double absorption) {
        double finalHealthValue;
        String colorCode;
        if (absorption > 0.0) {
            finalHealthValue = Math.max(0.0, currentHealth) + absorption;
            colorCode = "§e";
        } else {
            finalHealthValue = Math.max(0.0, currentHealth);
            colorCode = "§c";
        }
        return colorCode + String.format(Locale.US, "%.1f", finalHealthValue) + " HP";
    }

    public void updateNPCNameplate(TextDisplay display, String playerName, double baseHealth, double absorption) {
        CombatNpcNameplateUtil.get().updateNPCNameplate(display, playerName, baseHealth, absorption);
    }

    private UUID spawnHpOverlay(Location location, String playerName, double currentHealth, double absorption) {
        TextDisplay display = CombatNpcNameplateUtil.get().spawnNPCNameplate(location, playerName, currentHealth, absorption);
        return display != null ? display.getUniqueId() : null;
    }

    private void updateHpOverlay(CombatNpc combatNpc) {
        if (combatNpc.getHpOverlayUuid() == null) {
            return;
        }
        Entity entity = Bukkit.getEntity(combatNpc.getHpOverlayUuid());
        if (entity instanceof TextDisplay display) {
            updateNPCNameplate(display, combatNpc.getPlayerName(), combatNpc.getHealth(), combatNpc.getAbsorption());
        }
    }

    private void removeHpOverlay(CombatNpc combatNpc) {
        if (combatNpc.getHpOverlayUuid() != null) {
            Entity entity = Bukkit.getEntity(combatNpc.getHpOverlayUuid());
            if (entity != null) {
                entity.remove();
            }
            combatNpc.setHpOverlayUuid(null);
        }
    }

    public double calculateMitigatedDamage(CombatNpc combatNpc, double rawDamage) {
        if (rawDamage <= 0.0) {
            return 0.0;
        }

        double totalArmor = 0.0;
        double totalToughness = 0.0;
        int totalEpf = 0;

        for (ItemStack item : combatNpc.getArmorContents()) {
            if (item == null || item.getType().isAir()) {
                continue;
            }
            totalArmor += getArmorPoints(item.getType());
            totalToughness += getArmorToughness(item.getType());
            try {
                totalEpf += item.getEnchantmentLevel(Enchantment.PROTECTION);
            } catch (Throwable ignored) {
            }
        }

        double defense = Math.min(20.0, Math.max(totalArmor / 5.0, totalArmor - rawDamage / (2.0 + totalToughness / 4.0)));
        double afterArmor = rawDamage * (1.0 - defense / 25.0);

        double epfReduction = Math.min(20, totalEpf) * 0.04;
        double afterEpf = afterArmor * (1.0 - Math.min(0.80, epfReduction));

        double afterResistance = afterEpf;
        for (PotionEffect effect : combatNpc.getActiveEffects()) {
            if (effect.getType().equals(PotionEffectType.RESISTANCE)) {
                int amp = effect.getAmplifier() + 1;
                afterResistance = afterResistance * Math.max(0.0, 1.0 - (amp * 0.20));
                break;
            }
        }

        return Math.max(0.0, afterResistance);
    }

    private double getArmorPoints(Material material) {
        String name = material.name();
        if (name.endsWith("_HELMET")) {
            if (name.startsWith("NETHERITE_") || name.startsWith("DIAMOND_")) return 3.0;
            if (name.startsWith("IRON_") || name.startsWith("TURTLE_") || name.startsWith("GOLDEN_") || name.startsWith("CHAINMAIL_")) return 2.0;
            if (name.startsWith("LEATHER_")) return 1.0;
        } else if (name.endsWith("_CHESTPLATE")) {
            if (name.startsWith("NETHERITE_") || name.startsWith("DIAMOND_")) return 8.0;
            if (name.startsWith("IRON_")) return 6.0;
            if (name.startsWith("CHAINMAIL_") || name.startsWith("GOLDEN_")) return 5.0;
            if (name.startsWith("LEATHER_")) return 3.0;
        } else if (name.endsWith("_LEGGINGS")) {
            if (name.startsWith("NETHERITE_") || name.startsWith("DIAMOND_")) return 6.0;
            if (name.startsWith("IRON_")) return 5.0;
            if (name.startsWith("CHAINMAIL_")) return 4.0;
            if (name.startsWith("GOLDEN_")) return 3.0;
            if (name.startsWith("LEATHER_")) return 2.0;
        } else if (name.endsWith("_BOOTS")) {
            if (name.startsWith("NETHERITE_") || name.startsWith("DIAMOND_")) return 3.0;
            if (name.startsWith("IRON_")) return 2.0;
            if (name.startsWith("CHAINMAIL_") || name.startsWith("GOLDEN_") || name.startsWith("LEATHER_")) return 1.0;
        }
        return 0.0;
    }

    private double getArmorToughness(Material material) {
        String name = material.name();
        if (name.startsWith("NETHERITE_")) return 3.0;
        if (name.startsWith("DIAMOND_")) return 2.0;
        return 0.0;
    }

    public double resolvePlayerAttackDamage(Player attacker) {
        double damage = 1.0;
        ItemStack held = attacker.getInventory().getItemInMainHand();
        Material mat = (held != null) ? held.getType() : Material.AIR;
        double weaponBase = getBaseWeaponDamage(mat);

        try {
            Attribute damageAttr = resolveAttackDamageAttribute();
            if (damageAttr != null) {
                AttributeInstance inst = attacker.getAttribute(damageAttr);
                if (inst != null) {
                    damage = inst.getValue();
                }
            }
        } catch (Throwable ignored) {
        }

        damage = Math.max(damage, weaponBase);

        if (held != null && !held.getType().isAir()) {
            int sharpness = resolveSharpnessLevel(held);
            if (sharpness > 0) {
                damage += (0.5 * sharpness + 0.5);
            }
        }

        try {
            PotionEffect strength = attacker.getPotionEffect(PotionEffectType.STRENGTH);
            if (strength != null) {
                damage += 3.0 * (strength.getAmplifier() + 1);
            }
            PotionEffect weakness = attacker.getPotionEffect(PotionEffectType.WEAKNESS);
            if (weakness != null) {
                damage = Math.max(0.0, damage - 4.0 * (weakness.getAmplifier() + 1));
            }
        } catch (Throwable ignored) {
        }

        try {
            float cooldown = attacker.getCooledAttackStrength(0.5f);
            damage *= (0.2 + 0.8 * cooldown * cooldown);
        } catch (Throwable ignored) {
        }

        if (attacker.getFallDistance() > 0.0F && !attacker.isOnGround() && !attacker.isClimbing() && !attacker.isInWater()) {
            damage *= 1.5;
        }

        return Math.max(1.0, damage);
    }

    public void applyDamageToNpc(CombatNpc combatNpc, Player attacker, double rawDamage) {
        long now = System.currentTimeMillis();
        if (now - combatNpc.getLastDamageTimeMs() < 250) {
            return;
        }
        combatNpc.setLastDamageTimeMs(now);

        double finalDamage = calculateMitigatedDamage(combatNpc, rawDamage);

        double absorption = combatNpc.getAbsorption();
        if (absorption > 0.0) {
            if (finalDamage < absorption) {
                combatNpc.setAbsorption(absorption - finalDamage);
                finalDamage = 0.0;
            } else {
                finalDamage -= absorption;
                combatNpc.setAbsorption(0.0);
                combatNpc.getActiveEffects().removeIf(e -> e.getType().equals(PotionEffectType.ABSORPTION));
            }
        }

        double remaining = combatNpc.getHealth() - finalDamage;
        combatNpc.setHealth(remaining);

        pendingSyncByPlayer.put(combatNpc.getPlayerUuid(), new PendingSyncData(
                remaining,
                combatNpc.getMaxHealth(),
                combatNpc.getAbsorption(),
                combatNpc.getFireTicks(),
                new ArrayList<>(combatNpc.getActiveEffects()),
                combatNpc.getOpponentUuid(),
                true
        ));

        updateHpOverlay(combatNpc);

        Location loc = combatNpc.getLocation();
        if (loc.getWorld() != null) {
            loc.getWorld().playSound(loc, Sound.ENTITY_PLAYER_HURT, 1.0f, 1.0f);
            loc.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, loc.clone().add(0, 1.2, 0), 4, 0.2, 0.2, 0.2, 0.1);
        }

        broadcastHurtAnimation(loc, combatNpc.getNmsPlayer(), combatNpc.getEntityId());

        combatManager.tagPlayer(attacker, null);

        if (remaining <= 0.0) {
            handleNpcDeath(combatNpc, attacker);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        Entity entity = event.getEntity();
        CombatNpc combatNpc = npcsByInteractionUuid.get(entity.getUniqueId());
        if (combatNpc == null) {
            combatNpc = npcsByEntityUuid.get(entity.getUniqueId());
        }
        if (combatNpc == null) {
            return;
        }

        event.setCancelled(true);
        Player attacker = resolveAttacker(event);
        if (attacker == null) {
            return;
        }

        double rawDamage;
        if (event.getDamager() instanceof Projectile) {
            rawDamage = event.getDamage();
        } else {
            rawDamage = resolvePlayerAttackDamage(attacker);
        }
        applyDamageToNpc(combatNpc, attacker, rawDamage);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerUseUnknownEntity(PlayerUseUnknownEntityEvent event) {
        if (!event.isAttack()) {
            return;
        }
        int entityId = event.getEntityId();
        CombatNpc combatNpc = npcsByEntityId.get(entityId);
        if (combatNpc == null) {
            return;
        }
        Player attacker = event.getPlayer();
        double rawDamage = resolvePlayerAttackDamage(attacker);
        applyDamageToNpc(combatNpc, attacker, rawDamage);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (consumePendingKill(player.getUniqueId())) {
            player.getInventory().clear();
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    player.getInventory().clear();
                    player.setHealth(0.0);
                }
            });
            return;
        }

        Optional<CombatNpc> optNpc = getNpcByPlayer(player.getUniqueId());
        PendingSyncData pendingSync = pendingSyncByPlayer.remove(player.getUniqueId());

        if (optNpc.isPresent() || pendingSync != null) {
            double remainingHealth;
            double remainingAbsorption;
            int remainingFireTicks;
            Collection<PotionEffect> remainingEffects;
            UUID opponentUuid = null;

            if (optNpc.isPresent()) {
                CombatNpc combatNpc = optNpc.get();
                remainingHealth = combatNpc.getHealth();
                remainingAbsorption = Math.max(0.0, combatNpc.getAbsorption());
                remainingFireTicks = combatNpc.getFireTicks();
                remainingEffects = new ArrayList<>(combatNpc.getActiveEffects());
                opponentUuid = combatNpc.getOpponentUuid();
                despawnNpc(player.getUniqueId());
            } else {
                remainingHealth = pendingSync.getHealth();
                remainingAbsorption = Math.max(0.0, pendingSync.getAbsorption());
                remainingFireTicks = pendingSync.getFireTicks();
                remainingEffects = pendingSync.getEffects();
                opponentUuid = pendingSync.getOpponentUuid();
            }

            final double targetHealth = remainingHealth;
            final double targetAbsorption = remainingAbsorption;
            final int targetFireTicks = remainingFireTicks;
            final Collection<PotionEffect> targetEffects = remainingEffects;

            applyPlayerSync(player, targetHealth, targetAbsorption, targetFireTicks, targetEffects);

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                applyPlayerSync(player, targetHealth, targetAbsorption, targetFireTicks, targetEffects);
            }, 1L);

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                applyPlayerSync(player, targetHealth, targetAbsorption, targetFireTicks, targetEffects);
            }, 3L);

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                applyPlayerSync(player, targetHealth, targetAbsorption, targetFireTicks, targetEffects);
            }, 8L);

            if (opponentUuid != null) {
                Player opp = Bukkit.getPlayer(opponentUuid);
                if (opp != null) {
                    combatManager.tagPlayer(player, opp);
                }
            }

            player.sendMessage(ChatColorUtil.format(configManager.getNpcRejoinNotice()));
            plugin.getLogger().info("Synchronized health for rejoining player " + player.getName() + " to " + targetHealth + " HP (absorption: " + targetAbsorption + ")");
        }

        Scoreboard playerSb = player.getScoreboard();
        if (playerSb != null) {
            Team pt = ensureHiddenTeam(playerSb);
            if (pt != null) {
                for (CombatNpc activeNpc : npcsByPlayer.values()) {
                    pt.addEntry(activeNpc.getPlayerName());
                    pt.addEntry(sanitizePlayerName(activeNpc.getPlayerName()));
                }
            }
        }

        for (CombatNpc activeNpc : npcsByPlayer.values()) {
            Object nmsPlayer = activeNpc.getNmsPlayer();
            if (nmsPlayer != null) {
                sendNpcToViewer(player, nmsPlayer, activeNpc.getLocation(), activeNpc);
            }
        }
    }

    private Player resolveAttacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            return player;
        }
        if (event.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }

    public void handleNpcDeath(@NotNull CombatNpc combatNpc, @Nullable Player killer) {
        Location deathLoc = combatNpc.getLocation();

        for (ItemStack item : combatNpc.getInventoryContents()) {
            if (item != null && !item.getType().isAir()) {
                deathLoc.getWorld().dropItemNaturally(deathLoc, item.clone());
            }
        }
        for (ItemStack item : combatNpc.getArmorContents()) {
            if (item != null && !item.getType().isAir()) {
                deathLoc.getWorld().dropItemNaturally(deathLoc, item.clone());
            }
        }
        if (combatNpc.getOffHandItem() != null && !combatNpc.getOffHandItem().getType().isAir()) {
            deathLoc.getWorld().dropItemNaturally(deathLoc, combatNpc.getOffHandItem().clone());
        }

        if (combatNpc.getDroppedExp() > 0) {
            ExperienceOrb orb = deathLoc.getWorld().spawn(deathLoc, ExperienceOrb.class);
            orb.setExperience(combatNpc.getDroppedExp());
        }

        deathLoc.getWorld().playSound(deathLoc, Sound.ENTITY_PLAYER_DEATH, 1.0f, 1.0f);

        markPendingKill(combatNpc.getPlayerUuid());
        combatManager.endCombatForVictimAndOpponents(combatNpc.getPlayerUuid());

        if (killer != null) {
            combatManager.untagPlayer(killer.getUniqueId());
            String expiredMsg = configManager.getCombatExpiredMessage();
            if (expiredMsg != null && !expiredMsg.isBlank()) {
                killer.sendMessage(ChatColorUtil.format(expiredMsg));
            }
        }

        String killerName = killer != null ? killer.getName() : null;
        if (killerName == null && combatNpc.getOpponentUuid() != null) {
            Player opp = Bukkit.getPlayer(combatNpc.getOpponentUuid());
            if (opp != null) killerName = opp.getName();
        }

        if (killerName != null) {
            String msg = configManager.getNpcKilledBroadcast()
                    .replace("%player%", combatNpc.getPlayerName())
                    .replace("%killer%", killerName);
            Bukkit.broadcast(ChatColorUtil.format(msg));
        } else {
            String msg = configManager.getNpcKilledBroadcastUnknown()
                    .replace("%player%", combatNpc.getPlayerName());
            Bukkit.broadcast(ChatColorUtil.format(msg));
        }

        plugin.getDiscordWebhookService().logNpcSlain(combatNpc.getPlayerName(), killerName);
        despawnNpc(combatNpc.getPlayerUuid());
    }

    private ItemStack cloneOrNull(ItemStack item) {
        return (item != null && !item.getType().isAir()) ? item.clone() : null;
    }

    public void despawnNpc(@NotNull UUID playerUuid) {
        CombatNpc combatNpc = npcsByPlayer.remove(playerUuid);
        if (combatNpc != null) {
            removeHpOverlay(combatNpc);
            sendRemovePlayerInfoPacket(combatNpc.getLocation(), combatNpc.getPlayerUuid());
            if (combatNpc.getEntityUuid() != null) {
                sendRemovePlayerInfoPacket(combatNpc.getLocation(), combatNpc.getEntityUuid());
                npcsByEntityUuid.remove(combatNpc.getEntityUuid());
            }
            if (combatNpc.getEntityId() != 0) {
                sendRemoveEntitiesPacket(combatNpc.getLocation(), combatNpc.getEntityId());
                npcsByEntityId.remove(combatNpc.getEntityId());
            }
            if (combatNpc.getInteractionUuid() != null) {
                npcsByInteractionUuid.remove(combatNpc.getInteractionUuid());
                Entity ent = Bukkit.getEntity(combatNpc.getInteractionUuid());
                if (ent != null) {
                    ent.remove();
                }
            }
            unhidePlayerNametag(combatNpc.getPlayerName());
            unhidePlayerNametag(sanitizePlayerName(combatNpc.getPlayerName()));
        }
    }

    public void markPendingKill(@NotNull UUID playerUuid) {
        pendingKillOnJoin.add(playerUuid);
    }

    public boolean consumePendingKill(@NotNull UUID playerUuid) {
        return pendingKillOnJoin.remove(playerUuid);
    }

    public Optional<CombatNpc> getNpcByPlayer(@NotNull UUID playerUuid) {
        return Optional.ofNullable(npcsByPlayer.get(playerUuid));
    }

    public void despawnAll() {
        if (tickerTask != null && !tickerTask.isCancelled()) {
            tickerTask.cancel();
        }
        for (UUID playerUuid : new HashSet<>(npcsByPlayer.keySet())) {
            despawnNpc(playerUuid);
        }
        npcsByPlayer.clear();
        npcsByEntityUuid.clear();
        npcsByEntityId.clear();
        npcsByInteractionUuid.clear();
        pendingSyncByPlayer.clear();

        Scoreboard main = Bukkit.getScoreboardManager().getMainScoreboard();
        Team mt = main.getTeam("cl_npc_hidden");
        if (mt != null) {
            try {
                mt.unregister();
            } catch (Throwable ignored) {
            }
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            Scoreboard sb = p.getScoreboard();
            if (sb != null && sb != main) {
                Team pt = sb.getTeam("cl_npc_hidden");
                if (pt != null) {
                    try {
                        pt.unregister();
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
    }

    private Team ensureHiddenTeam(Scoreboard scoreboard) {
        if (scoreboard == null) {
            return null;
        }
        Team team = scoreboard.getTeam("cl_npc_hidden");
        if (team == null) {
            try {
                team = scoreboard.registerNewTeam("cl_npc_hidden");
            } catch (IllegalArgumentException e) {
                team = scoreboard.getTeam("cl_npc_hidden");
            }
        }
        if (team != null) {
            team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
            team.setCanSeeFriendlyInvisibles(false);
        }
        return team;
    }

    private void hidePlayerNametag(String name) {
        if (name == null || name.isBlank()) {
            return;
        }
        Scoreboard main = Bukkit.getScoreboardManager().getMainScoreboard();
        Team mt = ensureHiddenTeam(main);
        if (mt != null) {
            mt.addEntry(name);
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            Scoreboard sb = p.getScoreboard();
            if (sb != null && sb != main) {
                Team pt = ensureHiddenTeam(sb);
                if (pt != null) {
                    pt.addEntry(name);
                }
            }
        }
    }

    private void unhidePlayerNametag(String name) {
        if (name == null || name.isBlank()) {
            return;
        }
        Scoreboard main = Bukkit.getScoreboardManager().getMainScoreboard();
        Team mt = main.getTeam("cl_npc_hidden");
        if (mt != null) {
            mt.removeEntry(name);
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            Scoreboard sb = p.getScoreboard();
            if (sb != null && sb != main) {
                Team pt = sb.getTeam("cl_npc_hidden");
                if (pt != null) {
                    pt.removeEntry(name);
                }
            }
        }
    }

    private void applyPlayerSync(Player player, double health, double absorption, int fireTicks, Collection<PotionEffect> effects) {
        if (!player.isOnline()) {
            return;
        }
        Attribute healthAttr = resolveMaxHealthAttribute();
        AttributeInstance maxHealthAttr = healthAttr != null ? player.getAttribute(healthAttr) : null;
        double maxHealth = maxHealthAttr != null ? maxHealthAttr.getValue() : 20.0;

        if (health <= 0.0) {
            player.setHealth(0.0);
            return;
        }

        double safeHealth = Math.max(0.5, Math.min(maxHealth, health));
        player.setHealth(safeHealth);

        if (absorption <= 0.0) {
            player.removePotionEffect(PotionEffectType.ABSORPTION);
            player.setAbsorptionAmount(0.0);
        } else {
            player.setAbsorptionAmount(absorption);
        }

        if (fireTicks > 0) {
            player.setFireTicks(fireTicks);
        }

        if (effects != null) {
            for (PotionEffect effect : effects) {
                if (effect != null && !effect.getType().equals(PotionEffectType.ABSORPTION)) {
                    player.addPotionEffect(effect);
                }
            }
        }
    }

    private double getBaseWeaponDamage(Material material) {
        String name = material.name();
        if (name.equals("NETHERITE_SWORD")) return 8.0;
        if (name.equals("DIAMOND_SWORD")) return 7.0;
        if (name.equals("IRON_SWORD")) return 6.0;
        if (name.equals("STONE_SWORD")) return 5.0;
        if (name.equals("WOODEN_SWORD") || name.equals("GOLDEN_SWORD")) return 4.0;
        if (name.equals("NETHERITE_AXE")) return 10.0;
        if (name.equals("DIAMOND_AXE") || name.equals("IRON_AXE") || name.equals("STONE_AXE")) return 9.0;
        if (name.equals("WOODEN_AXE") || name.equals("GOLDEN_AXE")) return 7.0;
        if (name.equals("TRIDENT")) return 9.0;
        if (name.equals("MACE")) return 6.0;
        return 1.0;
    }

    private int resolveSharpnessLevel(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return 0;
        }
        try {
            Enchantment ench = org.bukkit.Registry.ENCHANTMENT.get(org.bukkit.NamespacedKey.minecraft("sharpness"));
            if (ench != null) {
                return item.getEnchantmentLevel(ench);
            }
        } catch (Throwable ignored) {
        }
        try {
            return item.getEnchantmentLevel(Enchantment.SHARPNESS);
        } catch (Throwable ignored) {
        }
        return 0;
    }

    public static class PendingSyncData {
        private final double health;
        private final double maxHealth;
        private final double absorption;
        private final int fireTicks;
        private final Collection<PotionEffect> effects;
        private final UUID opponentUuid;
        private final boolean wasDamaged;

        public PendingSyncData(
                double health,
                double maxHealth,
                double absorption,
                int fireTicks,
                Collection<PotionEffect> effects,
                UUID opponentUuid,
                boolean wasDamaged
        ) {
            this.health = health;
            this.maxHealth = maxHealth;
            this.absorption = absorption;
            this.fireTicks = fireTicks;
            this.effects = effects;
            this.opponentUuid = opponentUuid;
            this.wasDamaged = wasDamaged;
        }

        public double getHealth() {
            return health;
        }

        public double getMaxHealth() {
            return maxHealth;
        }

        public double getAbsorption() {
            return absorption;
        }

        public int getFireTicks() {
            return fireTicks;
        }

        public Collection<PotionEffect> getEffects() {
            return effects;
        }

        public UUID getOpponentUuid() {
            return opponentUuid;
        }

        public boolean isWasDamaged() {
            return wasDamaged;
        }
    }
}
