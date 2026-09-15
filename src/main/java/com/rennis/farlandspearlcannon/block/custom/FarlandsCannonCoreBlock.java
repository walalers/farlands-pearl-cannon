package com.rennis.farlandspearlcannon.block.custom;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;

import com.rennis.farlandspearlcannon.advancement.ModAdvancements;
import com.rennis.farlandspearlcannon.block.ModBlockEntities;
import com.rennis.farlandspearlcannon.block.entity.FarlandsCannonCoreBlockEntity;
import com.rennis.farlandspearlcannon.config.ModConfig;
import com.rennis.farlandspearlcannon.launch.BlueprintProjectionManager;
import com.rennis.farlandspearlcannon.launch.CannonStructure;
import com.rennis.farlandspearlcannon.launch.LaunchChecklist;
import com.rennis.farlandspearlcannon.launch.LaunchManager;
import com.rennis.farlandspearlcannon.network.OpenDialPayload;

public class FarlandsCannonCoreBlock extends BaseEntityBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** Only players this close to the core ride a redstone-triggered shot. */
    private static final double RIDER_RANGE_SQ = 8.0 * 8.0;

    public FarlandsCannonCoreBlock(Properties settings) {
        super(settings);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
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
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null
                : createTickerHelper(type, ModBlockEntities.FARLANDS_CANNON_CORE, FarlandsCannonCoreBlockEntity::serverTick);
    }

    /**
     * The barrel builds out in front of the player (the way they are looking), not back toward them. If the
     * player pinned a blueprint on this spot with the projector, the pinned heading wins so the deck they
     * already built lines up.
     */
    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction facing = ctx.getHorizontalDirection();
        if (ctx.getPlayer() instanceof ServerPlayer serverPlayer) {
            Direction pinned = BlueprintProjectionManager.pinnedFacing(serverPlayer, ctx.getClickedPos());
            if (pinned != null) facing = pinned;
        }
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack stack) {
        super.setPlacedBy(level, pos, state, by, stack);
        if (!level.isClientSide() && by instanceof ServerPlayer serverPlayer) {
            BlueprintProjectionManager.corePlaced(serverPlayer, pos);
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    // Comparator readout: 15 while primed, otherwise a 0-14 loading-progress value.
    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        if (level instanceof ServerLevel serverLevel && level.getBlockEntity(pos) instanceof FarlandsCannonCoreBlockEntity cannon) {
            return cannon.comparatorSignal(serverLevel, pos, state);
        }
        return 0;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof FarlandsCannonCoreBlockEntity cannon)) return InteractionResult.PASS;

        // Every loading click answers with what to do next, so the player is never left guessing.
        if (stack.is(Items.ENDER_PEARL)) {
            if (!level.isClientSide()) {
                if (cannon.loadPearl()) {
                    if (!player.getAbilities().instabuild) stack.shrink(1);
                    level.playSound(null, pos, SoundEvents.ENDERMAN_TELEPORT, SoundSource.BLOCKS, 0.45F, 1.35F);
                    player.sendOverlayMessage(msg("core.pearl_loaded", nextStep(level, pos, state, cannon)));
                } else {
                    player.sendOverlayMessage(msg("core.pearl_already", nextStep(level, pos, state, cannon)));
                }
            }
            return InteractionResult.SUCCESS;
        }

        if (stack.is(Items.BLAZE_POWDER) || stack.is(Items.ENDER_EYE)) {
            if (!level.isClientSide()) {
                if (cannon.addFuel(ModConfig.values.fuelRequired)) {
                    if (!player.getAbilities().instabuild) stack.shrink(1);
                    level.playSound(null, pos, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.BLOCKS, 0.55F, 1.2F);
                    player.sendOverlayMessage(msg("core.fuel", cannon.getFuel(), ModConfig.values.fuelRequired, nextStep(level, pos, state, cannon)));
                } else {
                    player.sendOverlayMessage(msg("core.fuel_full", nextStep(level, pos, state, cannon)));
                }
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
        MinecraftServer server = level.getServer();
        Direction facing = state.getValue(FACING);
        ServerPlayer rider = null;
        double best = RIDER_RANGE_SQ;
        for (ServerPlayer candidate : level.players()) {
            if (candidate.isSpectator()) continue;
            double dist = candidate.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            if (dist < best) {
                best = dist;
                rider = candidate;
            }
        }
        if (rider == null) return; // nobody close enough to ride the shot

        int stageIndex = CannonStructure.currentStageIndex(level, pos, facing);
        if (stageIndex < CannonStructure.stages().size()) {
            rider.sendOverlayMessage(incomplete(level, pos, facing, stageIndex)); // a half-built cannon never fires
            return;
        }

        ModAdvancements.grant(server, rider, "far_traveler");
        LaunchManager.tryStartLaunch(rider, level, pos, facing, cannon);
    }

    /** What the player should do next at this core: finish the current build step, or the next launch-checklist step. */
    private static MutableComponent nextStep(Level level, BlockPos pos, BlockState state, FarlandsCannonCoreBlockEntity cannon) {
        Direction facing = state.getValue(FACING);
        int stageIndex = CannonStructure.currentStageIndex(level, pos, facing);
        if (stageIndex < CannonStructure.stages().size()) return incomplete(level, pos, facing, stageIndex);
        return LaunchChecklist.of(level, pos, cannon).nextAction();
    }

    /** "Step 3/8 (Obsidian barrel), 4 blocks left" instead of a raw list of every missing block. */
    private static MutableComponent incomplete(Level level, BlockPos pos, Direction facing, int stageIndex) {
        CannonStructure.Stage stage = CannonStructure.stages().get(stageIndex);
        int left = stage.slots().size() - stage.placed(level, pos, facing);
        return msg("core.incomplete", stageIndex + 1, CannonStructure.stages().size(), stage.label(), left);
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
            Direction facing = state.getValue(FACING);
            int stageIndex = CannonStructure.currentStageIndex(level, pos, facing);
            if (stageIndex < CannonStructure.stages().size()) {
                player.sendOverlayMessage(incomplete(level, pos, facing, stageIndex));
                level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, 0.7F, 0.65F);
                return InteractionResult.SUCCESS;
            }

            if (player instanceof ServerPlayer serverPlayer) {
                ModAdvancements.grant(level.getServer(), serverPlayer, "far_traveler");
            }

            // Right-clicking a finished core is the "what now?" button: it fires if it can, otherwise names the next step.
            LaunchChecklist.Status status = LaunchChecklist.of(level, pos, cannon);
            boolean canFireByHand = !ModConfig.values.requireRedstoneSignal || level.hasNeighborSignal(pos);
            if (status.ready() && canFireByHand) {
                LaunchManager.tryStartLaunch(player, level, pos, facing, cannon);
                return InteractionResult.SUCCESS;
            }
            player.sendOverlayMessage(status.nextAction());
            if (status.ready()) {
                level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.7F, 1.4F);
            } else {
                level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, 0.7F, 0.75F);
            }
        }
        return InteractionResult.SUCCESS;
    }

    private static MutableComponent msg(String key, Object... args) {
        return Component.translatable("message.farlands_pearl_cannon." + key, args);
    }
}
