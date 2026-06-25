package com.rennis.farlandspearlcannon.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.rennis.farlandspearlcannon.FarlandsPearlCannonMod;

/** Server -> client: tells the player to open the launch-direction menu for the core at {@code corePos}. */
public record OpenDialPayload(BlockPos corePos, int targetIndex) implements CustomPacketPayload {
    public static final Type<OpenDialPayload> TYPE = new Type<>(FarlandsPearlCannonMod.id("open_dial"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenDialPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, OpenDialPayload::corePos,
            ByteBufCodecs.VAR_INT, OpenDialPayload::targetIndex,
            OpenDialPayload::new);

    @Override
    public Type<OpenDialPayload> type() {
        return TYPE;
    }
}
