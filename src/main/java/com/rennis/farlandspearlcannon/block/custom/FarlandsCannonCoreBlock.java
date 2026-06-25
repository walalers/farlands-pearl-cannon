package com.rennis.farlandspearlcannon.block.custom;

import com.mojang.serialization.MapCodec;
import org.jetbrains.annotations.Nullable;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;

import com.rennis.farlandspearlcannon.advancement.ModAdvancements;
import com.rennis.farlandspearlcannon.block.entity.FarlandsCannonCoreBlockEntity;
import com.rennis.farlandspearlcannon.config.ModConfig;
import com.rennis.farlandspearlcannon.launch.CannonStructure;
import com.rennis.farlandspearlcannon.launch.LaunchManager;
import com.rennis.farlandspearlcannon.network.OpenDialPayload;

public class FarlandsCannonCoreBlock extends BaseEntityBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    public FarlandsCannonCoreBlock(Properties settings) {
        super(settings);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(FarlandsCannonCoreBlock::new);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FarlandsCannonCoreBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof FarlandsCannonCoreBlockEntity cannon)) return InteractionResult.PASS;

        if (stack.is(Items.ENDER_PEARL)) {
            if (!level.isClientSide() && cannon.loadPearl()) {
                if (!player.getAbilities().instabuild) stack.shrink(1);
                level.playSound(null, pos, SoundEvents.ENDERMAN_TELEPORT, SoundSource.BLOCKS, 0.45F, 1.35F);
                player.sendOverlayMessage(Component.literal("Ender pearl loaded."));
            }
            return InteractionResult.SUCCESS;
        }

        if (stack.is(Items.BLAZE_POWDER) || stack.is(Items.ENDER_EYE)) {
            if (!level.isClientSide() && cannon.addFuel(ModConfig.values.fuelRequired)) {
                if (!player.getAbilities().instabuild) stack.shrink(1);
                level.playSound(null, pos, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.BLOCKS, 0.55F, 1.2F);
                player.sendOverlayMessage(Component.literal("Cannon fuel: " + cannon.getFuel() + "/" + ModConfig.values.fuelRequired));
            }
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, Orientation orientation, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, orientation, movedByPiston);
        if (level.isClientSide() || !(level.getBlockEntity(pos) instanceof FarlandsCannonCoreBlockEntity cannon)) return;

        boolean powered = level.hasNeighborSignal(pos);
        if (powered && !cannon.wasPowered()) {
            fireFromRedstone(state, (ServerLevel) level, pos, cannon);
        }
        cannon.setWasPowered(powered);
    }

    private void fireFromRedstone(BlockState state, ServerLevel level, BlockPos pos, FarlandsCannonCoreBlockEntity cannon) {
        if (!CannonStructure.validate(level, pos, state.getValue(FACING)).valid()) return; // ignore a powered lever on a half-built cannon

        MinecraftServer server = level.getServer();
        ServerPlayer rider = null;
        double best = 8.0 * 8.0;
        for (ServerPlayer candidate : server.getPlayerList().getPlayers()) {
            if (candidate.level() != level) continue;
            double dist = candidate.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            if (dist < best) {
                best = dist;
                rider = candidate;
            }
        }
        if (rider == null) return; // nobody close enough to ride the shot

        ModAdvancements.grant(server, rider, "far_traveler");
        LaunchManager.tryStartLaunch(rider, level, pos, state.getValue(FACING), cannon);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof FarlandsCannonCoreBlockEntity cannon)) return InteractionResult.PASS;

        if (player.isShiftKeyDown()) {
            if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
                level.playSound(null, pos, SoundEvents.COMPARATOR_CLICK, SoundSource.BLOCKS, 0.65F, 1.2F);
                ServerPlayNetworking.send(serverPlayer, new OpenDialPayload(pos, cannon.getTarget().ordinal()));
            }
            return InteractionResult.SUCCESS;
        }

        if (!level.isClientSide()) {
            CannonStructure.Validation validation = CannonStructure.validate(level, pos, state.getValue(FACING));
            if (!validation.valid()) {
                player.sendOverlayMessage(Component.literal("Cannon incomplete: " + validation.summary()));
                level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, 0.7F, 0.65F);
                return InteractionResult.SUCCESS;
            }

            if (player instanceof ServerPlayer serverPlayer) {
                ModAdvancements.grant(level.getServer(), serverPlayer, "far_traveler");
            }

            if (ModConfig.values.requireRedstoneSignal && !level.hasNeighborSignal(pos)) {
                player.sendOverlayMessage(Component.literal("Give the cannon core a redstone signal first."));
                level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, 0.7F, 0.75F);
                return InteractionResult.SUCCESS;
            }

            LaunchManager.tryStartLaunch(player, level, pos, state.getValue(FACING), cannon);
        }
        return InteractionResult.SUCCESS;
    }
}
