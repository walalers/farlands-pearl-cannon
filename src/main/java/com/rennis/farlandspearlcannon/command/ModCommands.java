package com.rennis.farlandspearlcannon.command;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

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
                                .requires(source -> ModConfig.values.enableReturnCommand)
                                .executes(context -> {
                                    if (context.getSource().getPlayer() instanceof ServerPlayer player) {
                                        int min = ModConfig.values.returnCommandMinDistance;
                                        if (Math.abs(player.getX()) < min && Math.abs(player.getZ()) < min) {
                                            context.getSource().sendFailure(Component.literal(
                                                    "The return beacon only reaches across the Far Lands — you're too close to home to use it."));
                                            return 0;
                                        }
                                        AnchorPearlItem.teleportHome(player, InteractionHand.MAIN_HAND, false);
                                        context.getSource().sendSuccess(() -> Component.literal("The Far Lands release you — returning home."), false);
                                        return 1;
                                    }
                                    return 0;
                                }))
        ));
    }
}
