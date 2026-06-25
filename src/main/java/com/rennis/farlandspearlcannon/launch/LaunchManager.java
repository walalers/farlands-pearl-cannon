package com.rennis.farlandspearlcannon.launch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.core.particles.ParticleTypes;

import com.rennis.farlandspearlcannon.advancement.ModAdvancements;
import com.rennis.farlandspearlcannon.block.entity.FarlandsCannonCoreBlockEntity;
import com.rennis.farlandspearlcannon.config.ModConfig;
import com.rennis.farlandspearlcannon.item.ModItems;

public class LaunchManager {
    private static final List<PendingLaunch> PENDING = new ArrayList<>();
    private static final DustParticleOptions REDSTONE_SPARK = new DustParticleOptions(0xFF2A24, 1.0F);
    private static final int BARREL_LENGTH = 7;

    public static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(LaunchManager::tick);
    }

    public static void tryStartLaunch(Player player, Level level, BlockPos corePos, Direction facing, FarlandsCannonCoreBlockEntity cannon) {
        if (!(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel serverLevel)) return;

        long now = level.getGameTime();
        long readyAt = cannon.getLastFireGameTime() + ModConfig.cooldownTicks();
        if (now < readyAt) {
            long seconds = (readyAt - now + 19) / 20;
            player.sendOverlayMessage(Component.literal("Cannon cooling down: " + seconds + "s"));
            serverLevel.playSound(null, corePos, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, 0.7F, 0.7F);
            return;
        }
        if (!cannon.isPearlLoaded()) {
            player.sendOverlayMessage(Component.literal("Load an ender pearl first."));
            return;
        }
        if (cannon.getFuel() < ModConfig.values.fuelRequired) {
            player.sendOverlayMessage(Component.literal("Fuel required: " + cannon.getFuel() + "/" + ModConfig.values.fuelRequired));
            return;
        }
        if (isLaunching(serverPlayer.getUUID())) return;

        FarlandsTarget target = cannon.getTarget();
        BlockPos targetXZ = target.targetFrom(player.blockPosition(), ModConfig.values.targetDistance);
        cannon.consumeForFire(ModConfig.values.consumePearlOnFire, ModConfig.values.consumeFuelOnFire, ModConfig.values.fuelRequired, now);

        int chargeTicks = Math.max(20, ModConfig.values.chargeTicks);
        Map<BlockPos, BlockState> lit = lightRedstone(serverLevel, corePos, facing);
        PENDING.add(new PendingLaunch(serverPlayer.getUUID(), serverLevel.dimension(), corePos.immutable(), facing, targetXZ, chargeTicks, lit));

        // Ignition: the machine springs to life.
        serverLevel.playSound(null, corePos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0F, 0.65F);
        serverLevel.playSound(null, corePos, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.BLOCKS, 1.0F, 0.85F);
        serverLevel.playSound(null, corePos, SoundEvents.TNT_PRIMED, SoundSource.BLOCKS, 1.0F, 0.7F);
        player.sendOverlayMessage(Component.literal("Cannon firing — hold on!"));
    }

    private static boolean isLaunching(UUID id) {
        for (PendingLaunch launch : PENDING) {
            if (launch.playerId.equals(id)) return true;
        }
        return false;
    }

    private static void tick(MinecraftServer server) {
        Iterator<PendingLaunch> iterator = PENDING.iterator();
        while (iterator.hasNext()) {
            PendingLaunch launch = iterator.next();
            ServerLevel level = server.getLevel(launch.levelKey);
            ServerPlayer player = server.getPlayerList().getPlayer(launch.playerId);
            if (level == null || player == null) {
                if (level != null) revertRedstone(level, launch.litBlocks);
                iterator.remove();
                continue;
            }

            launch.ticksRemaining--;
            animate(level, launch);
            if (launch.ticksRemaining <= 0) {
                completeLaunch(server, level, player, launch);
                iterator.remove();
            }
        }
    }

    /** The "working machine": redstone sparks, pistons cycling, and a charge that races down the barrel to the mouth at launch. */
    private static void animate(ServerLevel level, PendingLaunch launch) {
        BlockPos core = launch.corePos;
        Direction facing = launch.facing;
        int total = launch.totalTicks;
        int elapsed = total - launch.ticksRemaining;
        double cx = core.getX() + 0.5, cy = core.getY() + 0.9, cz = core.getZ() + 0.5;

        // Redstone energy crackles over every powered component.
        if (elapsed % 2 == 0) {
            for (CannonStructure.Requirement requirement : CannonStructure.requirements()) {
                BlockState state = level.getBlockState(requirement.worldPos(core, facing));
                if (state.hasProperty(BlockStateProperties.POWER) || state.hasProperty(BlockStateProperties.POWERED)) {
                    BlockPos p = requirement.worldPos(core, facing);
                    level.sendParticles(REDSTONE_SPARK, p.getX() + 0.5, p.getY() + 0.55, p.getZ() + 0.5, 1, 0.22, 0.22, 0.22, 0.0);
                }
            }
        }

        // Pistons slam in and out to drive the chamber.
        if (elapsed % 18 == 0) {
            for (int sign : new int[]{-1, 1}) {
                BlockPos piston = CannonStructure.transform(core, facing, sign, 0, 2);
                level.playSound(null, piston, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 0.7F, 0.85F);
                level.sendParticles(ParticleTypes.LARGE_SMOKE, piston.getX() + 0.5, piston.getY() + 0.6, piston.getZ() + 0.5, 6, 0.15, 0.15, 0.15, 0.02);
            }
        } else if (elapsed % 18 == 9) {
            for (int sign : new int[]{-1, 1}) {
                BlockPos piston = CannonStructure.transform(core, facing, sign, 0, 2);
                level.playSound(null, piston, SoundEvents.PISTON_CONTRACT, SoundSource.BLOCKS, 0.6F, 0.9F);
            }
        }

        int chargeStart = (int) (total * 0.65);
        if (elapsed < chargeStart) {
            // Pre-charge: the breech glows hotter.
            if (elapsed % 6 == 0) {
                level.sendParticles(ParticleTypes.CRIT, cx, cy, cz, 4, 0.3, 0.3, 0.3, 0.05);
                level.sendParticles(REDSTONE_SPARK, cx, cy, cz, 6, 0.3, 0.3, 0.3, 0.0);
            }
            if (elapsed == chargeStart - 1) {
                level.playSound(null, core, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 0.9F, 1.1F);
            }
        } else {
            // The shot travels down the barrel, reaching the mouth exactly at launch.
            double progress = (elapsed - chargeStart) / (double) Math.max(1, total - chargeStart);
            int forward = Math.max(0, Math.min(BARREL_LENGTH, (int) Math.round(progress * BARREL_LENGTH)));
            BlockPos shot = core.relative(facing, forward);
            double sx = shot.getX() + 0.5, sy = shot.getY() + 0.9, sz = shot.getZ() + 0.5;
            level.sendParticles(ParticleTypes.FLAME, sx, sy, sz, 14, 0.22, 0.22, 0.22, 0.02);
            level.sendParticles(ParticleTypes.LAVA, sx, sy, sz, 2, 0.1, 0.1, 0.1, 0.0);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, sx, sy, sz, 6, 0.2, 0.2, 0.2, 0.01);
            if (elapsed % 3 == 0) {
                level.playSound(null, shot, SoundEvents.TNT_PRIMED, SoundSource.BLOCKS, 0.5F, 1.25F);
            }
        }
    }

    private static void completeLaunch(MinecraftServer server, ServerLevel level, ServerPlayer player, PendingLaunch launch) {
        revertRedstone(level, launch.litBlocks);

        BlockPos mouth = launch.corePos.relative(launch.facing, BARREL_LENGTH);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, mouth.getX() + 0.5, mouth.getY() + 0.9, mouth.getZ() + 0.5, 1, 0.0, 0.0, 0.0, 0.0);
        level.playSound(null, mouth, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 1.2F, 0.7F);
        level.playSound(null, launch.corePos, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 1.0F, 0.8F);

        BlockPos safe = findSafeLanding(level, launch.targetXZ.getX(), launch.targetXZ.getZ());
        level.getChunk(safe.getX() >> 4, safe.getZ() >> 4);

        player.teleportTo(safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5);
        level.sendParticles(ParticleTypes.PORTAL, safe.getX() + 0.5, safe.getY() + 1.0, safe.getZ() + 0.5, 80, 0.8, 1.0, 0.8, 0.08);
        level.playSound(null, safe, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 0.85F);

        if (ModConfig.values.giveAnchorPearlOnLaunch) {
            ItemStack pearl = new ItemStack(ModItems.ANCHOR_PEARL);
            if (!player.getInventory().add(pearl)) player.drop(pearl, false); // never lose it to a full inventory
        }
        player.sendSystemMessage(Component.literal("Stranded? Use your Anchor Pearl, or type /farlandscannon return to head home.")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        ModAdvancements.grant(server, player, "how_did_i_get_here");
    }

    /** Temporarily lights every powered redstone component (client-visual only) so the build glows while firing. */
    private static Map<BlockPos, BlockState> lightRedstone(ServerLevel level, BlockPos core, Direction facing) {
        Map<BlockPos, BlockState> originals = new HashMap<>();
        for (CannonStructure.Requirement requirement : CannonStructure.requirements()) {
            BlockPos pos = requirement.worldPos(core, facing);
            BlockState state = level.getBlockState(pos);
            BlockState lit = null;
            if (state.hasProperty(BlockStateProperties.POWER)) {
                lit = state.setValue(BlockStateProperties.POWER, 15);
            } else if (state.hasProperty(BlockStateProperties.POWERED) && !state.getValue(BlockStateProperties.POWERED)) {
                lit = state.setValue(BlockStateProperties.POWERED, true);
            }
            if (lit != null) {
                originals.put(pos.immutable(), state);
                level.setBlock(pos, lit, Block.UPDATE_CLIENTS);
            }
        }
        return originals;
    }

    private static void revertRedstone(ServerLevel level, Map<BlockPos, BlockState> originals) {
        for (Map.Entry<BlockPos, BlockState> entry : originals.entrySet()) {
            if (level.getBlockState(entry.getKey()).is(entry.getValue().getBlock())) {
                level.setBlock(entry.getKey(), entry.getValue(), Block.UPDATE_CLIENTS);
            }
        }
    }

    public static BlockPos findSafeLanding(ServerLevel level, int x, int z) {
        level.getChunk(x >> 4, z >> 4);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos pos = new BlockPos(x, Math.max(level.getMinY() + 2, y + 1), z);

        if (ModConfig.values.createEmergencyLandingPlatform) {
            BlockPos feet = pos.below();
            BlockState below = level.getBlockState(feet);
            if (!below.isSolid()) {
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        level.setBlockAndUpdate(new BlockPos(x + dx, pos.getY() - 1, z + dz), Blocks.SMOOTH_STONE.defaultBlockState());
                    }
                }
            }
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(pos.above(), Blocks.AIR.defaultBlockState());
        }
        return pos;
    }

    private static class PendingLaunch {
        final UUID playerId;
        final ResourceKey<Level> levelKey;
        final BlockPos corePos;
        final Direction facing;
        final BlockPos targetXZ;
        final int totalTicks;
        final Map<BlockPos, BlockState> litBlocks;
        int ticksRemaining;

        PendingLaunch(UUID playerId, ResourceKey<Level> levelKey, BlockPos corePos, Direction facing, BlockPos targetXZ, int totalTicks, Map<BlockPos, BlockState> litBlocks) {
            this.playerId = playerId;
            this.levelKey = levelKey;
            this.corePos = corePos;
            this.facing = facing;
            this.targetXZ = targetXZ;
            this.totalTicks = totalTicks;
            this.ticksRemaining = totalTicks;
            this.litBlocks = litBlocks;
        }
    }
}
