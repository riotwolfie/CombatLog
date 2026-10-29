package com.riotwolfie.combatlog.command;

import com.riotwolfie.combatlog.CombatLogPlugin;
import com.riotwolfie.combatlog.api.CombatManager;
import com.riotwolfie.combatlog.config.ConfigManager;
import com.riotwolfie.combatlog.util.ChatColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class CombatLogCommand implements CommandExecutor, TabCompleter {

    private final CombatLogPlugin plugin;
    private final CombatManager combatManager;
    private final ConfigManager configManager;

    public CombatLogCommand(CombatLogPlugin plugin, CombatManager combatManager, ConfigManager configManager) {
        this.plugin = plugin;
        this.combatManager = combatManager;
        this.configManager = configManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            handleStatus(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("combatlog.admin")) {
                sender.sendMessage(ChatColorUtil.format("&cYou do not have permission to use this command."));
                return true;
            }
            configManager.loadConfig();
            sender.sendMessage(ChatColorUtil.format("&aCombatLog configuration reloaded successfully!"));
            return true;
        }

        if (args[0].equalsIgnoreCase("untag")) {
            if (!sender.hasPermission("combatlog.admin")) {
                sender.sendMessage(ChatColorUtil.format("&cYou do not have permission to use this command."));
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage(ChatColorUtil.format("&cUsage: /" + label + " untag <player>"));
                return true;
            }
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(ChatColorUtil.format("&cPlayer not found or offline: " + args[1]));
                return true;
            }
            combatManager.untagPlayer(target);
            sender.sendMessage(ChatColorUtil.format("&aUntagged player &e" + target.getName() + "&a from combat."));
            target.sendMessage(ChatColorUtil.format("&aYou were untagged from combat by an administrator."));
            return true;
        }

        if (args[0].equalsIgnoreCase("trust")) {
            handleTrust(sender, label, args);
            return true;
        }

        sender.sendMessage(ChatColorUtil.format("&6=== CombatLog Commands ==="));
        sender.sendMessage(ChatColorUtil.format("&e/" + label + " status &7- Check your combat status"));
        if (!plugin.isBetterTeamsAvailable()) {
            sender.sendMessage(ChatColorUtil.format("&e/" + label + " trust <add|remove|list> [player] &7- Manage trusted players"));
        }
        if (sender.hasPermission("combatlog.admin")) {
            sender.sendMessage(ChatColorUtil.format("&e/" + label + " reload &7- Reload plugin configuration"));
            sender.sendMessage(ChatColorUtil.format("&e/" + label + " untag <player> &7- Untag a player from combat"));
        }
        return true;
    }

    private void handleStatus(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColorUtil.format("&cOnly players can check personal combat status."));
            return;
        }

        if (combatManager.isInCombat(player)) {
            long remaining = combatManager.getRemainingSeconds(player.getUniqueId());
            player.sendMessage(ChatColorUtil.format("&cYou are currently in combat! &e" + remaining + "s &cremaining."));
        } else {
            player.sendMessage(ChatColorUtil.format("&aYou are not currently in combat."));
        }
    }

    private void handleTrust(CommandSender sender, String label, String[] args) {
        if (plugin.isBetterTeamsAvailable()) {
            sender.sendMessage(ChatColorUtil.format(configManager.getTrustDisabledMessage()));
            return;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColorUtil.format("&cOnly players can manage trusted friends."));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColorUtil.format("&cUsage: /" + label + " trust <add|remove|list> [player]"));
            return;
        }

        String sub = args[1].toLowerCase();
        if (sub.equals("add")) {
            if (args.length < 3) {
                sender.sendMessage(ChatColorUtil.format("&cUsage: /" + label + " trust add <player>"));
                return;
            }
            Player target = Bukkit.getPlayer(args[2]);
            if (target == null) {
                sender.sendMessage(ChatColorUtil.format(configManager.getTrustNotFoundMessage()));
                return;
            }
            if (player.getUniqueId().equals(target.getUniqueId())) {
                sender.sendMessage(ChatColorUtil.format(configManager.getTrustSelfMessage()));
                return;
            }
            plugin.getTrustManager().addTrust(player.getUniqueId(), target.getUniqueId());
            String msg = configManager.getTrustAddedMessage().replace("%player%", target.getName());
            player.sendMessage(ChatColorUtil.format(msg));
            return;
        }

        if (sub.equals("remove")) {
            if (args.length < 3) {
                sender.sendMessage(ChatColorUtil.format("&cUsage: /" + label + " trust remove <player>"));
                return;
            }
            UUID foundUuid = null;
            String foundName = args[2];
            Player onlineTarget = Bukkit.getPlayer(args[2]);
            if (onlineTarget != null) {
                foundUuid = onlineTarget.getUniqueId();
                foundName = onlineTarget.getName();
            } else {
                for (UUID u : plugin.getTrustManager().getTrusted(player.getUniqueId())) {
                    OfflinePlayer op = Bukkit.getOfflinePlayer(u);
                    if (op.getName() != null && op.getName().equalsIgnoreCase(args[2])) {
                        foundUuid = u;
                        foundName = op.getName();
                        break;
                    }
                }
            }

            if (foundUuid == null || !plugin.getTrustManager().removeTrust(player.getUniqueId(), foundUuid)) {
                sender.sendMessage(ChatColorUtil.format(configManager.getTrustNotFoundMessage()));
                return;
            }

            String msg = configManager.getTrustRemovedMessage().replace("%player%", foundName);
            player.sendMessage(ChatColorUtil.format(msg));
            return;
        }

        if (sub.equals("list")) {
            Set<UUID> set = plugin.getTrustManager().getTrusted(player.getUniqueId());
            if (set.isEmpty()) {
                player.sendMessage(ChatColorUtil.format(configManager.getTrustListEmptyMessage()));
                return;
            }
            String names = set.stream()
                    .map(u -> {
                        Player p = Bukkit.getPlayer(u);
                        if (p != null) return p.getName();
                        OfflinePlayer op = Bukkit.getOfflinePlayer(u);
                        return op.getName();
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining("&7, &e"));

            String header = configManager.getTrustListHeaderMessage().replace("%players%", names);
            player.sendMessage(ChatColorUtil.format(header));
            return;
        }

        sender.sendMessage(ChatColorUtil.format("&cUsage: /" + label + " trust <add|remove|list> [player]"));
    }

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            List<String> subcommands = new ArrayList<>();
            subcommands.add("status");
            if (!plugin.isBetterTeamsAvailable()) {
                subcommands.add("trust");
            }
            if (sender.hasPermission("combatlog.admin")) {
                subcommands.add("reload");
                subcommands.add("untag");
            }
            for (String sub : subcommands) {
                if (sub.toLowerCase().startsWith(args[0].toLowerCase())) {
                    completions.add(sub);
                }
            }
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("untag") && sender.hasPermission("combatlog.admin")) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                        completions.add(player.getName());
                    }
                }
            } else if (args[0].equalsIgnoreCase("trust") && !plugin.isBetterTeamsAvailable()) {
                List<String> trustSubs = List.of("add", "remove", "list");
                for (String s : trustSubs) {
                    if (s.toLowerCase().startsWith(args[1].toLowerCase())) {
                        completions.add(s);
                    }
                }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("trust") && !plugin.isBetterTeamsAvailable()) {
            if (args[1].equalsIgnoreCase("add") && sender instanceof Player p) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (!player.getUniqueId().equals(p.getUniqueId()) && player.getName().toLowerCase().startsWith(args[2].toLowerCase())) {
                        completions.add(player.getName());
                    }
                }
            } else if (args[1].equalsIgnoreCase("remove") && sender instanceof Player p) {
                for (UUID u : plugin.getTrustManager().getTrusted(p.getUniqueId())) {
                    Player op = Bukkit.getPlayer(u);
                    String name = op != null ? op.getName() : Bukkit.getOfflinePlayer(u).getName();
                    if (name != null && name.toLowerCase().startsWith(args[2].toLowerCase())) {
                        completions.add(name);
                    }
                }
            }
        }
        return completions;
    }
}
