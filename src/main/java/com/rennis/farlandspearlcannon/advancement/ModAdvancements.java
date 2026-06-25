package com.rennis.farlandspearlcannon.advancement;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import com.rennis.farlandspearlcannon.FarlandsPearlCannonMod;

public class ModAdvancements {
    public static void grant(MinecraftServer server, ServerPlayer player, String path) {
        var advancement = server.getAdvancements().get(FarlandsPearlCannonMod.id(path));
        if (advancement != null) {
            var progress = player.getAdvancements().getOrStartProgress(advancement);
            for (String criterion : progress.getRemainingCriteria()) {
                player.getAdvancements().award(advancement, criterion);
            }
        }
    }
}
