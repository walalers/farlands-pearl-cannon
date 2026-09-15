package com.rennis.farlandspearlcannon.advancement;

import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import com.rennis.farlandspearlcannon.FarlandsPearlCannonMod;
import com.rennis.farlandspearlcannon.item.CannonManual;

public class ModAdvancements {
    /**
     * The moment a player first picks up a Cannon Core or Blueprint Projector, they get the Field Manual too.
     * Called from PlayerAdvancementsMixin when an advancement becomes complete (Fabric has no earn event).
     */
    public static void onEarned(ServerPlayer player, AdvancementHolder advancement) {
        if (!advancement.id().equals(FarlandsPearlCannonMod.id("root"))) return;
        if (CannonManual.giveIfMissing(player)) {
            player.sendSystemMessage(Component.translatable("message.farlands_pearl_cannon.manual.granted").withStyle(ChatFormatting.GOLD));
        }
    }

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
