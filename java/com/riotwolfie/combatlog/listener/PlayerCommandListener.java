package com.riotwolfie.combatlog.listener;

import com.riotwolfie.combatlog.CombatLogPlugin;
import com.riotwolfie.combatlog.api.CombatManager;
import com.riotwolfie.combatlog.config.ConfigManager;
import com.riotwolfie.combatlog.util.ChatColorUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.List;

public class PlayerCommandListener implements Listener {

    private final CombatLogPlugin plugin;
    private final CombatManager combatManager;
    private final ConfigManager configManager;

    public PlayerCommandListener(CombatLogPlugin plugin, CombatManager combatManager, ConfigManager configManager) {
        this.plugin = plugin;
        this.combatManager = combatManager;
        this.configManager = configManager;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();

        if (!configManager.isBlockCommands() || player.hasPermission("combatlog.bypass")) {
            return;
        }

        if (!combatManager.isInCombat(player)) {
            return;
        }

        String raw = event.getMessage();
        if (raw.startsWith("/")) {
            raw = raw.substring(1);
        }
        String clean = raw.trim().toLowerCase();

        int colon = clean.indexOf(':');
        int space = clean.indexOf(' ');
        if (colon != -1 && (space == -1 || colon < space)) {
            clean = clean.substring(colon + 1);
        }

        List<String> blockedList = configManager.getBlockedCommands();
        for (String blocked : blockedList) {
            if (clean.equals(blocked) || clean.startsWith(blocked + " ")) {
                event.setCancelled(true);
                player.sendMessage(ChatColorUtil.format(configManager.getCommandBlockedMessage()));
                return;
            }
        }
    }
}
