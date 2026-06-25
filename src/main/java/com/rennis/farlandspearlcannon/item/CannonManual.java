package com.rennis.farlandspearlcannon.item;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;

import com.rennis.farlandspearlcannon.launch.CannonStructure;

/**
 * A real vanilla written book ("Cannon Field Manual") so the read screen is handled by the game.
 * Pages are generated from the live structure, so the material list can never drift out of date.
 */
public final class CannonManual {
    public static final String TITLE = "Cannon Field Manual";
    private static final int LINES_PER_PAGE = 11;

    private CannonManual() {}

    public static ItemStack create() {
        List<Filterable<Component>> pages = new ArrayList<>();

        pages.add(page(Component.literal("""
                Cannon Field Manual

                Build a redstone ender-pearl cannon and fire yourself to the edge of the world.

                Place a Cannon Core, then hold the Blueprint Projector to see a glowing build guide.""")));

        // Shopping list, generated straight from the structure and split across pages.
        List<String> lines = new ArrayList<>();
        for (CannonStructure.Material material : CannonStructure.materials()) {
            lines.add(material.required() + " x " + new ItemStack(material.icon()).getHoverName().getString());
        }
        int total = lines.size();
        for (int start = 0, part = 1; start < total; start += LINES_PER_PAGE, part++) {
            int end = Math.min(start + LINES_PER_PAGE, total);
            String header = total > LINES_PER_PAGE ? "Materials (" + part + ")" : "Materials";
            pages.add(page(Component.literal(header + "\n\n" + String.join("\n", lines.subList(start, end)))));
        }

        pages.add(page(Component.literal("""
                Building

                1. Place the Cannon Core on the ground, aimed the way it should fire.

                2. Hold the Blueprint Projector (either hand). The guide appears by itself.

                3. It shows one step at a time. A beam marks the exact next block to place.""")));

        pages.add(page(Component.literal("""
                Reading the guide

                White cage = place a block here.
                Green = correct block placed.
                Red = wrong block in this slot.
                Arrow = which way it must face.

                Finish every step to earn 'Far Traveler'.""")));

        pages.add(page(Component.literal("""
                Aiming the cannon

                Right-click the Direction Dial to open the aim menu, then click a heading.

                N/S/E/W fire straight down one axis and keep your other coordinate.

                NE/NW/SE/SW send you to a far corner where two Far Lands walls meet.""")));

        pages.add(page(Component.literal("""
                Firing

                1. Right-click the Direction Dial and pick a heading.
                2. Use an Ender Pearl on the Core to load it.
                3. Add 3 Blaze Powder or Eyes of Ender as fuel.
                4. Stand by the cannon and flip the ignition lever (any redstone to the core works).
                5. The machine roars to life and launches the nearest player. 5 min cooldown.""")));

        pages.add(page(Component.literal("""
                The shot

                When fired, the redstone lights up, the pistons cycle, and a charge races down the barrel.
                You launch the instant it reaches the muzzle.""")));

        pages.add(page(Component.literal("""
                Getting home

                Each launch gives you an Anchor Pearl - use it to return to your bed (or world spawn).

                Lost it, or inventory full? Type /farlandscannon return. It only works out in the Far Lands, so it is never an easy shortcut home.""")));

        WrittenBookContent content = new WrittenBookContent(
                Filterable.passThrough(TITLE), "Shigeo", 0, pages, true);
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, content);
        return book;
    }

    /** Gives the player a manual if they do not already carry one. Returns true if one was added. */
    public static boolean giveIfMissing(Player player) {
        if (hasManual(player)) return false;
        player.getInventory().add(create());
        return true;
    }

    private static boolean hasManual(Player player) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(Items.WRITTEN_BOOK)) {
                WrittenBookContent content = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
                if (content != null && TITLE.equals(content.title().get(false))) return true;
            }
        }
        return false;
    }

    private static Filterable<Component> page(Component component) {
        return Filterable.passThrough(component);
    }
}
