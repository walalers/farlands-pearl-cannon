package com.rennis.farlandspearlcannon.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.rennis.farlandspearlcannon.FarlandsPearlCannonMod;

/**
 * Server -> client: the server placed a blueprint pin for this player, so the client guide shows exactly the
 * same spot and heading (the server is the only side that decides whether a click pins).
 */
public record PinBlueprintPayload(BlockPos core, int facing) implements CustomPacketPayload {
    public static final Type<PinBlueprintPayload> TYPE = new Type<>(FarlandsPearlCannonMod.id("pin_blueprint"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PinBlueprintPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, PinBlueprintPayload::core,
            ByteBufCodecs.VAR_INT, PinBlueprintPayload::facing,
            PinBlueprintPayload::new);

    @Override
    public Type<PinBlueprintPayload> type() {
        return TYPE;
    }
}
