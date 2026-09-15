package com.rennis.farlandspearlcannon.launch;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public enum FarlandsTarget {
    NORTH("North", 0, -1),
    SOUTH("South", 0, 1),
    EAST("East", 1, 0),
    WEST("West", -1, 0),
    NORTHEAST("Northeast", 1, -1),
    NORTHWEST("Northwest", -1, -1),
    SOUTHEAST("Southeast", 1, 1),
    SOUTHWEST("Southwest", -1, 1);

    public final String displayName;
    public final int xSign;
    public final int zSign;

    FarlandsTarget(String displayName, int xSign, int zSign) {
        this.displayName = displayName;
        this.xSign = xSign;
        this.zSign = zSign;
    }

    /** Translatable heading name for menus and messages. */
    public MutableComponent label() {
        return Component.translatable("direction.farlands_pearl_cannon." + name().toLowerCase(java.util.Locale.ROOT));
    }

    public BlockPos targetFrom(BlockPos current, int distance) {
        int x = xSign == 0 ? current.getX() : xSign * distance;
        int z = zSign == 0 ? current.getZ() : zSign * distance;
        return new BlockPos(x, current.getY(), z);
    }
}
