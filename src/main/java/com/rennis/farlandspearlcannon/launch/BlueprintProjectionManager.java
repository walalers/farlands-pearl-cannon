package com.rennis.farlandspearlcannon.launch;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import com.rennis.farlandspearlcannon.advancement.ModAdvancements;
import com.rennis.farlandspearlcannon.block.custom.FarlandsCannonCoreBlock;
import com.rennis.farlandspearlcannon.item.ModItems;

/**
 * The in-world guide is drawn entirely client-side (see BlueprintGuideClient), so it works from either
 * hand without any link step. This server side only watches for the build being finished so it can grant
 * the advancement and fire a celebration everyone can see.
 */
public class BlueprintProjectionManager {
    private static final int SCAN_RADIUS = 8;
    private static final int CHECK_TICKS = 20;
    private static final Map<UUID, Boolean> CELEBRATED = new HashMap<>();

    public static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(BlueprintProjectionManager::tick);
    }

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % CHECK_TICKS != 0) return;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            boolean holding = player.getMainHandItem().is(ModItems.BLUEPRINT_PROJECTOR)
                    || player.getOffhandItem().is(ModItems.BLUEPRINT_PROJECTOR);
            if (!holding) {
                CELEBRATED.remove(player.getUUID());
                continue;
            }

            ServerLevel level = (ServerLevel) player.level();
            BlockPos core = CannonStructure.findCore(level, player.blockPosition(), SCAN_RADIUS);
            if (core == null) {
                CELEBRATED.remove(player.getUUID());
                continue;
            }

            Direction facing = level.getBlockState(core).getValue(FarlandsCannonCoreBlock.FACING);
            boolean complete = CannonStructure.validate(level, core, facing).valid();
            boolean alreadyCelebrated = CELEBRATED.getOrDefault(player.getUUID(), false);

            if (complete) {
                ModAdvancements.grant(server, player, "far_traveler");
                if (!alreadyCelebrated) {
                    CELEBRATED.put(player.getUUID(), true);
                    celebrate(level, player, core);
                }
            } else {
                CELEBRATED.put(player.getUUID(), false);
            }
        }
    }

    private static void celebrate(ServerLevel level, ServerPlayer player, BlockPos core) {
        double cx = core.getX() + 0.5;
        double cy = core.getY() + 1.0;
        double cz = core.getZ() + 0.5;
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, cx, cy, cz, 70, 0.7, 0.9, 0.7, 0.3);
        level.sendParticles(ParticleTypes.END_ROD, cx, cy, cz, 25, 0.45, 0.6, 0.45, 0.08);
        level.sendParticles(ParticleTypes.FIREWORK, cx, cy, cz, 30, 0.5, 0.5, 0.5, 0.2);
        level.playSound(null, core, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 0.8F, 1.2F);
        level.playSound(null, core, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 0.7F, 1.0F);
        player.sendOverlayMessage(Component.literal("✦ Cannon complete — power it with redstone, then fire!")
                .withStyle(ChatFormatting.GREEN));
    }
}
