package com.riotwolfie.combatlog.webhook;

import com.riotwolfie.combatlog.CombatLogPlugin;
import com.riotwolfie.combatlog.config.ConfigManager;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class DiscordWebhookService {

    private final CombatLogPlugin plugin;
    private final ConfigManager configManager;
    private final HttpClient httpClient;

    public DiscordWebhookService(CombatLogPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public void logCombatQuit(
            @NotNull Player player,
            @Nullable String opponentName,
            @NotNull String action
    ) {
        if (!configManager.isWebhookEnabled() || configManager.getWebhookUrl().isBlank()) {
            return;
        }

        String player1 = player.getName();
        String player2 = (opponentName != null && !opponentName.isBlank()) ? opponentName : "an opponent";
        String messageContent = "**" + player1 + " Combat Logged while trying to escape " + player2 + "**\n" + action;

        sendAsync(buildPayloadJson(messageContent));
    }

    public void logNpcSlain(
            @NotNull String playerName,
            @Nullable String killerName
    ) {
        if (!configManager.isWebhookEnabled() || configManager.getWebhookUrl().isBlank()) {
            return;
        }

        String killer = (killerName != null && !killerName.isBlank()) ? killerName : "an opponent";
        String messageContent = "**" + playerName + "'s Combat NPC was killed by " + killer + "**";

        sendAsync(buildPayloadJson(messageContent));
    }

    private void sendAsync(String jsonPayload) {
        CompletableFuture.runAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(configManager.getWebhookUrl()))
                        .header("Content-Type", "application/json; charset=utf-8")
                        .timeout(Duration.ofSeconds(5))
                        .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                        .build();

                httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                        .thenAccept(response -> {
                            if (response.statusCode() >= 400) {
                                plugin.getLogger().warning("Discord webhook returned HTTP " + response.statusCode());
                            }
                        })
                        .exceptionally(ex -> {
                            plugin.getLogger().log(Level.WARNING, "Failed to dispatch Discord webhook: " + ex.getMessage());
                            return null;
                        });
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to construct Discord webhook request: " + e.getMessage());
            }
        });
    }

    private String buildPayloadJson(String content) {
        String botName = escape(configManager.getWebhookUsername());
        String avatarUrl = escape(configManager.getWebhookAvatarUrl());

        return "{"
                + "\"username\":\"" + botName + "\","
                + (avatarUrl.isEmpty() ? "" : "\"avatar_url\":\"" + avatarUrl + "\",")
                + "\"content\":\"" + escape(content) + "\""
                + "}";
    }

    private static String escape(String raw) {
        if (raw == null) return "";
        return raw.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
