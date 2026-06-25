package com.rennis.farlandspearlcannon;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ModInitializer;

import net.minecraft.resources.Identifier;

import com.rennis.farlandspearlcannon.block.ModBlockEntities;
import com.rennis.farlandspearlcannon.block.ModBlocks;
import com.rennis.farlandspearlcannon.command.ModCommands;
import com.rennis.farlandspearlcannon.config.ModConfig;
import com.rennis.farlandspearlcannon.item.ModItems;
import com.rennis.farlandspearlcannon.launch.BlueprintProjectionManager;
import com.rennis.farlandspearlcannon.launch.LaunchManager;
import com.rennis.farlandspearlcannon.network.ModNetworking;

public class FarlandsPearlCannonMod implements ModInitializer {
    public static final String MOD_ID = "farlands_pearl_cannon";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ModConfig.load();
        ModBlocks.initialize();
        ModBlockEntities.initialize();
        ModItems.initialize();
        ModNetworking.initialize();
        ModCommands.initialize();
        LaunchManager.initialize();
        BlueprintProjectionManager.initialize();
        LOGGER.info("Farlands Pearl Cannon loaded.");
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
