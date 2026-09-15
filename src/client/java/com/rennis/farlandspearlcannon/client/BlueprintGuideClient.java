package com.rennis.farlandspearlcannon.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import com.rennis.farlandspearlcannon.block.ModBlocks;
import com.rennis.farlandspearlcannon.block.custom.FarlandsCannonCoreBlock;
import com.rennis.farlandspearlcannon.block.entity.FarlandsCannonCoreBlockEntity;
import com.rennis.farlandspearlcannon.item.ModItems;
import com.rennis.farlandspearlcannon.item.custom.BlueprintProjectorItem;
import com.rennis.farlandspearlcannon.launch.CannonStructure;
import com.rennis.farlandspearlcannon.launch.LaunchChecklist;

/**
 * Draws the in-world build guide one stage at a time, entirely on the client, so it works with the
 * projector in either hand and never floods the screen.
 *
 * Three anchors, in priority order:
 *  1. a real Cannon Core nearby - the normal stage-by-stage guide;
 *  2. a pinned blueprint (right-click on the ground, sent by the server) - the deck stage plus a beam where the core goes;
 *  3. nothing yet - a faint preview of the deck footprint following the crosshair.
 */
public class BlueprintGuideClient {
    private static final int SCAN_RADIUS = 8;
    private static final double KEEP_DISTANCE_SQ = 24.0 * 24.0;
    private static final double PLAN_KEEP_DISTANCE_SQ = 64.0 * 64.0;
    private static final double LO = -0.02;
    private static final double HI = 1.02;
    private static final int EDGE_SAMPLES = 2;

    private static final DustParticleOptions WHITE = new DustParticleOptions(0xCFE8FF, 1.0F);
    private static final DustParticleOptions GREEN = new DustParticleOptions(0x46E066, 0.9F);
    private static final DustParticleOptions RED = new DustParticleOptions(0xFF454F, 1.0F);
    private static final DustParticleOptions NEXT = new DustParticleOptions(0xEAFBFF, 1.25F);
    private static final DustParticleOptions BEAM = new DustParticleOptions(0x66E0FF, 1.1F);
    private static final DustParticleOptions CORE_BEAM = new DustParticleOptions(0xFFD479, 1.2F);
    // Dim corner pips that sketch the whole cannon so players see the full footprint at a glance.
    private static final DustParticleOptions FAINT_TODO = new DustParticleOptions(0x44586E, 0.55F);
    private static final DustParticleOptions FAINT_DONE = new DustParticleOptions(0x356B43, 0.55F);
    private static final DustParticleOptions PREVIEW = new DustParticleOptions(0x5FA8C8, 0.6F);

    private static int ticks;
    private static boolean holding;
    @Nullable private static BlockPos core;
    private static Direction facing = Direction.NORTH;
    @Nullable private static BlockPos planCore;
    private static Direction planFacing = Direction.NORTH;
    @Nullable private static BlockPos previewCore;

    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(BlueprintGuideClient::tick);
    }

    public static void pin(BlockPos pos, Direction dir) {
        planCore = pos.immutable();
        planFacing = dir;
        core = null;
    }

    public static boolean isHolding() { return holding; }
    @Nullable public static BlockPos core() { return core; }
    public static Direction facing() { return facing; }
    @Nullable public static BlockPos planCore() { return planCore; }
    public static Direction planFacing() { return planFacing; }
    public static boolean previewing() { return previewCore != null; }

    private static void tick(Minecraft minecraft) {
        ClientLevel level = minecraft.level;
        LocalPlayer player = minecraft.player;
        holding = player != null
                && (player.getMainHandItem().is(ModItems.BLUEPRINT_PROJECTOR) || player.getOffhandItem().is(ModItems.BLUEPRINT_PROJECTOR));
        if (level == null || player == null || !holding) {
            core = null;
            previewCore = null;
            return;
        }
        ticks++;

        // Drop the cached core the instant it stops being a real core block (e.g. after a launch teleports
        // us far away and the old chunk reads as void_air). This prevents reading FACING off a missing block.
        if (core != null && !level.getBlockState(core).is(ModBlocks.FARLANDS_CANNON_CORE)) {
            core = null;
        }
        boolean tooFar = core != null && player.blockPosition().distSqr(core) > KEEP_DISTANCE_SQ;
        // A fresh blueprint wins over some other core standing nearby; only the pinned spot's own core takes over.
        boolean planActive = planCore != null && player.blockPosition().distSqr(planCore) <= PLAN_KEEP_DISTANCE_SQ;
        if (!planActive && (core == null || tooFar) && ticks % 5 == 0) {
            core = CannonStructure.findCore(level, player.blockPosition(), SCAN_RADIUS);
        }
        // The pinned spot got its core: the real block takes over from the plan.
        if (core == null && planCore != null && level.getBlockState(planCore).is(ModBlocks.FARLANDS_CANNON_CORE)) {
            core = planCore;
        }

        if (core != null) {
            planCore = null;
            previewCore = null;
            BlockState coreState = level.getBlockState(core);
            if (!coreState.is(ModBlocks.FARLANDS_CANNON_CORE)) return; // safety: never read FACING off a non-core block
            facing = coreState.getValue(FarlandsCannonCoreBlock.FACING);
            drawGuide(level, core, facing, false);
            return;
        }

        if (planCore != null) {
            previewCore = null;
            if (player.blockPosition().distSqr(planCore) > PLAN_KEEP_DISTANCE_SQ) return; // keep the pin, just stop drawing
            drawGuide(level, planCore, planFacing, true);
            return;
        }

        drawPreview(level, player, player.pick(BlueprintProjectorItem.PIN_RANGE, 1.0F, false));
    }

    /** Faint deck footprint at the crosshair (same reach the server uses) so the player sees where a pin would land. */
    private static void drawPreview(ClientLevel level, LocalPlayer player, @Nullable HitResult hit) {
        if (!(hit instanceof BlockHitResult blockHit) || blockHit.getType() != HitResult.Type.BLOCK) {
            previewCore = null;
            return;
        }
        previewCore = BlueprintProjectorItem.plannedCore(blockHit.getBlockPos(), blockHit.getDirection());
        Direction dir = player.getDirection();
        if (ticks % 4 != 0) return;
        for (CannonStructure.Requirement requirement : CannonStructure.stages().get(CannonStructure.DECK_STAGE).slots()) {
            corners(level, PREVIEW, requirement.worldPos(previewCore, dir));
        }
        pip(level, CORE_BEAM, previewCore);
        // A short nose of the barrel so the heading is obvious before pinning.
        for (int f = 1; f <= 3; f++) pip(level, PREVIEW, previewCore.relative(dir, f));
    }

    /**
     * The stage-by-stage guide. In planned mode only the deck stage is live and, once the deck is done,
     * the beam moves to the spot where the Cannon Core must be placed.
     */
    private static void drawGuide(ClientLevel level, BlockPos anchor, Direction dir, boolean planned) {
        List<CannonStructure.Stage> stages = CannonStructure.stages();
        int index = planned ? CannonStructure.DECK_STAGE : CannonStructure.currentStageIndex(level, anchor, dir);
        boolean complete = !planned && index >= stages.size();

        // Faint full-structure ghost: every stage but the active one, so the whole cannon is always sketched.
        if (ticks % 8 == 0) {
            for (int si = 0; si < stages.size(); si++) {
                if (si == index && !complete) continue;
                DustParticleOptions tone = (complete || si < index) ? FAINT_DONE : FAINT_TODO;
                for (CannonStructure.Requirement requirement : stages.get(si).slots()) {
                    corners(level, tone, requirement.worldPos(anchor, dir));
                }
            }
        }
        if (complete) {
            drawLaunchCue(level, anchor, dir); // finished: HUD shows the launch checklist
            return;
        }

        CannonStructure.Stage stage = stages.get(index);
        CannonStructure.Requirement next = stage.firstIncomplete(level, anchor, dir);
        boolean drawCages = ticks % 4 == 0;

        for (CannonStructure.Requirement requirement : stage.slots()) {
            BlockPos pos = requirement.worldPos(anchor, dir);
            BlockState state = level.getBlockState(pos);
            boolean correct = requirement.matches(state, dir);
            boolean isNext = requirement == next;

            if (correct) {
                if (drawCages) pip(level, GREEN, pos);
            } else if (drawCages) {
                boolean wrong = !state.isAir();
                cage(level, isNext ? NEXT : wrong ? RED : WHITE, pos);
                Direction wanted = requirement.expectedFacingOrNull(dir);
                if (wanted != null) arrow(level, pos, wanted, wrong ? RED : WHITE);
            }
            if (isNext) beam(level, pos, BEAM);
        }

        // Planned and the deck is done: the only thing left in this step is the core itself.
        if (planned && next == null) {
            if (drawCages) cage(level, CORE_BEAM, anchor);
            beam(level, anchor, CORE_BEAM);
        }
    }

    /** Finished cannon: a gold cage on the core while it still needs loading, a green beam on the lever once it can fire. */
    private static void drawLaunchCue(ClientLevel level, BlockPos core, Direction dir) {
        if (!(level.getBlockEntity(core) instanceof FarlandsCannonCoreBlockEntity cannon)) return;
        switch (LaunchChecklist.of(level, core, cannon).step()) {
            case LOAD_PEARL, ADD_FUEL -> {
                if (ticks % 4 == 0) cage(level, CORE_BEAM, core);
            }
            case RESET_LEVER, FIRE -> beam(level, CannonStructure.transform(core, dir, 0, 1, 0), GREEN);
            case COOLING_DOWN -> {}
        }
    }

    private static void beam(ClientLevel level, BlockPos pos, DustParticleOptions color) {
        double x = pos.getX() + 0.5;
        double z = pos.getZ() + 0.5;
        double base = pos.getY() + 0.5;
        for (double h = 0.0; h <= 1.6; h += 0.2) spawn(level, color, x, base + h, z);
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
        corners(level, color, pos);
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

    private static void arrow(ClientLevel level, BlockPos pos, Direction dir, DustParticleOptions color) {
        double fx = dir.getStepX();
        double fy = dir.getStepY();
        double fz = dir.getStepZ();
        Direction side = dir.getClockWise();
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
