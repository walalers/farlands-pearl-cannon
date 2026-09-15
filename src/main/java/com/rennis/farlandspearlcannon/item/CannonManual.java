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

import com.rennis.farlandspearlcannon.config.ModConfig;
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

                Build a giant redstone cannon and blast yourself 12 million blocks to the edge of the world!

                This book shows you how.""")));

        pages.add(page(Component.literal("""
                What to collect

                This is a big build. Gear up first:

                - Blaze rods from the Nether
                - Ender pearls from endermen
                - A diamond pickaxe for obsidian
                - Lots and lots of iron""")));

        pages.add(page(Component.literal("""
                Crafting (1)

                Blueprint Projector:
                map, 2 redstone torches, amethyst shard, paper

                Direction Dial:
                compass, 2 redstone, amethyst shard, quartz

                Your recipe book shows the layout.""")));

        pages.add(page(Component.literal("""
                Crafting (2)

                Cannon Core:
                4 obsidian, eye of ender, lodestone, 2 redstone, copper block

                Pearl Chamber:
                4 glass, 2 copper blocks, copper ingot, ender pearl, redstone""")));

        // Shopping list, generated straight from the structure and split across pages.
        List<String> lines = new ArrayList<>();
        for (CannonStructure.Material material : CannonStructure.materials()) {
            lines.add(material.required() + " x " + new ItemStack(material.icon()).getHoverName().getString());
        }
        int total = lines.size();
        for (int start = 0, part = 1; start < total; start += LINES_PER_PAGE, part++) {
            int end = Math.min(start + LINES_PER_PAGE, total);
            String header = total > LINES_PER_PAGE ? "Shopping list (" + part + ")" : "Shopping list";
            pages.add(page(Component.literal(header + "\n\n" + String.join("\n", lines.subList(start, end)))));
        }

        pages.add(page(Component.literal("""
                Building it

                1. Hold the Blueprint Projector and right-click the ground where the back of the cannon goes.

                2. Fill the glowing outline with iron blocks.

                3. Put the Cannon Core on the gold beam. The guide takes it from there!""")));

        pages.add(page(Component.literal("""
                Reading the guide

                White box = place a block here
                Green = done!
                Red = wrong block

                Pistons and dispensers turn to face the barrel by themselves. Just place them!""")));

        pages.add(page(Component.literal("""
                Aiming

                Right-click the Direction Dial and pick where to fly.

                N, S, E, W: straight out in a line.

                NE, NW, SE, SW: to a far corner of the world.""")));

        pages.add(page(Component.literal("""
                Loading & launching

                1. Pick a direction on the Dial.
                2. Right-click the Core with an Ender Pearl.
                3. Right-click it with %d Blaze Powder or Eyes of Ender.
                4. Stand close and flip the lever!""".formatted(ModConfig.values.fuelRequired))));

        pages.add(page(Component.literal("""
                Stuck?

                Right-click the Core with an empty hand. It tells you what to do next.

                Hold the projector to see a checklist.

                Lever already on? Flip it off and on again.""")));

        pages.add(page(Component.literal("""
                The launch

                The redstone lights up, the pistons pump, and a blast races down the barrel.

                Stay close! You fly the moment it reaches the end.""")));

        pages.add(page(Component.literal("""
                Redstone trick

                Put a comparator next to the Core. It gives a full signal when the cannon is ready.

                Break a loaded Core and you get your pearl and fuel back.""")));

        pages.add(page(Component.literal("""
                Getting home

                Every launch gives you an Anchor Pearl. Right-click it to warp back to your bed.

                Lost it? Type /farlandscannon return. It only works out in the Far Lands.""")));

        WrittenBookContent content = new WrittenBookContent(
                Filterable.passThrough(TITLE), "Shigeo", 0, pages, true);
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, content);
        return book;
    }

    /**
     * Gives the player a manual if they do not already carry one, and swaps an out-of-date copy (from an
     * older version of the mod) for the current pages. Returns true if a manual was added or replaced.
     */
    public static boolean giveIfMissing(Player player) {
        ItemStack fresh = create();
        List<Filterable<Component>> freshPages = fresh.get(DataComponents.WRITTEN_BOOK_CONTENT).pages();
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            WrittenBookContent content = manualContent(inventory.getItem(i));
            if (content == null) continue;
            if (content.pages().equals(freshPages)) return false;
            inventory.setItem(i, fresh);
            return true;
        }
        inventory.add(fresh);
        return true;
    }

    private static WrittenBookContent manualContent(ItemStack stack) {
        if (!stack.is(Items.WRITTEN_BOOK)) return null;
        WrittenBookContent content = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
        return content != null && TITLE.equals(content.title().get(false)) ? content : null;
    }

    private static Filterable<Component> page(Component component) {
        return Filterable.passThrough(component);
    }
}
