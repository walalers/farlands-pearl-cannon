package com.rennis.farlandspearlcannon.block;

import java.util.function.Function;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import com.rennis.farlandspearlcannon.FarlandsPearlCannonMod;
import com.rennis.farlandspearlcannon.block.custom.DirectionDialBlock;
import com.rennis.farlandspearlcannon.block.custom.FarlandsCannonCoreBlock;
import com.rennis.farlandspearlcannon.block.custom.PearlChamberBlock;
import com.rennis.farlandspearlcannon.item.Tooltips;

public class ModBlocks {
    public static final Block FARLANDS_CANNON_CORE = register("farlands_cannon_core", FarlandsCannonCoreBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.OBSIDIAN).strength(12.0F, 1200.0F).sound(SoundType.METAL).lightLevel(state -> 5),
            Tooltips.lore("farlands_cannon_core", ChatFormatting.AQUA, ChatFormatting.GRAY, ChatFormatting.GRAY));

    public static final Block PEARL_CHAMBER = register("pearl_chamber", PearlChamberBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(4.0F, 6.0F).sound(SoundType.COPPER).lightLevel(state -> 4),
            Tooltips.lore("pearl_chamber", ChatFormatting.GRAY));

    public static final Block DIRECTION_DIAL = register("direction_dial", DirectionDialBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.LODESTONE).strength(5.0F, 8.0F).sound(SoundType.METAL).lightLevel(state -> 3),
            Tooltips.lore("direction_dial", ChatFormatting.GRAY, ChatFormatting.GRAY));

    private static Block register(String name, Function<BlockBehaviour.Properties, Block> blockFactory, BlockBehaviour.Properties properties, ItemLore lore) {
        ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(FarlandsPearlCannonMod.MOD_ID, name));
        Block block = blockFactory.apply(properties.setId(blockKey));

        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(FarlandsPearlCannonMod.MOD_ID, name));
        BlockItem blockItem = new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix().component(DataComponents.LORE, lore));
        Registry.register(BuiltInRegistries.ITEM, itemKey, blockItem);

        return Registry.register(BuiltInRegistries.BLOCK, blockKey, block);
    }

    public static void initialize() {
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(tab -> {
            tab.accept(FARLANDS_CANNON_CORE.asItem());
            tab.accept(PEARL_CHAMBER.asItem());
            tab.accept(DIRECTION_DIAL.asItem());
        });
    }
}
