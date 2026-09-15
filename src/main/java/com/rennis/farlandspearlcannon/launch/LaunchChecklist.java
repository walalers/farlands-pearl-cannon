package com.rennis.farlandspearlcannon.launch;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.Level;

import com.rennis.farlandspearlcannon.block.entity.FarlandsCannonCoreBlockEntity;

/**
 * What a finished cannon still needs before it can fire, in the order the player should do it. Shared by the
 * server (right-click and loading messages) and the client (HUD checklist and in-world cue), so every surface
 * points at the same next step.
 */
public final class LaunchChecklist {
    public enum Step { LOAD_PEARL, ADD_FUEL, COOLING_DOWN, RESET_LEVER, FIRE }

    public record Status(Step step, boolean pearlLoaded, int fuel, int fuelRequired, long cooldownSeconds, FarlandsTarget target) {
        public boolean ready() {
            return step == Step.RESET_LEVER || step == Step.FIRE;
        }

        /** One plain-language sentence telling the player exactly what to do next. */
        public MutableComponent nextAction() {
            return switch (step) {
                case LOAD_PEARL -> msg("load_pearl");
                case ADD_FUEL -> msg("add_fuel", fuelRequired - fuel);
                case COOLING_DOWN -> msg("cooling_down", cooldownSeconds);
                case RESET_LEVER -> msg("reset_lever");
                case FIRE -> msg("fire");
            };
        }
    }

    private LaunchChecklist() {}

    /** Assumes the structure is already complete (check CannonStructure first). */
    public static Status of(Level level, BlockPos core, FarlandsCannonCoreBlockEntity cannon) {
        int required = cannon.fuelRequired();
        long readyAt = cannon.getLastFireGameTime() + cannon.cooldownTicks();
        long now = level.getGameTime();
        long cooldownSeconds = now < readyAt ? (readyAt - now + 19) / 20 : 0;

        Step step;
        if (!cannon.isPearlLoaded()) step = Step.LOAD_PEARL;
        else if (cannon.getFuel() < required) step = Step.ADD_FUEL;
        else if (cooldownSeconds > 0) step = Step.COOLING_DOWN;
        // The lever was left on while loading, so there is no rising edge left to fire on.
        else if (level.hasNeighborSignal(core)) step = Step.RESET_LEVER;
        else step = Step.FIRE;
        return new Status(step, cannon.isPearlLoaded(), cannon.getFuel(), required, cooldownSeconds, cannon.getTarget());
    }

    private static MutableComponent msg(String key, Object... args) {
        return Component.translatable("message.farlands_pearl_cannon.checklist." + key, args);
    }
}
