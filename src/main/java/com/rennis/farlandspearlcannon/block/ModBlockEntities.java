package com.rennis.farlandspearlcannon.block;

import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

import com.rennis.farlandspearlcannon.FarlandsPearlCannonMod;
import com.rennis.farlandspearlcannon.block.entity.FarlandsCannonCoreBlockEntity;

public class ModBlockEntities {
    public static final BlockEntityType<FarlandsCannonCoreBlockEntity> FARLANDS_CANNON_CORE =
            register("farlands_cannon_core", FarlandsCannonCoreBlockEntity::new, ModBlocks.FARLANDS_CANNON_CORE);

    private static <T extends BlockEntity> BlockEntityType<T> register(String name, FabricBlockEntityTypeBuilder.Factory<? extends T> factory, Block... blocks) {
        Identifier id = Identifier.fromNamespaceAndPath(FarlandsPearlCannonMod.MOD_ID, name);
        return Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id, FabricBlockEntityTypeBuilder.<T>create(factory, blocks).build());
    }

    public static void initialize() {
    }
}
