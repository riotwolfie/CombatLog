package com.riotwolfie.combatlog.listener;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import java.lang.reflect.Method;
import com.riotwolfie.combatlog.CombatLogPlugin;
import com.riotwolfie.combatlog.api.CombatManager;
import com.riotwolfie.combatlog.api.CombatTag;
import com.riotwolfie.combatlog.config.ConfigManager;
import com.riotwolfie.combatlog.npc.CombatNpcManager;
import com.riotwolfie.combatlog.util.ChatColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public class PlayerQuitListener implements Listener {

    private final CombatLogPlugin plugin;
    private final CombatManager combatManager;
    private final ConfigManager configManager;
    private final CombatNpcManager combatNpcManager;

    public PlayerQuitListener(
            CombatLogPlugin plugin,
            CombatManager combatManager,
            ConfigManager configManager,
            CombatNpcManager combatNpcManager
    ) {
        this.plugin = plugin;
        this.combatManager = combatManager;
        this.configManager = configManager;
        this.combatNpcManager = combatNpcManager;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        if (player.hasPermission("combatlog.bypass") && !player.isOp()) {
            combatManager.untagPlayer(player);
            return;
        }

        if (combatManager.isInCombat(player)) {
            UUID playerUuid = player.getUniqueId();
            String playerName = player.getName();
            org.bukkit.Location quitLoc = player.getLocation().clone();
            PlayerProfile profile = player.getPlayerProfile();
            Optional<CombatTag> optTag = combatManager.getCombatTag(playerUuid);
            String opponentName = null;
            if (optTag.isPresent() && optTag.get().opponentUuid() != null) {
                Player opponent = Bukkit.getPlayer(optTag.get().opponentUuid());
                if (opponent != null) {
                    opponentName = opponent.getName();
                }
            }

            org.bukkit.inventory.ItemStack[] storage = cloneItemArray(player.getInventory().getStorageContents());
            org.bukkit.inventory.ItemStack[] armor = cloneItemArray(player.getInventory().getArmorContents());
            org.bukkit.inventory.ItemStack offHand = cloneOrNull(player.getInventory().getItemInOffHand());
            double health = player.getHealth();
            double maxHealth = resolveMaxHealth(player);
            double absorption = player.getAbsorptionAmount();
            int exp = Math.min(100, player.getLevel() * 7);
            Collection<PotionEffect> effects = new ArrayList<>(player.getActivePotionEffects());
            int fireTicks = player.getFireTicks();

            String textureValue = null;
            String textureSignature = null;
            try {
                Object serverPlayer = player.getClass().getMethod("getHandle").invoke(player);
                Object originalProfile = serverPlayer.getClass().getMethod("getGameProfile").invoke(serverPlayer);
                Method propsMethod = null;
                try {
                    propsMethod = originalProfile.getClass().getMethod("properties");
                } catch (NoSuchMethodException e) {
                    try {
                        propsMethod = originalProfile.getClass().getMethod("getProperties");
                    } catch (NoSuchMethodException ignored) {
                    }
                }
                if (propsMethod == null) {
                    for (Method m : originalProfile.getClass().getMethods()) {
                        if (m.getParameterCount() == 0 && m.getReturnType().getSimpleName().contains("PropertyMap")) {
                            propsMethod = m;
                            break;
                        }
                    }
                }
                if (propsMethod != null) {
                    Object propMap = propsMethod.invoke(originalProfile);
                    Method getMethod = null;
                    for (Method m : propMap.getClass().getMethods()) {
                        if (m.getName().equals("get") && m.getParameterCount() == 1) {
                            getMethod = m;
                            break;
                        }
                    }
                    if (getMethod != null) {
                        Collection<?> props = (Collection<?>) getMethod.invoke(propMap, "textures");
                        if (props != null && !props.isEmpty()) {
                            Object first = props.iterator().next();
                            try {
                                textureValue = (String) first.getClass().getMethod("value").invoke(first);
                            } catch (Throwable t) {
                                try {
                                    textureValue = (String) first.getClass().getMethod("getValue").invoke(first);
                                } catch (Throwable ignored) {
                                }
                            }
                            try {
                                textureSignature = (String) first.getClass().getMethod("signature").invoke(first);
                            } catch (Throwable t) {
                                try {
                                    textureSignature = (String) first.getClass().getMethod("getSignature").invoke(first);
                                } catch (Throwable ignored) {
                                }
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {
            }

            if (textureValue == null && profile != null) {
                for (ProfileProperty prop : profile.getProperties()) {
                    if ("textures".equalsIgnoreCase(prop.getName())) {
                        textureValue = prop.getValue();
                        textureSignature = prop.getSignature();
                        break;
                    }
                }
            }

            String action;
            if (configManager.isKillOnQuit()) {
                player.setHealth(0.0);
                action = "Killed";

                if (configManager.isBroadcastQuitPunishment()) {
                    String rawMsg = configManager.getCombatQuitPunishMessage()
                            .replace("%player%", player.getName());
                    Bukkit.broadcast(ChatColorUtil.format(rawMsg));
                }
                combatManager.untagPlayer(player);
            } else {
                action = "Spawned NPC";
                plugin.getLogger().info("Player " + playerName + " quit in combat. Scheduling NPC spawn at " + quitLoc.getBlockX() + ", " + quitLoc.getBlockY() + ", " + quitLoc.getBlockZ());
                CombatTag capturedTag = optTag.orElse(null);
                final String skinVal = textureValue;
                final String skinSig = textureSignature;
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    combatNpcManager.spawnNpc(
                            playerUuid,
                            playerName,
                            quitLoc,
                            profile,
                            skinVal,
                            skinSig,
                            capturedTag,
                            storage,
                            armor,
                            offHand,
                            exp,
                            health,
                            maxHealth,
                            absorption,
                            effects,
                            fireTicks
                    );
                }, 1L);
            }

            plugin.getDiscordWebhookService().logCombatQuit(player, opponentName, action);
        }
    }

    private double resolveMaxHealth(Player player) {
        Attribute healthAttr = CombatNpcManager.resolveMaxHealthAttribute();
        if (healthAttr != null) {
            AttributeInstance instance = player.getAttribute(healthAttr);
            if (instance != null) {
                return instance.getValue();
            }
        }
        return 20.0;
    }

    private org.bukkit.inventory.ItemStack cloneOrNull(org.bukkit.inventory.ItemStack item) {
        return (item != null && !item.getType().isAir()) ? item.clone() : null;
    }

    private org.bukkit.inventory.ItemStack[] cloneItemArray(org.bukkit.inventory.ItemStack[] array) {
        if (array == null) {
            return new org.bukkit.inventory.ItemStack[0];
        }
        org.bukkit.inventory.ItemStack[] cloned = new org.bukkit.inventory.ItemStack[array.length];
        for (int i = 0; i < array.length; i++) {
            cloned[i] = cloneOrNull(array[i]);
        }
        return cloned;
    }
}
