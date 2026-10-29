package com.riotwolfie.combatlog.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.jetbrains.annotations.NotNull;

public final class ChatColorUtil {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer AMPERSAND_SERIALIZER =
            LegacyComponentSerializer.builder().character('&').hexColors().useUnusualXRepeatedCharacterHexFormat().build();

    private ChatColorUtil() {
    }

    @NotNull
    public static Component format(@NotNull String input) {
        if (input.contains("&")) {
            return AMPERSAND_SERIALIZER.deserialize(input);
        }
        return MINI_MESSAGE.deserialize(input);
    }
}
