package com.rennis.farlandspearlcannon.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.rennis.farlandspearlcannon.launch.BlueprintProjectionManager;

/** Fabric stand-in for NeoForge's block-place event: lets cannon slots turn pistons and dispensers to face the barrel. */
@Mixin(BlockItem.class)
public abstract class BlockItemMixin {
    @Inject(method = "place", at = @At("RETURN"))
    private void farlands_pearl_cannon$afterPlace(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir) {
        if (!cir.getReturnValue().consumesAction()) return;
        if (!(context.getPlayer() instanceof ServerPlayer) || !(context.getLevel() instanceof ServerLevel level)) return;
        BlueprintProjectionManager.onBlockPlaced(level, context.getClickedPos());
    }
}
