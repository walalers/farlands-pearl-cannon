package com.rennis.farlandspearlcannon.launch;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import com.rennis.farlandspearlcannon.advancement.ModAdvancements;
import com.rennis.farlandspearlcannon.block.ModBlocks;
import com.rennis.farlandspearlcannon.block.custom.FarlandsCannonCoreBlock;
import com.rennis.farlandspearlcannon.config.ModConfig;
import com.rennis.farlandspearlcannon.item.CannonManual;
import com.rennis.farlandspearlcannon.item.ModItems;
import com.rennis.farlandspearlcannon.item.custom.BlueprintProjectorItem;
import com.rennis.farlandspearlcannon.network.PinBlueprintPayload;

/**
 * The in-world guide is drawn entirely client-side (see BlueprintGuideClient), so it works from either
 * hand without any link step. This server side only watches for the build being finished so it can grant
 * the advancement and fire a celebration everyone can see.
 */
public class BlueprintProjectionManager {
    private static final int SCAN_RADIUS = 8;
    private static final int CHECK_TICKS = 20;
    /** A plain right-click this close to an existing blueprint leaves it alone (Shift + right-click moves it). */
    private static final double PIN_KEEP_DISTANCE = 48.0;
    private static final Map<UUID, Boolean> CELEBRATED = new HashMap<>();
    /** Blueprints pinned with the projector before a core exists: where the core will go and which way it fires. */
    private static final Map<UUID, Pin> PINS = new HashMap<>();

    public record Pin(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension, BlockPos core, Direction facing) {}

    public static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(BlueprintProjectionManager::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> { CELEBRATED.clear(); PINS.clear(); });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            CELEBRATED.remove(handler.player.getUUID());
            PINS.remove(handler.player.getUUID());
        });
    }

    /**
     * A projector click on the ground. Plain right-click places a blueprint, Shift + right-click moves it. A plain
     * click never replaces a nearby blueprint or hijacks the guide of a cannon that is already standing here;
     * it explains why instead, so a click is never silently ignored.
     */
    public static void requestPin(ServerPlayer player, BlockPos clickedPos, Direction clickedFace, boolean move) {
        ServerLevel level = (ServerLevel) player.level();
        BlockPos core = BlueprintProjectorItem.plannedCore(clickedPos, clickedFace);
        Pin existing = PINS.get(player.getUUID());
        boolean hasNearbyPin = existing != null && existing.dimension().equals(level.dimension()) && existing.core().closerThan(core, PIN_KEEP_DISTANCE);

        if (!move) {
            if (hasNearbyPin) {
                player.sendOverlayMessage(Component.translatable("message.farlands_pearl_cannon.projector.already_pinned").withStyle(ChatFormatting.YELLOW));
                return;
            }
            if (CannonStructure.findCore(level, player.blockPosition(), SCAN_RADIUS) != null) {
                player.sendOverlayMessage(Component.translatable("message.farlands_pearl_cannon.projector.core_nearby").withStyle(ChatFormatting.YELLOW));
                return;
            }
        }

        Direction facing = player.getDirection();
        PINS.put(player.getUUID(), new Pin(level.dimension(), core.immutable(), facing));
        ServerPlayNetworking.send(player, new PinBlueprintPayload(core, facing.get3DDataValue()));
        level.playSound(null, core, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 1.3F);
        player.sendOverlayMessage(Component.translatable(hasNearbyPin
                ? "message.farlands_pearl_cannon.projector.moved"
                : "message.farlands_pearl_cannon.projector.pinned").withStyle(ChatFormatting.AQUA));
        if (CannonManual.giveIfMissing(player)) {
            player.sendSystemMessage(Component.translatable("message.farlands_pearl_cannon.projector.manual_added_hint").withStyle(ChatFormatting.GOLD));
        }
    }

    /** The facing a core placed at {@code pos} by this player should get, or null if they have no pin there. */
    public static Direction pinnedFacing(ServerPlayer player, BlockPos pos) {
        Pin pin = PINS.get(player.getUUID());
        if (pin == null || !pin.dimension().equals(player.level().dimension()) || !pin.core().equals(pos)) return null;
        return pin.facing();
    }

    /** A core went down on the pinned spot: the blueprint is now anchored to the real block. */
    public static void corePlaced(ServerPlayer player, BlockPos pos) {
        Pin pin = PINS.get(player.getUUID());
        if (pin != null && pin.core().equals(pos)) PINS.remove(player.getUUID());
    }

    /**
     * A piston or dispenser dropped into its cannon slot turns to face the barrel by itself. Vanilla aims them
     * at the player's eyes, which points them up or down when placed from the deck or the barrel.
     * Called from BlockItemMixin after a player places a block (Fabric has no block-place event).
     */
    public static void onBlockPlaced(ServerLevel level, BlockPos pos) {
        BlockState placed = level.getBlockState(pos);
        if (!placed.hasProperty(BlockStateProperties.FACING) || !isOrientedSlotBlock(placed)) return;

        BlockPos core = CannonStructure.findCore(level, pos, SCAN_RADIUS);
        if (core == null) return;
        Direction coreFacing = level.getBlockState(core).getValue(FarlandsCannonCoreBlock.FACING);
        for (CannonStructure.Requirement requirement : CannonStructure.requirements()) {
            Direction wanted = requirement.expectedFacingOrNull(coreFacing);
            if (wanted == null || !requirement.worldPos(core, coreFacing).equals(pos)) continue;
            BlockState turned = placed.setValue(BlockStateProperties.FACING, wanted);
            if (turned != placed && requirement.matches(turned, coreFacing)) level.setBlock(pos, turned, Block.UPDATE_ALL);
            return;
        }
    }

    private static boolean isOrientedSlotBlock(BlockState state) {
        for (CannonStructure.Requirement requirement : CannonStructure.requirements()) {
            if (!requirement.isOriented()) continue;
            for (Block block : requirement.accepted()) {
                if (state.is(block)) return true;
            }
        }
        return false;
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

            var coreState = level.getBlockState(core);
            if (!coreState.is(ModBlocks.FARLANDS_CANNON_CORE)) continue;
            Direction facing = coreState.getValue(FarlandsCannonCoreBlock.FACING);
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
        player.sendOverlayMessage(Component.translatable("message.farlands_pearl_cannon.build.complete", ModConfig.values.fuelRequired).withStyle(ChatFormatting.GREEN));
        // Warn before the pearl and fuel are spent, not only after landing.
        FarlandsCompat.warnIfReforgedMissing(player, "built");
    }
}
