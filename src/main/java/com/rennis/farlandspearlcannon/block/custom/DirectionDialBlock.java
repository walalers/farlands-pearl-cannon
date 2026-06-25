package com.rennis.farlandspearlcannon.block.custom;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import com.rennis.farlandspearlcannon.block.entity.FarlandsCannonCoreBlockEntity;
import com.rennis.farlandspearlcannon.launch.CannonStructure;
import com.rennis.farlandspearlcannon.network.OpenDialPayload;

public class DirectionDialBlock extends Block {
    public DirectionDialBlock(Properties settings) {
        super(settings);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            BlockPos corePos = CannonStructure.findCore(level, pos, 3);
            if (corePos == null || !(level.getBlockEntity(corePos) instanceof FarlandsCannonCoreBlockEntity cannon)) {
                level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, 0.6F, 0.7F);
                player.sendOverlayMessage(Component.literal("Mount the dial on a built cannon to aim it."));
                return InteractionResult.SUCCESS;
            }
            level.playSound(null, pos, SoundEvents.COMPARATOR_CLICK, SoundSource.BLOCKS, 0.65F, 1.1F);
            ServerPlayNetworking.send(serverPlayer, new OpenDialPayload(corePos, cannon.getTarget().ordinal()));
        }
        return InteractionResult.SUCCESS;
    }
}
