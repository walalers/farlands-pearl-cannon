package com.rennis.farlandspearlcannon.item.custom;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import com.rennis.farlandspearlcannon.block.custom.FarlandsCannonCoreBlock;
import com.rennis.farlandspearlcannon.item.CannonManual;
import com.rennis.farlandspearlcannon.launch.CannonStructure;

public class BlueprintProjectorItem extends Item {
    public BlueprintProjectorItem(Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level) || !(context.getPlayer() instanceof ServerPlayer player)) return InteractionResult.SUCCESS;
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof FarlandsCannonCoreBlock)) {
            player.sendOverlayMessage(Component.literal("Place a Farlands Cannon Core, then use the blueprint on it."));
            return InteractionResult.SUCCESS;
        }
        if (player.isShiftKeyDown()) {
            player.sendSystemMessage(Component.literal("Farlands Pearl Cannon materials:").withStyle(ChatFormatting.AQUA));
            for (String line : CannonStructure.materialList()) {
                player.sendSystemMessage(Component.literal("  • " + line).withStyle(ChatFormatting.GRAY));
            }
        } else {
            // The guide is automatic now: just hold the projector near the core. Use here gives the manual.
            player.sendOverlayMessage(Component.literal("Guide active — hold the projector (either hand) to see the next step.")
                    .withStyle(ChatFormatting.AQUA));
            if (CannonManual.giveIfMissing(player)) {
                player.sendSystemMessage(Component.literal("Added the Cannon Field Manual to your inventory — right-click it to read.").withStyle(ChatFormatting.GOLD));
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        // Sneak + use in the air to fetch (or replace) the Field Manual.
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide()) {
                if (CannonManual.giveIfMissing(player)) {
                    player.sendSystemMessage(Component.literal("Added the Cannon Field Manual to your inventory.").withStyle(ChatFormatting.GOLD));
                } else {
                    player.sendOverlayMessage(Component.literal("You already have the Cannon Field Manual."));
                }
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay displayComponent, Consumer<Component> textConsumer, TooltipFlag type) {
        textConsumer.accept(Component.literal("Hold near a Cannon Core (either hand) to guide the build.").withStyle(ChatFormatting.AQUA));
        textConsumer.accept(Component.literal("Shows one step at a time; a beam marks the next block.").withStyle(ChatFormatting.GRAY));
        textConsumer.accept(Component.literal("White = place here, green = done, red = wrong block.").withStyle(ChatFormatting.GRAY));
        textConsumer.accept(Component.literal("Arrows show which way a block must face.").withStyle(ChatFormatting.GRAY));
        textConsumer.accept(Component.literal("Sneak-use in air: get the Cannon Field Manual.").withStyle(ChatFormatting.GRAY));
        textConsumer.accept(Component.literal("Creative: Farlands Pearl Cannon tab or search 'blueprint'.").withStyle(ChatFormatting.DARK_GRAY));
    }
}
