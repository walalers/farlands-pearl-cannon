package com.rennis.farlandspearlcannon.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import com.rennis.farlandspearlcannon.block.entity.FarlandsCannonCoreBlockEntity;
import com.rennis.farlandspearlcannon.launch.FarlandsTarget;

/** Registers the launch-direction menu packets and applies the player's selection on the server. */
public final class ModNetworking {
    private ModNetworking() {}

    public static void initialize() {
        PayloadTypeRegistry.clientboundPlay().register(OpenDialPayload.TYPE, OpenDialPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(PinBlueprintPayload.TYPE, PinBlueprintPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SelectTargetPayload.TYPE, SelectTargetPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(SelectTargetPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> applySelection(player, payload));
        });
    }

    private static void applySelection(ServerPlayer player, SelectTargetPayload payload) {
        if (!(player.level() instanceof ServerLevel level)) return;
        BlockPos corePos = payload.corePos();
        // Guard against a stale/forged packet: the player must be next to the core they are aiming.
        if (!corePos.closerThan(player.blockPosition(), 10.0)) return;
        if (!(level.getBlockEntity(corePos) instanceof FarlandsCannonCoreBlockEntity cannon)) return;

        FarlandsTarget[] all = FarlandsTarget.values();
        int index = Math.floorMod(payload.targetIndex(), all.length);
        cannon.setTargetIndex(index);

        level.playSound(null, corePos, SoundEvents.COMPARATOR_CLICK, SoundSource.BLOCKS, 0.7F, 1.3F);
        player.sendOverlayMessage(Component.translatable("message.farlands_pearl_cannon.direction.set", all[index].label())
                .withStyle(ChatFormatting.AQUA));
    }
}
