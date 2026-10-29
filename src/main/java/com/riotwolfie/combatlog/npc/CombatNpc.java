package com.riotwolfie.combatlog.npc;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class CombatNpc {

    private final UUID playerUuid;
    private final String playerName;
    private final Location location;
    private final ItemStack[] inventoryContents;
    private final ItemStack[] armorContents;
    private final ItemStack offHandItem;
    private final int droppedExp;
    private final Instant spawnTime;
    private final Instant expireTime;
    private final UUID opponentUuid;
    private double health;
    private final double maxHealth;
    private double absorption;
    private final UUID entityUuid;
    private int entityId;
    private Object nmsPlayer;
    private UUID interactionUuid;
    private UUID hpOverlayUuid;
    private final List<PotionEffect> activeEffects = new ArrayList<>();
    private long lastDamageTimeMs;
    private int fireTicks;

    public CombatNpc(
            @NotNull UUID playerUuid,
            @NotNull String playerName,
            @NotNull Location location,
            @NotNull ItemStack[] inventoryContents,
            @NotNull ItemStack[] armorContents,
            @Nullable ItemStack offHandItem,
            int droppedExp,
            @NotNull Instant expireTime,
            @Nullable UUID opponentUuid,
            double health,
            double maxHealth,
            double absorption,
            @Nullable Collection<PotionEffect> effects,
            int fireTicks,
            @Nullable UUID entityUuid) {
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.location = location;
        this.inventoryContents = inventoryContents;
        this.armorContents = armorContents;
        this.offHandItem = offHandItem;
        this.droppedExp = droppedExp;
        this.spawnTime = Instant.now();
        this.expireTime = expireTime;
        this.opponentUuid = opponentUuid;
        this.health = health;
        this.maxHealth = Math.max(1.0, maxHealth);
        this.absorption = Math.max(0.0, absorption);
        if (effects != null) {
            this.activeEffects.addAll(effects);
        }
        this.fireTicks = fireTicks;
        this.entityUuid = entityUuid;
        this.lastDamageTimeMs = 0;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expireTime);
    }

    @Nullable
    public UUID getEntityUuid() {
        return entityUuid;
    }

    @NotNull
    public Location getLocation() {
        return location;
    }

    public double getHealth() {
        return health;
    }

    public void setHealth(double health) {
        this.health = health;
    }

    public double getMaxHealth() {
        return maxHealth;
    }

    public double getAbsorption() {
        return absorption;
    }

    public void setAbsorption(double absorption) {
        this.absorption = Math.max(0.0, absorption);
    }

    @NotNull
    public List<PotionEffect> getActiveEffects() {
        return activeEffects;
    }

    public Instant getSpawnTime() {
        return spawnTime;
    }

    public long getLastDamageTimeMs() {
        return lastDamageTimeMs;
    }

    public void setLastDamageTimeMs(long lastDamageTimeMs) {
        this.lastDamageTimeMs = lastDamageTimeMs;
    }

    public int getFireTicks() {
        return fireTicks;
    }

    public void setFireTicks(int fireTicks) {
        this.fireTicks = fireTicks;
    }

    @Nullable
    public UUID getHpOverlayUuid() {
        return hpOverlayUuid;
    }

    public void setHpOverlayUuid(@Nullable UUID hpOverlayUuid) {
        this.hpOverlayUuid = hpOverlayUuid;
    }

    @NotNull
    public UUID getPlayerUuid() {
        return playerUuid;
    }

    @NotNull
    public String getPlayerName() {
        return playerName;
    }

    @NotNull
    public ItemStack[] getInventoryContents() {
        return inventoryContents;
    }

    @NotNull
    public ItemStack[] getArmorContents() {
        return armorContents;
    }

    @Nullable
    public ItemStack getOffHandItem() {
        return offHandItem;
    }

    public int getDroppedExp() {
        return droppedExp;
    }

    @NotNull
    public Instant getExpireTime() {
        return expireTime;
    }

    @Nullable
    public UUID getOpponentUuid() {
        return opponentUuid;
    }

    public int getEntityId() {
        return entityId;
    }

    public void setEntityId(int entityId) {
        this.entityId = entityId;
    }

    public Object getNmsPlayer() {
        return nmsPlayer;
    }

    public void setNmsPlayer(Object nmsPlayer) {
        this.nmsPlayer = nmsPlayer;
    }

    @Nullable
    public UUID getInteractionUuid() {
        return interactionUuid;
    }

    public void setInteractionUuid(@Nullable UUID interactionUuid) {
        this.interactionUuid = interactionUuid;
    }
}
