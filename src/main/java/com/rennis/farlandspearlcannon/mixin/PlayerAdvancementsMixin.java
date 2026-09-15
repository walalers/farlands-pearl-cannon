package com.rennis.farlandspearlcannon.mixin;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.rennis.farlandspearlcannon.advancement.ModAdvancements;

/** Fabric stand-in for NeoForge's AdvancementEarnEvent: fires once, when an advancement goes from unfinished to done. */
@Mixin(PlayerAdvancements.class)
public abstract class PlayerAdvancementsMixin {
    @Shadow private ServerPlayer player;

    @Unique private boolean farlands_pearl_cannon$wasDone;

    @Inject(method = "award", at = @At("HEAD"))
    private void farlands_pearl_cannon$beforeAward(AdvancementHolder advancement, String criterion, CallbackInfoReturnable<Boolean> cir) {
        farlands_pearl_cannon$wasDone = ((PlayerAdvancements) (Object) this).getOrStartProgress(advancement).isDone();
    }

    @Inject(method = "award", at = @At("RETURN"))
    private void farlands_pearl_cannon$afterAward(AdvancementHolder advancement, String criterion, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() || farlands_pearl_cannon$wasDone) return;
        if (((PlayerAdvancements) (Object) this).getOrStartProgress(advancement).isDone()) {
            ModAdvancements.onEarned(player, advancement);
        }
    }
}
