package com.rennis.farlandspearlcannon.client;

import java.util.List;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import com.rennis.farlandspearlcannon.block.ModBlocks;
import com.rennis.farlandspearlcannon.launch.CannonStructure;

/**
 * Right-side "step card": tells the player which build stage they are on, the exact next block to place,
 * and just that stage's materials with have/needed counts. Reads the core/stage cached by BlueprintGuideClient.
 */
public class BlueprintHudOverlay implements HudElement {
    private static final int PANEL_W = 178;
    private static final int ROW_H = 16;
    private static final int PAD = 7;

    private static final int COLOR_PANEL = 0xC00B0E14;
    private static final int COLOR_ACCENT = 0xFF49E0FF;
    private static final int COLOR_HEADER = 0xFF66E0FF;
    private static final int COLOR_STEP = 0xFFFFD479;
    private static final int COLOR_TEXT = 0xFFE6E6E6;
    private static final int COLOR_SUBTLE = 0xFF9AA4B2;
    private static final int COLOR_DONE = 0xFF55E06A;
    private static final int COLOR_TODO = 0xFFFF7A82;

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || !BlueprintGuideClient.isHolding()) return;

        Font font = minecraft.font;
        BlockPos core = BlueprintGuideClient.core();
        int x0 = graphics.guiWidth() - PANEL_W - 6;

        if (core == null || !minecraft.level.getBlockState(core).is(ModBlocks.FARLANDS_CANNON_CORE)) {
            drawHint(graphics, font, x0);
            return;
        }

        Direction facing = BlueprintGuideClient.facing();
        List<CannonStructure.Stage> stages = CannonStructure.stages();
        int index = CannonStructure.currentStageIndex(minecraft.level, core, facing);
        boolean complete = index >= stages.size();

        List<CannonStructure.Material> stageMats = complete ? List.of() : CannonStructure.stageMaterials(index);
        int contentTop = PAD + 13 + (complete ? 26 : 13 + 20 + stageMats.size() * ROW_H);
        int totalH = contentTop + 12 + PAD;
        int y0 = Math.max(6, (graphics.guiHeight() - totalH) / 2);

        graphics.fill(x0, y0, x0 + PANEL_W, y0 + totalH, COLOR_PANEL);
        graphics.fill(x0, y0, x0 + PANEL_W, y0 + 2, COLOR_ACCENT);

        int y = y0 + PAD;
        graphics.text(font, Component.literal("Cannon Build"), x0 + PAD, y, COLOR_HEADER, true);
        y += 13;

        Inventory inventory = minecraft.player.getInventory();

        if (complete) {
            graphics.text(font, Component.literal("✓ Complete!"), x0 + PAD, y, COLOR_DONE, true);
            y += 12;
            graphics.text(font, Component.literal("Power with redstone, then fire."), x0 + PAD, y, COLOR_SUBTLE, true);
            y += 14;
        } else {
            CannonStructure.Stage stage = stages.get(index);
            graphics.text(font, Component.literal("Step " + (index + 1) + "/" + stages.size() + "  •  " + stage.name()),
                    x0 + PAD, y, COLOR_STEP, true);
            y += 13;

            CannonStructure.Requirement next = stage.firstIncomplete(minecraft.level, core, facing);
            if (next != null) {
                ItemStack icon = new ItemStack(next.accepted()[0].asItem());
                graphics.item(icon, x0 + PAD, y);
                graphics.text(font, Component.literal("Place next:"), x0 + PAD + 20, y + 1, COLOR_SUBTLE, true);
                graphics.text(font, icon.getHoverName(), x0 + PAD + 20, y + 10, COLOR_TEXT, true);
            }
            y += 20;

            for (CannonStructure.Material material : stageMats) {
                int have = countHave(inventory, material);
                boolean ok = have >= material.required();
                ItemStack rowIcon = new ItemStack(material.icon());
                graphics.item(rowIcon, x0 + PAD, y);
                graphics.text(font, rowIcon.getHoverName(), x0 + PAD + 20, y + 4, ok ? COLOR_DONE : COLOR_TEXT, true);
                String countStr = have + "/" + material.required();
                int countX = x0 + PANEL_W - PAD - font.width(countStr);
                graphics.text(font, Component.literal(countStr), countX, y + 4, ok ? COLOR_DONE : COLOR_TODO, true);
                graphics.fill(countX - 10, y + 5, countX - 4, y + 11, ok ? COLOR_DONE : COLOR_TODO);
                y += ROW_H;
            }
        }

        // Stage progress bar across the bottom.
        int done = Math.min(index, stages.size());
        int barX = x0 + PAD;
        int barW = PANEL_W - PAD * 2;
        int barY = y0 + totalH - PAD - 4;
        graphics.fill(barX, barY, barX + barW, barY + 4, 0xFF1B2230);
        graphics.fill(barX, barY, barX + (int) (barW * (done / (float) stages.size())), barY + 4, COLOR_DONE);
        Component progress = Component.literal(done + "/" + stages.size() + " stages");
        graphics.text(font, progress, x0 + PANEL_W - PAD - font.width(progress), barY - 11, COLOR_SUBTLE, true);
    }

    private static void drawHint(GuiGraphicsExtractor graphics, Font font, int x0) {
        int totalH = PAD + 13 + 24 + PAD;
        int y0 = Math.max(6, (graphics.guiHeight() - totalH) / 2);
        graphics.fill(x0, y0, x0 + PANEL_W, y0 + totalH, COLOR_PANEL);
        graphics.fill(x0, y0, x0 + PANEL_W, y0 + 2, COLOR_ACCENT);
        graphics.text(font, Component.literal("Cannon Build"), x0 + PAD, y0 + PAD, COLOR_HEADER, true);
        graphics.text(font, Component.literal("Place a Cannon Core on the"), x0 + PAD, y0 + PAD + 14, COLOR_TEXT, true);
        graphics.text(font, Component.literal("ground to start the guide."), x0 + PAD, y0 + PAD + 24, COLOR_TEXT, true);
    }

    private static int countHave(Inventory inventory, CannonStructure.Material material) {
        int total = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && material.accepted().contains(stack.getItem())) total += stack.getCount();
        }
        return total;
    }
}
