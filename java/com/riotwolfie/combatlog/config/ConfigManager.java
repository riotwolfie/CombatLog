package com.riotwolfie.combatlog.config;

import com.riotwolfie.combatlog.CombatLogPlugin;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ConfigManager {

    private final CombatLogPlugin plugin;

    private int combatDurationSeconds;
    private boolean killOnQuit;
    private boolean broadcastQuitPunishment;
    private boolean blockCommands;
    private List<String> blockedCommands = Collections.emptyList();

    private int npcDurationSeconds;
    private String npcNameFormat;
    private String npcKilledBroadcast;
    private String npcKilledBroadcastUnknown;
    private String npcRejoinNotice;

    private boolean webhookEnabled;
    private String webhookUrl;
    private String webhookUsername;
    private String webhookAvatarUrl;

    private String combatEnterMessage;
    private String combatExpiredMessage;
    private String combatQuitPunishMessage;
    private String commandBlockedMessage;
    private String trustDisabledMessage;
    private String trustAddedMessage;
    private String trustRemovedMessage;
    private String trustSelfMessage;
    private String trustNotFoundMessage;
    private String trustListEmptyMessage;
    private String trustListHeaderMessage;

    public ConfigManager(CombatLogPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadConfig() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();

        this.combatDurationSeconds = config.getInt("combat.duration-seconds", 15);
        this.killOnQuit = config.getBoolean("punishment.kill-on-quit", false);
        this.broadcastQuitPunishment = config.getBoolean("punishment.broadcast-message", true);
        this.blockCommands = config.getBoolean("combat.block-commands", true);

        List<String> rawBlocked = config.getStringList("combat.blocked-commands");
        List<String> normalizedBlocked = new ArrayList<>(rawBlocked.size());
        for (String raw : rawBlocked) {
            if (raw != null) {
                String trimmed = raw.trim().toLowerCase();
                if (trimmed.startsWith("/")) {
                    trimmed = trimmed.substring(1).trim();
                }
                if (!trimmed.isEmpty()) {
                    normalizedBlocked.add(trimmed);
                }
            }
        }
        this.blockedCommands = Collections.unmodifiableList(normalizedBlocked);

        this.npcDurationSeconds = config.getInt("npc.duration-seconds", 15);
        this.npcNameFormat = config.getString("npc.name-format", "%player%");
        this.npcKilledBroadcast = config.getString("npc.killed-broadcast", "&c%player%'s combat NPC was eliminated by &e%killer%&c!");
        this.npcKilledBroadcastUnknown = config.getString("npc.killed-broadcast-unknown", "&c%player%'s combat NPC was eliminated!");
        this.npcRejoinNotice = config.getString("npc.rejoin-notice", "&eYou rejoined before your combat NPC expired! Your health has been synchronized and you are still in combat.");

        this.webhookEnabled = config.getBoolean("webhook.enabled", false);
        this.webhookUrl = config.getString("webhook.url", "");
        this.webhookUsername = config.getString("webhook.username", "CombatLog Watcher");
        this.webhookAvatarUrl = config.getString("webhook.avatar-url", "");

        this.combatEnterMessage = config.getString("messages.combat-enter", "&cYou are now in combat with &e%opponent%&c! Do not disconnect for &e%seconds%s&c.");
        this.combatExpiredMessage = config.getString("messages.combat-expired", "&aYou are no longer in combat.");
        this.combatQuitPunishMessage = config.getString("messages.combat-quit-punish", "&c%player% logged out during combat and was eliminated!");
        this.commandBlockedMessage = config.getString("messages.command-blocked", "&cYou cannot execute that command while in combat!");
        this.trustDisabledMessage = config.getString("messages.trust-disabled", "&cThe trust system is disabled because BetterTeams is managing team combat.");
        this.trustAddedMessage = config.getString("messages.trust-added", "&aYou now trust &e%player%&a. Your attacks against them will not tag either of you in combat.");
        this.trustRemovedMessage = config.getString("messages.trust-removed", "&cYou no longer trust &e%player%&c.");
        this.trustSelfMessage = config.getString("messages.trust-self", "&cYou cannot add yourself to your trust list.");
        this.trustNotFoundMessage = config.getString("messages.trust-not-found", "&cPlayer not found.");
        this.trustListEmptyMessage = config.getString("messages.trust-list-empty", "&7You do not trust any players.");
        this.trustListHeaderMessage = config.getString("messages.trust-list-header", "&6Trusted players: &e%players%");
    }

    public int getCombatDurationSeconds() {
        return combatDurationSeconds;
    }

    public boolean isKillOnQuit() {
        return killOnQuit;
    }

    public boolean isBroadcastQuitPunishment() {
        return broadcastQuitPunishment;
    }

    public boolean isBlockCommands() {
        return blockCommands;
    }

    public List<String> getBlockedCommands() {
        return blockedCommands;
    }

    public int getNpcDurationSeconds() {
        return npcDurationSeconds;
    }

    public String getNpcNameFormat() {
        return npcNameFormat;
    }

    public String getNpcKilledBroadcast() {
        return npcKilledBroadcast;
    }

    public String getNpcKilledBroadcastUnknown() {
        return npcKilledBroadcastUnknown;
    }

    public String getNpcRejoinNotice() {
        return npcRejoinNotice;
    }

    public boolean isWebhookEnabled() {
        return webhookEnabled;
    }

    public String getWebhookUrl() {
        return webhookUrl;
    }

    public String getWebhookUsername() {
        return webhookUsername;
    }

    public String getWebhookAvatarUrl() {
        return webhookAvatarUrl;
    }

    public String getCombatEnterMessage() {
        return combatEnterMessage;
    }

    public String getCombatExpiredMessage() {
        return combatExpiredMessage;
    }

    public String getCombatQuitPunishMessage() {
        return combatQuitPunishMessage;
    }

    public String getCommandBlockedMessage() {
        return commandBlockedMessage;
    }

    public String getTrustDisabledMessage() {
        return trustDisabledMessage;
    }

    public String getTrustAddedMessage() {
        return trustAddedMessage;
    }

    public String getTrustRemovedMessage() {
        return trustRemovedMessage;
    }

    public String getTrustSelfMessage() {
        return trustSelfMessage;
    }

    public String getTrustNotFoundMessage() {
        return trustNotFoundMessage;
    }

    public String getTrustListEmptyMessage() {
        return trustListEmptyMessage;
    }

    public String getTrustListHeaderMessage() {
        return trustListHeaderMessage;
    }
}
