package com.rennis.farlandspearlcannon.command;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;

import com.rennis.farlandspearlcannon.config.ModConfig;
import com.rennis.farlandspearlcannon.item.custom.AnchorPearlItem;

public class ModCommands {
    public static void initialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                Commands.literal("farlandscannon")
                        .then(Commands.literal("return")
                                .requires(ModCommands::canReturn)
                                .executes(context -> {
                                    if (context.getSource().getPlayer() instanceof ServerPlayer player) {
                                        int min = ModConfig.values.returnCommandMinDistance;
                                        if (Math.abs(player.getX()) < min && Math.abs(player.getZ()) < min) {
                                            context.getSource().sendFailure(Component.translatable("message.farlands_pearl_cannon.return.too_close"));
                                            return 0;
                                        }
                                        AnchorPearlItem.teleportHome(player, InteractionHand.MAIN_HAND, false);
                                        context.getSource().sendSuccess(() -> Component.translatable("message.farlands_pearl_cannon.return.success"), false);
                                        return 1;
                                    }
                                    return 0;
                                }))
        ));
    }

    private static boolean canReturn(CommandSourceStack source) {
        if (!ModConfig.values.enableReturnCommand) return false;
        return !ModConfig.values.returnCommandRequiresPermission || Commands.hasPermission(Commands.LEVEL_GAMEMASTERS).test(source);
    }
}
