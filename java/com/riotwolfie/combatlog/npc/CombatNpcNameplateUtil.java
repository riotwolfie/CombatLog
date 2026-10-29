package com.riotwolfie.combatlog.npc;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

public class CombatNpcNameplateUtil {

    private static final CombatNpcNameplateUtil INSTANCE = new CombatNpcNameplateUtil();

    public static CombatNpcNameplateUtil get() {
        return INSTANCE;
    }

    public void updateNPCNameplate(
            @Nullable TextDisplay display,
            @NotNull String playerName,
            double baseHealth,
            double absorption
    ) {
        if (display == null) {
            return;
        }

        double finalHealthValue;
        String colorCode;

        if (absorption > 0.0) {
            finalHealthValue = Math.max(0.0, baseHealth) + absorption;
            colorCode = "§e";
        } else {
            finalHealthValue = Math.max(0.0, baseHealth);
            colorCode = "§c";
        }

        String line1 = "§f" + playerName;
        String line2 = colorCode + String.format(Locale.US, "%.1f", finalHealthValue) + " HP";
        String fullDisplay = line1 + "\n" + line2;

        display.text(LegacyComponentSerializer.legacySection().deserialize(fullDisplay));
    }

    @Nullable
    public TextDisplay spawnNPCNameplate(
            @NotNull Location location,
            @NotNull String playerName,
            double baseHealth,
            double absorption
    ) {
        if (location.getWorld() == null) {
            return null;
        }

        Location displayLoc = location.clone().add(0, 2.1, 0);
        TextDisplay display = location.getWorld().spawn(displayLoc, TextDisplay.class, d -> {
            d.setBillboard(Display.Billboard.CENTER);
            d.setDefaultBackground(true);
            d.setAlignment(TextDisplay.TextAlignment.CENTER);
            d.setShadowed(true);
            updateNPCNameplate(d, playerName, baseHealth, absorption);
        });
        if (display != null) {
            updateNPCNameplate(display, playerName, baseHealth, absorption);
        }
        return display;
    }
}
