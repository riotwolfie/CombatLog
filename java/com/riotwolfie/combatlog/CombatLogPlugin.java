package com.riotwolfie.combatlog;

import com.riotwolfie.combatlog.command.CombatLogCommand;
import com.riotwolfie.combatlog.config.ConfigManager;
import com.riotwolfie.combatlog.listener.CombatListener;
import com.riotwolfie.combatlog.listener.PlayerCommandListener;
import com.riotwolfie.combatlog.listener.PlayerQuitListener;
import com.riotwolfie.combatlog.manager.CombatManagerImpl;
import com.riotwolfie.combatlog.npc.CombatNpcManager;
import com.riotwolfie.combatlog.trust.TrustManager;
import com.riotwolfie.combatlog.webhook.DiscordWebhookService;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public final class CombatLogPlugin extends JavaPlugin {

    private static CombatLogPlugin instance;
    private ConfigManager configManager;
    private CombatManagerImpl combatManager;
    private CombatNpcManager combatNpcManager;
    private DiscordWebhookService discordWebhookService;
    private TrustManager trustManager;
    private boolean betterTeamsAvailable;

    @Override
    public void onEnable() {
        instance = this;

        this.configManager = new ConfigManager(this);
        this.configManager.loadConfig();

        this.betterTeamsAvailable = getServer().getPluginManager().isPluginEnabled("BetterTeams");
        this.trustManager = new TrustManager(this);

        this.combatManager = new CombatManagerImpl(this, configManager);
        this.combatNpcManager = new CombatNpcManager(this, configManager, combatManager);
        this.discordWebhookService = new DiscordWebhookService(this, configManager);

        registerListeners();
        registerCommands();

        getLogger().info("========================================");
        getLogger().info(" CombatLog v" + getDescription().getVersion() + " initialized successfully!");
        if (betterTeamsAvailable) {
            getLogger().info(" Hooks: BetterTeams (Friendly fire protection enabled)");
        } else {
            getLogger().info(" Hooks: Trust system active (/cl trust)");
        }
        getLogger().info(" NPC Logger: Native Player Model with Live HP Overlay active");
        getLogger().info("========================================");
    }

    @Override
    public void onDisable() {
        if (this.combatNpcManager != null) {
            this.combatNpcManager.despawnAll();
        }

        if (this.combatManager != null) {
            this.combatManager.shutdown();
        }

        if (this.trustManager != null) {
            this.trustManager.saveSync();
        }

        getLogger().info("CombatLog has been disabled.");
        instance = null;
    }

    private void registerListeners() {
        var pm = getServer().getPluginManager();
        pm.registerEvents(new CombatListener(this, combatManager), this);
        pm.registerEvents(new PlayerQuitListener(this, combatManager, configManager, combatNpcManager), this);
        pm.registerEvents(new PlayerCommandListener(this, combatManager, configManager), this);
    }

    private void registerCommands() {
        PluginCommand command = getCommand("combatlog");
        if (command != null) {
            CombatLogCommand executor = new CombatLogCommand(this, combatManager, configManager);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        } else {
            getLogger().log(Level.WARNING, "Failed to register /combatlog command.");
        }
    }

    public static CombatLogPlugin getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public CombatManagerImpl getCombatManager() {
        return combatManager;
    }

    public CombatNpcManager getCombatNpcManager() {
        return combatNpcManager;
    }

    public DiscordWebhookService getDiscordWebhookService() {
        return discordWebhookService;
    }

    public TrustManager getTrustManager() {
        return trustManager;
    }

    public boolean isBetterTeamsAvailable() {
        return betterTeamsAvailable;
    }
}