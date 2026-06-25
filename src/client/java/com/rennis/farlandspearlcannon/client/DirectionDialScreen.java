package com.rennis.farlandspearlcannon.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import com.rennis.farlandspearlcannon.launch.FarlandsTarget;
import com.rennis.farlandspearlcannon.network.SelectTargetPayload;

/**
 * A compass-style picker: the 8 launch directions laid out around a 3x3 grid (center empty).
 * The current selection is highlighted; clicking one sends it to the server and closes the menu.
 */
public class DirectionDialScreen extends Screen {
    private static final int BUTTON_W = 52;
    private static final int BUTTON_H = 20;
    private static final int COL_STEP = BUTTON_W + 6;
    private static final int ROW_STEP = BUTTON_H + 8;

    private final BlockPos corePos;
    private final int currentIndex;

    public DirectionDialScreen(BlockPos corePos, int currentIndex) {
        super(Minecraft.getInstance(), Minecraft.getInstance().font, Component.literal("Choose Launch Direction"));
        this.corePos = corePos;
        this.currentIndex = currentIndex;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int cy = this.height / 2;

        for (FarlandsTarget target : FarlandsTarget.values()) {
            // xSign/zSign double as compass grid offsets: x = east/west column, z = north/south row.
            int x = cx + target.xSign * COL_STEP - BUTTON_W / 2;
            int y = cy + target.zSign * ROW_STEP - BUTTON_H / 2;

            boolean current = target.ordinal() == currentIndex;
            Component label = current
                    ? Component.literal(target.displayName).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                    : Component.literal(target.displayName);

            Button button = Button.builder(label, b -> select(target)).bounds(x, y, BUTTON_W, BUTTON_H).build();
            this.addRenderableWidget(button);
        }
    }

    private void select(FarlandsTarget target) {
        ClientPlayNetworking.send(new SelectTargetPayload(corePos, target.ordinal()));
        this.onClose();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        int cx = this.width / 2;
        int cy = this.height / 2;

        graphics.centeredText(this.font, this.title, cx, cy - 2 * ROW_STEP - 22, 0xFFFFFFFF);

        Component current = Component.literal("Current: " + FarlandsTarget.values()[currentIndex].displayName)
                .withStyle(ChatFormatting.GRAY);
        graphics.centeredText(this.font, current, cx, cy + 2 * ROW_STEP + 12, 0xFFAAAAAA);
    }
}
