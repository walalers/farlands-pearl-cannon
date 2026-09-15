package com.rennis.farlandspearlcannon.launch;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import com.rennis.farlandspearlcannon.block.ModBlocks;

public class CannonStructure {
    public enum FacingRule { ANY, CORE_FACING, AWAY_FROM_MUZZLE, TOWARD_CENTER }

    public record Requirement(int stage, int side, int up, int forward, String label, FacingRule facingRule, Block... accepted) {
        public boolean matches(BlockState state, Direction coreFacing) {
            Block found = state.getBlock();
            boolean acceptedBlock = false;
            for (Block acceptedBlockType : accepted) {
                if (found == acceptedBlockType) {
                    acceptedBlock = true;
                    break;
                }
            }
            if (!acceptedBlock) return false;
            if (facingRule == FacingRule.ANY) return true;

            Direction expected = expectedFacing(coreFacing);
            if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
                return state.getValue(BlockStateProperties.HORIZONTAL_FACING) == expected;
            }
            if (state.hasProperty(BlockStateProperties.FACING)) {
                return state.getValue(BlockStateProperties.FACING) == expected;
            }
            return false;
        }

        private Direction expectedFacing(Direction coreFacing) {
            return switch (facingRule) {
                case CORE_FACING -> coreFacing;
                case AWAY_FROM_MUZZLE -> coreFacing.getOpposite();
                case TOWARD_CENTER -> side < 0 ? coreFacing.getClockWise() : coreFacing.getCounterClockWise();
                case ANY -> coreFacing;
            };
        }

        public boolean isOriented() {
            return facingRule != FacingRule.ANY;
        }

        /** Plain-language placement hint for the HUD, or null when any rotation is accepted. */
        public String hintKey() {
            return switch (facingRule) {
                case ANY -> null;
                case TOWARD_CENTER -> "gui.farlands_pearl_cannon.hud.hint.toward_center";
                case CORE_FACING -> "gui.farlands_pearl_cannon.hud.hint.core_facing";
                case AWAY_FROM_MUZZLE -> "gui.farlands_pearl_cannon.hud.hint.away_from_muzzle";
            };
        }

        /** The facing this slot's block must point, or null if orientation does not matter. */
        public Direction expectedFacingOrNull(Direction coreFacing) {
            return facingRule == FacingRule.ANY ? null : expectedFacing(coreFacing);
        }

        public BlockPos worldPos(BlockPos core, Direction facing) {
            return transform(core, facing, side, up, forward);
        }
    }

    /** One build step: a named group of slots the player completes before moving on. */
    public record Stage(String id, List<Requirement> slots) {
        /** Translatable stage name (key stage.farlands_pearl_cannon.<id>). */
        public net.minecraft.network.chat.MutableComponent label() {
            return net.minecraft.network.chat.Component.translatable("stage.farlands_pearl_cannon." + id);
        }

        public boolean isComplete(BlockGetter level, BlockPos core, Direction facing) {
            for (Requirement requirement : slots) {
                if (!requirement.matches(level.getBlockState(requirement.worldPos(core, facing)), facing)) return false;
            }
            return true;
        }

        public int placed(BlockGetter level, BlockPos core, Direction facing) {
            int count = 0;
            for (Requirement requirement : slots) {
                if (requirement.matches(level.getBlockState(requirement.worldPos(core, facing)), facing)) count++;
            }
            return count;
        }

        /** The first slot in this stage that is not yet satisfied, or null if the stage is done. */
        public Requirement firstIncomplete(BlockGetter level, BlockPos core, Direction facing) {
            for (Requirement requirement : slots) {
                if (!requirement.matches(level.getBlockState(requirement.worldPos(core, facing)), facing)) return requirement;
            }
            return null;
        }
    }

    public record Validation(boolean valid, List<String> missing) {
        public String summary() {
            if (missing.isEmpty()) return "ready";
            Map<String, Integer> counts = new LinkedHashMap<>();
            for (String m : missing) counts.put(m, counts.getOrDefault(m, 0) + 1);
            List<String> parts = new ArrayList<>();
            for (Map.Entry<String, Integer> e : counts.entrySet()) parts.add(e.getValue() + "x " + e.getKey());
            return String.join(", ", parts);
        }
    }

    /** One row of a shopping list: an icon item, how many are needed, and every item that satisfies the slot. */
    public record Material(Item icon, int required, List<Item> accepted) {}

    /** Index of the deck stage: the only stage that can be built before the core exists (from a pinned blueprint). */
    public static final int DECK_STAGE = 0;

    private static final List<Stage> STAGES = buildStages();
    private static final List<Requirement> REQUIREMENTS = flatten(STAGES);

    public static List<Stage> stages() {
        return STAGES;
    }

    public static List<Requirement> requirements() {
        return REQUIREMENTS;
    }

    /** Index of the first unfinished stage, or stages().size() when the whole cannon is built. */
    public static int currentStageIndex(BlockGetter level, BlockPos core, Direction facing) {
        for (int i = 0; i < STAGES.size(); i++) {
            if (!STAGES.get(i).isComplete(level, core, facing)) return i;
        }
        return STAGES.size();
    }

    /** Finds the closest placed Cannon Core within the given radius, or null. */
    public static BlockPos findCore(BlockGetter level, BlockPos center, int radius) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    cursor.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    if (level.getBlockState(cursor).is(ModBlocks.FARLANDS_CANNON_CORE)) {
                        double dist = dx * dx + dy * dy + dz * dz;
                        if (dist < bestDist) {
                            bestDist = dist;
                            best = cursor.immutable();
                        }
                    }
                }
            }
        }
        return best;
    }

    /** Aggregated material rows for the whole build (icon + count + accepted items). */
    public static List<Material> materials() {
        return aggregate(ModBlocks.FARLANDS_CANNON_CORE, REQUIREMENTS);
    }

    /** Aggregated material rows for a single stage. */
    public static List<Material> stageMaterials(int stageIndex) {
        if (stageIndex < 0 || stageIndex >= STAGES.size()) return List.of();
        return aggregate(null, STAGES.get(stageIndex).slots());
    }

    /** Aggregated "Nx Label" material lines for the whole build, for the chat readout and the manual. */
    public static List<String> materialList() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("Farlands Cannon Core", 1);
        for (Requirement requirement : REQUIREMENTS) {
            counts.merge(requirement.label(), 1, Integer::sum);
        }
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            lines.add(entry.getValue() + "x " + entry.getKey());
        }
        return lines;
    }

    private static List<Material> aggregate(Block leadingCore, List<Requirement> reqs) {
        LinkedHashMap<Block, Integer> counts = new LinkedHashMap<>();
        LinkedHashMap<Block, LinkedHashSet<Item>> accepts = new LinkedHashMap<>();
        if (leadingCore != null) accumulate(counts, accepts, leadingCore, new Block[]{leadingCore});
        for (Requirement requirement : reqs) {
            accumulate(counts, accepts, requirement.accepted()[0], requirement.accepted());
        }
        List<Material> out = new ArrayList<>();
        for (Map.Entry<Block, Integer> entry : counts.entrySet()) {
            out.add(new Material(entry.getKey().asItem(), entry.getValue(), List.copyOf(accepts.get(entry.getKey()))));
        }
        return out;
    }

    private static void accumulate(Map<Block, Integer> counts, Map<Block, LinkedHashSet<Item>> accepts, Block key, Block[] accepted) {
        counts.merge(key, 1, Integer::sum);
        LinkedHashSet<Item> set = accepts.computeIfAbsent(key, k -> new LinkedHashSet<>());
        for (Block block : accepted) set.add(block.asItem());
    }

    public static Validation validate(Level level, BlockPos core, Direction facing) {
        List<String> missing = new ArrayList<>();
        for (Requirement requirement : REQUIREMENTS) {
            if (!requirement.matches(level.getBlockState(requirement.worldPos(core, facing)), facing)) missing.add(requirement.label);
        }
        return new Validation(missing.isEmpty(), missing);
    }

    public static BlockPos transform(BlockPos core, Direction facing, int side, int up, int forward) {
        Direction right = facing.getClockWise();
        return core.relative(facing, forward).relative(right, side).above(up);
    }

    private static List<Requirement> flatten(List<Stage> stages) {
        List<Requirement> all = new ArrayList<>();
        for (Stage stage : stages) all.addAll(stage.slots());
        return List.copyOf(all);
    }

    private static List<Stage> buildStages() {
        List<Stage> stages = new ArrayList<>();

        // Stage 0 - a solid iron deck (side -2..2, forward 0..4). Gives every redstone line a floor,
        // ties the whole machine together, and reads as one platform with no floating blocks.
        List<Requirement> deck = new ArrayList<>();
        for (int s = -2; s <= 2; s++) {
            for (int f = 0; f <= 4; f++) {
                deck.add(req(0, s, -1, f, "Iron deck plate", Blocks.IRON_BLOCK));
            }
        }
        stages.add(new Stage("foundation_deck", deck));

        // Stage 1 - breech: the pearl magazine sits behind the core with the dial mounted on top.
        stages.add(new Stage("breech_chamber", List.of(
                req(1, 0, 0, -1, "Pearl Chamber", ModBlocks.PEARL_CHAMBER),
                req(1, 0, 1, -1, "Direction Dial", ModBlocks.DIRECTION_DIAL))));

        // Stage 2 - the barrel runs down the centre and flares into a muzzle at the end.
        List<Requirement> barrel = new ArrayList<>();
        for (int f = 1; f <= 7; f++) barrel.add(req(2, 0, 0, f, "Obsidian barrel", Blocks.OBSIDIAN, Blocks.CRYING_OBSIDIAN));
        barrel.add(req(2, 1, 0, 7, "Muzzle flare", Blocks.OBSIDIAN, Blocks.CRYING_OBSIDIAN));
        barrel.add(req(2, -1, 0, 7, "Muzzle flare", Blocks.OBSIDIAN, Blocks.CRYING_OBSIDIAN));
        barrel.add(req(2, 0, 1, 7, "Muzzle flare", Blocks.OBSIDIAN, Blocks.CRYING_OBSIDIAN));
        barrel.add(req(2, 0, -1, 7, "Muzzle flare", Blocks.OBSIDIAN, Blocks.CRYING_OBSIDIAN));
        stages.add(new Stage("obsidian_barrel", barrel));

        // Stage 3 - feed lane: dust carries power from the core out to the rails, with a dispenser and
        // ram piston flanking the breech on each side (symmetric).
        stages.add(new Stage("loaders_feed", List.of(
                req(3, -1, 0, 0, "Redstone dust", Blocks.REDSTONE_WIRE),
                req(3, 1, 0, 0, "Redstone dust", Blocks.REDSTONE_WIRE),
                req(3, -1, 0, 1, "Dispenser (facing the barrel)", FacingRule.TOWARD_CENTER, Blocks.DISPENSER),
                req(3, 1, 0, 1, "Dispenser (facing the barrel)", FacingRule.TOWARD_CENTER, Blocks.DISPENSER),
                req(3, -1, 0, 2, "Piston (facing the barrel)", FacingRule.TOWARD_CENTER, Blocks.PISTON, Blocks.STICKY_PISTON),
                req(3, 1, 0, 2, "Piston (facing the barrel)", FacingRule.TOWARD_CENTER, Blocks.PISTON, Blocks.STICKY_PISTON))));

        // Stage 4 - left control circuit: core -> dust -> repeater -> dust -> comparator -> observer. The circuit is
        // decorative (the lever fires the core), so these accept any rotation; only the pistons/dispensers are oriented.
        stages.add(new Stage("left_circuit", List.of(
                req(4, -2, 0, 0, "Redstone dust", Blocks.REDSTONE_WIRE),
                req(4, -2, 0, 1, "Repeater", Blocks.REPEATER),
                req(4, -2, 0, 2, "Redstone dust", Blocks.REDSTONE_WIRE),
                req(4, -2, 0, 3, "Comparator", Blocks.COMPARATOR),
                req(4, -2, 0, 4, "Observer", Blocks.OBSERVER))));

        // Stage 5 - right control circuit (exact mirror of the left).
        stages.add(new Stage("right_circuit", List.of(
                req(5, 2, 0, 0, "Redstone dust", Blocks.REDSTONE_WIRE),
                req(5, 2, 0, 1, "Repeater", Blocks.REPEATER),
                req(5, 2, 0, 2, "Redstone dust", Blocks.REDSTONE_WIRE),
                req(5, 2, 0, 3, "Comparator", Blocks.COMPARATOR),
                req(5, 2, 0, 4, "Observer", Blocks.OBSERVER))));

        // Stage 6 - sights mounted on top of the barrel, on the centre line.
        stages.add(new Stage("sights_tuning", List.of(
                req(6, 0, 1, 3, "Amethyst tuner", Blocks.AMETHYST_BLOCK),
                req(6, 0, 1, 5, "End rod sight", Blocks.END_ROD))));

        // Stage 7 - the ignition: a lever right on top of the core wires power straight into it.
        // Flip it on (or feed any redstone to the core) and the cannon fires.
        stages.add(new Stage("ignition_lever", List.of(
                req(7, 0, 1, 0, "Ignition Lever (on top of core)", Blocks.LEVER))));

        return List.copyOf(stages);
    }

    private static Requirement req(int stage, int side, int up, int forward, String label, Block... accepted) {
        return new Requirement(stage, side, up, forward, label, FacingRule.ANY, accepted);
    }

    private static Requirement req(int stage, int side, int up, int forward, String label, FacingRule rule, Block... accepted) {
        return new Requirement(stage, side, up, forward, label, rule, accepted);
    }
}
