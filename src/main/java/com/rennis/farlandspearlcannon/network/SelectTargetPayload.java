package com.rennis.farlandspearlcannon.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.rennis.farlandspearlcannon.FarlandsPearlCannonMod;

/** Client -> server: the player picked a launch direction in the menu for the core at {@code corePos}. */
public record SelectTargetPayload(BlockPos corePos, int targetIndex) implements CustomPacketPayload {
    public static final Type<SelectTargetPayload> TYPE = new Type<>(FarlandsPearlCannonMod.id("select_target"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SelectTargetPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SelectTargetPayload::corePos,
            ByteBufCodecs.VAR_INT, SelectTargetPayload::targetIndex,
            SelectTargetPayload::new);

    @Override
    public Type<SelectTargetPayload> type() {
        return TYPE;
    }
}
