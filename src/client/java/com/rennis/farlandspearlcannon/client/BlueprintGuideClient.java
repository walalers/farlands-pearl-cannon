package com.rennis.farlandspearlcannon.client;

import java.util.List;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.level.block.state.BlockState;

import com.rennis.farlandspearlcannon.block.ModBlocks;
import com.rennis.farlandspearlcannon.block.custom.FarlandsCannonCoreBlock;
import com.rennis.farlandspearlcannon.item.ModItems;
import com.rennis.farlandspearlcannon.launch.CannonStructure;

/**
 * Draws the in-world build guide one stage at a time, entirely on the client, so it works with the
 * projector in either hand and never floods the screen. Only the current stage is shown; the single next
 * block to place gets a beam and arrow so it is obvious where to go. The HUD reads the cached core below.
 */
public class BlueprintGuideClient {
    private static final int SCAN_RADIUS = 8;
    private static final double KEEP_DISTANCE_SQ = 24.0 * 24.0;
    private static final double LO = -0.02;
    private static final double HI = 1.02;
    private static final int EDGE_SAMPLES = 2;

    private static final DustParticleOptions WHITE = new DustParticleOptions(0xCFE8FF, 1.0F);
    private static final DustParticleOptions GREEN = new DustParticleOptions(0x46E066, 0.9F);
    private static final DustParticleOptions RED = new DustParticleOptions(0xFF454F, 1.0F);
    private static final DustParticleOptions NEXT = new DustParticleOptions(0xEAFBFF, 1.25F);
    private static final DustParticleOptions BEAM = new DustParticleOptions(0x66E0FF, 1.1F);
    // Dim corner pips that sketch the whole cannon so players see the full footprint at a glance.
    private static final DustParticleOptions FAINT_TODO = new DustParticleOptions(0x44586E, 0.55F);
    private static final DustParticleOptions FAINT_DONE = new DustParticleOptions(0x356B43, 0.55F);

    private static int ticks;
    private static boolean holding;
    private static BlockPos core;
    private static Direction facing = Direction.NORTH;

    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(BlueprintGuideClient::tick);
    }

    public static boolean isHolding() {
        return holding;
    }

    public static BlockPos core() {
        return core;
    }

    public static Direction facing() {
        return facing;
    }

    private static void tick(Minecraft minecraft) {
        ClientLevel level = minecraft.level;
        LocalPlayer player = minecraft.player;
        holding = player != null
                && (player.getMainHandItem().is(ModItems.BLUEPRINT_PROJECTOR) || player.getOffhandItem().is(ModItems.BLUEPRINT_PROJECTOR));
        if (level == null || player == null || !holding) {
            core = null;
            return;
        }
        ticks++;

        // Drop the cached core the instant it stops being a real core block (e.g. after a launch teleports
        // us far away and the old chunk reads as void_air). This prevents reading FACING off a missing block.
        if (core != null && !level.getBlockState(core).is(ModBlocks.FARLANDS_CANNON_CORE)) {
            core = null;
        }
        boolean tooFar = core != null && player.blockPosition().distSqr(core) > KEEP_DISTANCE_SQ;
        if ((core == null || tooFar) && ticks % 5 == 0) {
            core = CannonStructure.findCore(level, player.blockPosition(), SCAN_RADIUS);
        }
        if (core == null) return;

        BlockState coreState = level.getBlockState(core);
        if (!coreState.is(ModBlocks.FARLANDS_CANNON_CORE)) return; // safety: never read FACING off a non-core block
        facing = coreState.getValue(FarlandsCannonCoreBlock.FACING);
        List<CannonStructure.Stage> stages = CannonStructure.stages();
        int index = CannonStructure.currentStageIndex(level, core, facing);
        boolean complete = index >= stages.size();

        // Faint full-structure ghost: every stage but the active one, so the whole cannon is always sketched.
        if (ticks % 8 == 0) {
            for (int si = 0; si < stages.size(); si++) {
                if (si == index && !complete) continue;
                DustParticleOptions tone = (complete || si < index) ? FAINT_DONE : FAINT_TODO;
                for (CannonStructure.Requirement requirement : stages.get(si).slots()) {
                    corners(level, tone, requirement.worldPos(core, facing));
                }
            }
        }
        if (complete) return; // finished: HUD shows the completion card

        CannonStructure.Stage stage = stages.get(index);
        CannonStructure.Requirement next = stage.firstIncomplete(level, core, facing);
        boolean drawCages = ticks % 4 == 0;

        for (CannonStructure.Requirement requirement : stage.slots()) {
            BlockPos pos = requirement.worldPos(core, facing);
            BlockState state = level.getBlockState(pos);
            boolean correct = requirement.matches(state, facing);
            boolean isNext = requirement == next;

            if (correct) {
                if (drawCages) pip(level, GREEN, pos);
            } else if (drawCages) {
                boolean wrong = !state.isAir();
                cage(level, isNext ? NEXT : wrong ? RED : WHITE, pos);
                Direction wanted = requirement.expectedFacingOrNull(facing);
                if (wanted != null) arrow(level, pos, wanted, wrong ? RED : WHITE);
            }
            if (isNext) beam(level, pos);
        }
    }

    private static void beam(ClientLevel level, BlockPos pos) {
        double x = pos.getX() + 0.5;
        double z = pos.getZ() + 0.5;
        double base = pos.getY() + 0.5;
        for (double h = 0.0; h <= 1.6; h += 0.2) spawn(level, BEAM, x, base + h, z);
        double rise = (ticks % 20) / 20.0;
        spawn(level, NEXT, x, base + rise * 2.0, z);
    }

    private static void pip(ClientLevel level, DustParticleOptions color, BlockPos pos) {
        spawn(level, color, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    private static void corners(ClientLevel level, DustParticleOptions color, BlockPos pos) {
        double x = pos.getX();
        double y = pos.getY();
        double z = pos.getZ();
        for (double cx : new double[]{LO, HI}) {
            for (double cy : new double[]{LO, HI}) {
                for (double cz : new double[]{LO, HI}) {
                    spawn(level, color, x + cx, y + cy, z + cz);
                }
            }
        }
    }

    private static void cage(ClientLevel level, DustParticleOptions color, BlockPos pos) {
        double x = pos.getX();
        double y = pos.getY();
        double z = pos.getZ();
        for (double cx : new double[]{LO, HI}) {
            for (double cy : new double[]{LO, HI}) {
                for (double cz : new double[]{LO, HI}) {
                    spawn(level, color, x + cx, y + cy, z + cz);
                }
            }
        }
        for (int i = 1; i <= EDGE_SAMPLES; i++) {
            double t = LO + (HI - LO) * i / (EDGE_SAMPLES + 1);
            spawn(level, color, x + t, y + LO, z + LO);
            spawn(level, color, x + t, y + LO, z + HI);
            spawn(level, color, x + t, y + HI, z + LO);
            spawn(level, color, x + t, y + HI, z + HI);
            spawn(level, color, x + LO, y + t, z + LO);
            spawn(level, color, x + LO, y + t, z + HI);
            spawn(level, color, x + HI, y + t, z + LO);
            spawn(level, color, x + HI, y + t, z + HI);
            spawn(level, color, x + LO, y + LO, z + t);
            spawn(level, color, x + LO, y + HI, z + t);
            spawn(level, color, x + HI, y + LO, z + t);
            spawn(level, color, x + HI, y + HI, z + t);
        }
    }

    private static void arrow(ClientLevel level, BlockPos pos, Direction facing, DustParticleOptions color) {
        double fx = facing.getStepX();
        double fy = facing.getStepY();
        double fz = facing.getStepZ();
        Direction side = facing.getClockWise();
        double sx = side.getStepX();
        double sy = side.getStepY();
        double sz = side.getStepZ();

        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 0.5;
        double cz = pos.getZ() + 0.5;
        double base = 0.46;
        double bx = cx + fx * base;
        double by = cy + fy * base;
        double bz = cz + fz * base;

        spawn(level, color, bx + fx * 0.16, by + fy * 0.16, bz + fz * 0.16);
        spawn(level, color, cx + fx * 0.20, cy + fy * 0.20, cz + fz * 0.20);
        double back = 0.10;
        spawn(level, color, bx - fx * back + sx * 0.14, by - fy * back + sy * 0.14, bz - fz * back + sz * 0.14);
        spawn(level, color, bx - fx * back - sx * 0.14, by - fy * back - sy * 0.14, bz - fz * back - sz * 0.14);
        spawn(level, color, bx - fx * back, by - fy * back + 0.14, bz - fz * back);
        spawn(level, color, bx - fx * back, by - fy * back - 0.14, bz - fz * back);
    }

    private static void spawn(ClientLevel level, DustParticleOptions color, double x, double y, double z) {
        level.addParticle(color, x, y, z, 0.0, 0.0, 0.0);
    }
}
