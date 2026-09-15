package com.rennis.farlandspearlcannon.item;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.component.ItemLore;

/**
 * Static tooltip text shipped as the vanilla lore component (translatable, non-italic) instead of the
 * deprecated appendHoverText override. Keys are tooltip.farlands_pearl_cannon.<item>.<n>.
 */
public final class Tooltips {
    private Tooltips() {}

    public static ItemLore lore(String item, ChatFormatting... colors) {
        List<Component> lines = new ArrayList<>(colors.length);
        for (int i = 0; i < colors.length; i++) {
            lines.add(Component.translatable("tooltip.farlands_pearl_cannon." + item + "." + (i + 1))
                    .withStyle(style -> style.withItalic(false)).withStyle(colors[i]));
        }
        return new ItemLore(List.copyOf(lines));
    }
}
