package com.rennis.farlandspearlcannon.item;

import java.util.function.Function;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

import com.rennis.farlandspearlcannon.FarlandsPearlCannonMod;
import com.rennis.farlandspearlcannon.block.ModBlocks;
import com.rennis.farlandspearlcannon.item.custom.AnchorPearlItem;
import com.rennis.farlandspearlcannon.item.custom.BlueprintProjectorItem;

public class ModItems {
    public static final Item BLUEPRINT_PROJECTOR = register("blueprint_projector", BlueprintProjectorItem::new, new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
    public static final Item ANCHOR_PEARL = register("anchor_pearl", AnchorPearlItem::new, new Item.Properties().stacksTo(1).rarity(Rarity.RARE));

    public static <T extends Item> T register(String name, Function<Item.Properties, T> itemFactory, Item.Properties settings) {
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(FarlandsPearlCannonMod.MOD_ID, name));
        T item = itemFactory.apply(settings.setId(itemKey));
        Registry.register(BuiltInRegistries.ITEM, itemKey, item);
        return item;
    }

    public static void initialize() {
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, FarlandsPearlCannonMod.id("creative_tab"), FabricCreativeModeTab.builder()
                .title(Component.translatable("itemGroup.farlands_pearl_cannon"))
                .icon(() -> new ItemStack(ModBlocks.FARLANDS_CANNON_CORE))
                .displayItems((parameters, output) -> {
                    output.accept(BLUEPRINT_PROJECTOR);
                    output.accept(CannonManual.create());
                    output.accept(ModBlocks.FARLANDS_CANNON_CORE);
                    output.accept(ModBlocks.PEARL_CHAMBER);
                    output.accept(ModBlocks.DIRECTION_DIAL);
                    output.accept(ANCHOR_PEARL);
                })
                .build());

        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(tab -> {
            tab.accept(BLUEPRINT_PROJECTOR);
            tab.accept(ANCHOR_PEARL);
        });
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(tab -> {
            tab.accept(BLUEPRINT_PROJECTOR);
        });
    }
}
