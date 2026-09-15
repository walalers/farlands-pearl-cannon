package com.rennis.farlandspearlcannon.launch;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.levelgen.Heightmap;

import com.rennis.farlandspearlcannon.FarlandsPearlCannonMod;
import com.rennis.farlandspearlcannon.advancement.ModAdvancements;
import com.rennis.farlandspearlcannon.block.entity.FarlandsCannonCoreBlockEntity;
import com.rennis.farlandspearlcannon.config.ModConfig;
import com.rennis.farlandspearlcannon.item.ModItems;

public class LaunchManager {
    private static final List<PendingLaunch> PENDING = new ArrayList<>();
    private static final DustParticleOptions REDSTONE_SPARK = new DustParticleOptions(0xFF2A24, 1.0F);
    private static final int BARREL_LENGTH = 7;
    /** Keep the landing this far inside a shrunken world border so the player never spawns in the wall. */
    private static final int BORDER_MARGIN = 16;
    /** A rider who gets farther than this from the core while it charges (walked off, respawned, teleported) is not fired. */
    private static final double STAY_RANGE_SQ = 24.0 * 24.0;

    public static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(LaunchManager::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(LaunchManager::onServerStopping);
    }

    /** Never carry a half-finished shot from one world into the next (singleplayer quit-to-title mid-charge). */
    private static void onServerStopping(MinecraftServer server) {
        for (PendingLaunch launch : PENDING) {
            ServerLevel level = server.getLevel(launch.levelKey);
            if (level != null) {
                revertRedstone(level, launch.litBlocks);
                refund(level, launch);
            }
        }
        PENDING.clear();
    }

    public static void tryStartLaunch(Player player, Level level, BlockPos corePos, Direction facing, FarlandsCannonCoreBlockEntity cannon) {
        if (!(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel serverLevel)) return;

        long now = level.getGameTime();
        long readyAt = cannon.getLastFireGameTime() + ModConfig.cooldownTicks();
        if (now < readyAt) {
            long seconds = (readyAt - now + 19) / 20;
            player.sendOverlayMessage(msg("launch.cooldown", seconds));
            serverLevel.playSound(null, corePos, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, 0.7F, 0.7F);
            return;
        }
        if (!cannon.isPearlLoaded()) {
            player.sendOverlayMessage(msg("launch.no_pearl"));
            return;
        }
        if (cannon.getFuel() < ModConfig.values.fuelRequired) {
            player.sendOverlayMessage(msg("launch.no_fuel", cannon.getFuel(), ModConfig.values.fuelRequired));
            return;
        }
        if (isLaunching(serverPlayer.getUUID())) return;

        FarlandsTarget target = cannon.getTarget();
        BlockPos targetXZ = target.targetFrom(player.blockPosition(), ModConfig.values.targetDistance);
        BlockPos clamped = clampToWorldBorder(serverLevel, targetXZ);
        if (!clamped.equals(targetXZ)) {
            player.sendOverlayMessage(msg("launch.border_clamped").withStyle(ChatFormatting.YELLOW));
            targetXZ = clamped;
        }
        cannon.consumeForFire(ModConfig.values.consumePearlOnFire, ModConfig.values.consumeFuelOnFire, ModConfig.values.fuelRequired, now);

        int chargeTicks = Math.max(20, ModConfig.values.chargeTicks);
        Map<BlockPos, BlockState> lit = lightRedstone(serverLevel, corePos, facing);
        PENDING.add(new PendingLaunch(serverPlayer.getUUID(), serverLevel.dimension(), corePos.immutable(), facing, targetXZ, chargeTicks, lit));

        // Ignition: the machine springs to life.
        serverLevel.playSound(null, corePos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0F, 0.65F);
        serverLevel.playSound(null, corePos, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.BLOCKS, 1.0F, 0.85F);
        serverLevel.playSound(null, corePos, SoundEvents.TNT_PRIMED, SoundSource.BLOCKS, 1.0F, 0.7F);
        player.sendOverlayMessage(msg("launch.firing"));
    }

    /** Pulls the target inside the world border (with a margin) so a shrunken border can never strand the rider in the wall. */
    static BlockPos clampToWorldBorder(ServerLevel level, BlockPos target) {
        WorldBorder border = level.getWorldBorder();
        double minX = border.getMinX() + BORDER_MARGIN;
        double maxX = border.getMaxX() - BORDER_MARGIN;
        double minZ = border.getMinZ() + BORDER_MARGIN;
        double maxZ = border.getMaxZ() - BORDER_MARGIN;
        if (minX > maxX || minZ > maxZ) return target; // border too small to matter; let vanilla handle it
        int x = (int) Math.max(minX, Math.min(maxX, target.getX()));
        int z = (int) Math.max(minZ, Math.min(maxZ, target.getZ()));
        return x == target.getX() && z == target.getZ() ? target : new BlockPos(x, target.getY(), z);
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
            if (level == null) {
                iterator.remove();
                continue;
            }
            // Rider logged out, died, or walked through a portal mid-charge: reload the cannon rather than eat the shot.
            if (player == null || !player.isAlive() || player.level() != level) {
                revertRedstone(level, launch.litBlocks);
                refund(level, launch);
                if (player != null) player.sendOverlayMessage(msg("launch.aborted").withStyle(ChatFormatting.YELLOW));
                iterator.remove();
                continue;
            }

            if (player.distanceToSqr(launch.corePos.getX() + 0.5, launch.corePos.getY() + 0.5, launch.corePos.getZ() + 0.5) > STAY_RANGE_SQ) {
                revertRedstone(level, launch.litBlocks);
                refund(level, launch);
                player.sendOverlayMessage(msg("launch.walked_away").withStyle(ChatFormatting.YELLOW));
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

    private static void refund(ServerLevel level, PendingLaunch launch) {
        if (level.getBlockEntity(launch.corePos) instanceof FarlandsCannonCoreBlockEntity cannon) {
            cannon.refundShot(ModConfig.values.consumePearlOnFire, ModConfig.values.consumeFuelOnFire, ModConfig.values.fuelRequired);
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
                BlockPos p = requirement.worldPos(core, facing);
                BlockState state = level.getBlockState(p);
                if (state.hasProperty(BlockStateProperties.POWER) || state.hasProperty(BlockStateProperties.POWERED)) {
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

        player.teleportTo(level, safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5, Set.of(), player.getYRot(), player.getXRot(), false);
        player.fallDistance = 0.0F;
        level.sendParticles(ParticleTypes.PORTAL, safe.getX() + 0.5, safe.getY() + 1.0, safe.getZ() + 0.5, 80, 0.8, 1.0, 0.8, 0.08);
        level.playSound(null, safe, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 0.85F);

        if (ModConfig.values.giveAnchorPearlOnLaunch) {
            ItemStack pearl = new ItemStack(ModItems.ANCHOR_PEARL);
            if (!player.getInventory().add(pearl)) player.drop(pearl, false); // never lose it to a full inventory
        }
        player.sendSystemMessage(msg("launch.stranded").withStyle(ChatFormatting.LIGHT_PURPLE));
        ModAdvancements.grant(server, player, "how_did_i_get_here");
        FarlandsPearlCannonMod.LOGGER.info("{} launched to {} {} (heading {}).", player.getGameProfile().name(), safe.getX(), safe.getZ(), launch.targetXZ);
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
            // Nothing to stand on (air, water, leaves, a fence post...): lay a small stone pad first.
            if (level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()) {
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

    private static net.minecraft.network.chat.MutableComponent msg(String key, Object... args) {
        return Component.translatable("message.farlands_pearl_cannon." + key, args);
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
