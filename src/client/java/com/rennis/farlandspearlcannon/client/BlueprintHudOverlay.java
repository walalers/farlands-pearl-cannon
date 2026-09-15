package com.rennis.farlandspearlcannon.client;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.rennis.farlandspearlcannon.block.ModBlocks;
import com.rennis.farlandspearlcannon.block.entity.FarlandsCannonCoreBlockEntity;
import com.rennis.farlandspearlcannon.launch.CannonStructure;
import com.rennis.farlandspearlcannon.launch.LaunchChecklist;

/**
 * Right-side "step card": tells the player which build stage they are on, the exact next block to place
 * (with a plain-language hint when its rotation matters), and just that stage's materials with have/needed
 * counts. Once the structure is finished it turns into a launch checklist (heading, pearl, fuel, next action).
 * Reads the core / pinned blueprint cached by BlueprintGuideClient.
 */
public class BlueprintHudOverlay implements HudElement {
    private static final int PANEL_W = 178;
    private static final int INNER_W = PANEL_W - 14;
    private static final int ROW_H = 16;
    private static final int LINE_H = 10;
    private static final int PAD = 7;

    private static final int COLOR_PANEL = 0xC00B0E14;
    private static final int COLOR_ACCENT = 0xFF49E0FF;
    private static final int COLOR_HEADER = 0xFF66E0FF;
    private static final int COLOR_STEP = 0xFFFFD479;
    private static final int COLOR_TEXT = 0xFFE6E6E6;
    private static final int COLOR_SUBTLE = 0xFF9AA4B2;
    private static final int COLOR_HINT = 0xFFB8D8FF;
    private static final int COLOR_DONE = 0xFF55E06A;
    private static final int COLOR_TODO = 0xFFFF7A82;

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || !BlueprintGuideClient.isHolding()) return;

        Font font = minecraft.font;
        int x0 = graphics.guiWidth() - PANEL_W - 6;

        BlockPos core = BlueprintGuideClient.core();
        if (core != null && minecraft.level.getBlockState(core).is(ModBlocks.FARLANDS_CANNON_CORE)) {
            Direction facing = BlueprintGuideClient.facing();
            if (CannonStructure.currentStageIndex(minecraft.level, core, facing) >= CannonStructure.stages().size()) {
                drawChecklistCard(graphics, font, minecraft, x0, core);
            } else {
                drawBuildCard(graphics, font, minecraft, x0, core, facing, false);
            }
            return;
        }
        BlockPos plan = BlueprintGuideClient.planCore();
        if (plan != null) {
            drawBuildCard(graphics, font, minecraft, x0, plan, BlueprintGuideClient.planFacing(), true);
            return;
        }
        drawHint(graphics, font, x0, BlueprintGuideClient.previewing()
                ? Component.translatable("gui.farlands_pearl_cannon.hud.pin_here")
                : Component.translatable("gui.farlands_pearl_cannon.hud.pin_hint"));
    }

    private static void drawBuildCard(GuiGraphicsExtractor graphics, Font font, Minecraft minecraft, int x0,
                                      BlockPos anchor, Direction facing, boolean planned) {
        List<CannonStructure.Stage> stages = CannonStructure.stages();
        int index = planned ? CannonStructure.DECK_STAGE : CannonStructure.currentStageIndex(minecraft.level, anchor, facing);
        CannonStructure.Stage stage = stages.get(index);

        // Everything below the "Place next" row is decided up front so the panel height is exact.
        List<CannonStructure.Material> stageMats = CannonStructure.stageMaterials(index);
        CannonStructure.Requirement next = stage.firstIncomplete(minecraft.level, anchor, facing);
        ItemStack nextIcon = ItemStack.EMPTY;
        List<FormattedCharSequence> hintLines = new ArrayList<>();
        if (next != null) {
            nextIcon = new ItemStack(next.accepted()[0].asItem());
            if (next.hintKey() != null) hintLines.addAll(font.split(Component.translatable(next.hintKey()), INNER_W));
        } else if (planned) {
            nextIcon = new ItemStack(ModBlocks.FARLANDS_CANNON_CORE);
            hintLines.addAll(font.split(Component.translatable("gui.farlands_pearl_cannon.hud.plan_core"), INNER_W));
        }

        int contentTop = PAD + 13 + 13 + 20 + hintLines.size() * LINE_H + (hintLines.isEmpty() ? 0 : 3) + stageMats.size() * ROW_H;
        int totalH = contentTop + 12 + PAD;
        int y0 = drawPanel(graphics, font, x0, totalH);
        int y = y0 + PAD + 13;

        graphics.text(font, Component.translatable("gui.farlands_pearl_cannon.hud.step", index + 1, stages.size(), stage.label()),
                x0 + PAD, y, COLOR_STEP, true);
        y += 13;

        if (!nextIcon.isEmpty()) {
            graphics.item(nextIcon, x0 + PAD, y);
            graphics.text(font, Component.translatable("gui.farlands_pearl_cannon.hud.place_next"), x0 + PAD + 20, y + 1, COLOR_SUBTLE, true);
            graphics.text(font, nextIcon.getHoverName(), x0 + PAD + 20, y + 10, COLOR_TEXT, true);
        }
        y += 20;

        for (FormattedCharSequence line : hintLines) {
            graphics.text(font, line, x0 + PAD, y, COLOR_HINT, true);
            y += LINE_H;
        }
        if (!hintLines.isEmpty()) y += 3;

        Inventory inventory = minecraft.player.getInventory();
        for (CannonStructure.Material material : stageMats) {
            int have = countHave(inventory, material);
            boolean ok = have >= material.required();
            ItemStack rowIcon = new ItemStack(material.icon());
            drawRow(graphics, font, x0, y, rowIcon, rowIcon.getHoverName(), Component.literal(have + "/" + material.required()), ok);
            y += ROW_H;
        }

        drawProgress(graphics, font, x0, y0, totalH, index, stages.size());
    }

    /** The finished-cannon card: heading, pearl, fuel (and cooldown), then the one thing to do next. */
    private static void drawChecklistCard(GuiGraphicsExtractor graphics, Font font, Minecraft minecraft, int x0, BlockPos core) {
        if (!(minecraft.level.getBlockEntity(core) instanceof FarlandsCannonCoreBlockEntity cannon)) return;
        LaunchChecklist.Status status = LaunchChecklist.of(minecraft.level, core, cannon);
        boolean cooling = status.cooldownSeconds() > 0;
        List<FormattedCharSequence> actionLines = font.split(status.nextAction(), INNER_W);

        int rows = cooling ? 4 : 3;
        int totalH = PAD + 13 + 13 + rows * ROW_H + 3 + actionLines.size() * LINE_H + 3 + 12 + PAD;
        int y0 = drawPanel(graphics, font, x0, totalH);
        int y = y0 + PAD + 13;

        graphics.text(font, Component.translatable(status.ready()
                        ? "gui.farlands_pearl_cannon.hud.checklist.ready"
                        : "gui.farlands_pearl_cannon.hud.checklist.title"),
                x0 + PAD, y, status.ready() ? COLOR_DONE : COLOR_STEP, true);
        y += 13;

        drawRow(graphics, font, x0, y, new ItemStack(ModBlocks.DIRECTION_DIAL),
                Component.translatable("gui.farlands_pearl_cannon.hud.checklist.heading"), status.target().label(), null);
        y += ROW_H;
        drawRow(graphics, font, x0, y, new ItemStack(Items.ENDER_PEARL),
                Component.translatable("gui.farlands_pearl_cannon.hud.checklist.pearl"),
                Component.literal((status.pearlLoaded() ? 1 : 0) + "/1"), status.pearlLoaded());
        y += ROW_H;
        boolean fueled = status.fuel() >= status.fuelRequired();
        drawRow(graphics, font, x0, y, new ItemStack(Items.BLAZE_POWDER),
                Component.translatable("gui.farlands_pearl_cannon.hud.checklist.fuel"),
                Component.literal(status.fuel() + "/" + status.fuelRequired()), fueled);
        y += ROW_H;
        if (cooling) {
            drawRow(graphics, font, x0, y, new ItemStack(Items.CLOCK),
                    Component.translatable("gui.farlands_pearl_cannon.hud.checklist.cooldown"),
                    Component.literal(status.cooldownSeconds() + "s"), false);
            y += ROW_H;
        }

        y += 3;
        for (FormattedCharSequence line : actionLines) {
            graphics.text(font, line, x0 + PAD, y, status.ready() ? COLOR_DONE : COLOR_HINT, true);
            y += LINE_H;
        }

        int stageCount = CannonStructure.stages().size();
        drawProgress(graphics, font, x0, y0, totalH, stageCount, stageCount);
    }

    /** Panel background, accent strip and title; returns the panel's top edge. */
    private static int drawPanel(GuiGraphicsExtractor graphics, Font font, int x0, int totalH) {
        int y0 = Math.max(6, (graphics.guiHeight() - totalH) / 2);
        graphics.fill(x0, y0, x0 + PANEL_W, y0 + totalH, COLOR_PANEL);
        graphics.fill(x0, y0, x0 + PANEL_W, y0 + 2, COLOR_ACCENT);
        graphics.text(font, Component.translatable("gui.farlands_pearl_cannon.hud.title"), x0 + PAD, y0 + PAD, COLOR_HEADER, true);
        return y0;
    }

    /** Icon, label and a right-aligned value; {@code ok} colours the value green/red, or null for a neutral value. */
    private static void drawRow(GuiGraphicsExtractor graphics, Font font, int x0, int y, ItemStack icon, Component label, Component value, Boolean ok) {
        graphics.item(icon, x0 + PAD, y);
        int labelColor = ok == null ? COLOR_SUBTLE : ok ? COLOR_DONE : COLOR_TEXT;
        graphics.text(font, label, x0 + PAD + 20, y + 4, labelColor, true);
        int valueX = x0 + PANEL_W - PAD - font.width(value);
        graphics.text(font, value, valueX, y + 4, ok == null ? COLOR_TEXT : ok ? COLOR_DONE : COLOR_TODO, true);
        if (ok != null) graphics.fill(valueX - 10, y + 5, valueX - 4, y + 11, ok ? COLOR_DONE : COLOR_TODO);
    }

    /** Stage progress bar across the bottom of the panel. */
    private static void drawProgress(GuiGraphicsExtractor graphics, Font font, int x0, int y0, int totalH, int done, int total) {
        int barX = x0 + PAD;
        int barW = PANEL_W - PAD * 2;
        int barY = y0 + totalH - PAD - 4;
        graphics.fill(barX, barY, barX + barW, barY + 4, 0xFF1B2230);
        graphics.fill(barX, barY, barX + (int) (barW * (done / (float) total)), barY + 4, COLOR_DONE);
        Component progress = Component.translatable("gui.farlands_pearl_cannon.hud.progress", done, total);
        graphics.text(font, progress, x0 + PANEL_W - PAD - font.width(progress), barY - 11, COLOR_SUBTLE, true);
    }

    private static void drawHint(GuiGraphicsExtractor graphics, Font font, int x0, Component hint) {
        List<FormattedCharSequence> lines = font.split(hint, INNER_W);
        int totalH = PAD + 13 + 2 + lines.size() * LINE_H + PAD;
        int y0 = drawPanel(graphics, font, x0, totalH);
        int y = y0 + PAD + 15;
        for (FormattedCharSequence line : lines) {
            graphics.text(font, line, x0 + PAD, y, COLOR_TEXT, true);
            y += LINE_H;
        }
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
