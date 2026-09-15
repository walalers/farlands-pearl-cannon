package com.rennis.farlandspearlcannon.item.custom;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import com.rennis.farlandspearlcannon.block.custom.FarlandsCannonCoreBlock;
import com.rennis.farlandspearlcannon.item.CannonManual;
import com.rennis.farlandspearlcannon.launch.BlueprintProjectionManager;
import com.rennis.farlandspearlcannon.launch.CannonStructure;

/**
 * Right-click the ground to place a blueprint, Shift + right-click to move it. Aiming past arm's reach still
 * works (up to {@link #PIN_RANGE} blocks). The server decides every pin and sends it to the client guide.
 */
public class BlueprintProjectorItem extends Item {
    /** How far away the player can point at the ground and still place a blueprint. */
    public static final double PIN_RANGE = 32.0;

    public BlueprintProjectorItem(Properties settings) {
        super(settings);
    }

    /** Where the core would go if the blueprint were pinned on this click: one above the block the click would place. */
    public static BlockPos plannedCore(BlockPos clickedPos, Direction clickedFace) {
        return clickedPos.relative(clickedFace).above();
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) return InteractionResult.SUCCESS;

        handleAim(serverPlayer, level, context.getClickedPos(), context.getClickedFace());
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) return InteractionResult.SUCCESS;

        // The crosshair was past arm's reach: look further along it so pointing at a far spot still works.
        HitResult hit = player.pick(PIN_RANGE, 1.0F, false);
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            handleAim(serverPlayer, level, blockHit.getBlockPos(), blockHit.getDirection());
            return InteractionResult.SUCCESS;
        }

        // Pointing at the sky: Shift + right-click hands out a fresh Field Manual.
        if (player.isShiftKeyDown()) {
            if (CannonManual.giveIfMissing(player)) {
                player.sendSystemMessage(Component.translatable("message.farlands_pearl_cannon.projector.manual_added").withStyle(ChatFormatting.GOLD));
            } else {
                player.sendOverlayMessage(Component.translatable("message.farlands_pearl_cannon.projector.manual_owned"));
            }
        } else {
            player.sendOverlayMessage(Component.translatable("message.farlands_pearl_cannon.projector.too_far"));
        }
        return InteractionResult.SUCCESS;
    }

    private static void handleAim(ServerPlayer player, Level level, BlockPos pos, Direction face) {
        if (!(level.getBlockState(pos).getBlock() instanceof FarlandsCannonCoreBlock)) {
            BlueprintProjectionManager.requestPin(player, pos, face, player.isShiftKeyDown());
            return;
        }
        if (player.isShiftKeyDown()) {
            player.sendSystemMessage(Component.translatable("message.farlands_pearl_cannon.projector.materials_header").withStyle(ChatFormatting.AQUA));
            for (CannonStructure.Material material : CannonStructure.materials()) {
                player.sendSystemMessage(Component.literal("  • " + material.required() + "x ")
                        .append(new ItemStack(material.icon()).getHoverName()).withStyle(ChatFormatting.GRAY));
            }
        } else {
            player.sendOverlayMessage(Component.translatable("message.farlands_pearl_cannon.projector.guide_active").withStyle(ChatFormatting.AQUA));
            if (CannonManual.giveIfMissing(player)) {
                player.sendSystemMessage(Component.translatable("message.farlands_pearl_cannon.projector.manual_added_hint").withStyle(ChatFormatting.GOLD));
            }
        }
    }
}
