package com.rennis.farlandspearlcannon.launch;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * The cannon only flies players to the edge; the Far Lands terrain itself comes from Farlands Reforged. When that mod
 * is missing, players are told so instead of landing in ordinary terrain with no explanation. The server is the side
 * that generates terrain, so this is checked there.
 */
public final class FarlandsCompat {
    public static final String REFORGED_MOD_ID = "farlandsreforged";

    private FarlandsCompat() {}

    /** Also warns once each time a player joins, so they find out before gathering anything for the cannon. */
    public static void initialize() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> warnIfReforgedMissing(handler.getPlayer(), "login"));
    }

    public static boolean isReforgedLoaded() {
        return FabricLoader.getInstance().isModLoaded(REFORGED_MOD_ID);
    }

    /** Sends the "Farlands Reforged is missing" chat warning ({@code login}, {@code built} or {@code landed}) if the mod is not loaded. */
    public static void warnIfReforgedMissing(ServerPlayer player, String moment) {
        if (isReforgedLoaded()) return;
        player.sendSystemMessage(Component.translatable("message.farlands_pearl_cannon.reforged_missing." + moment)
                .withStyle(ChatFormatting.GOLD));
    }
}
