package com.rennis.farlandspearlcannon.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import com.rennis.farlandspearlcannon.block.ModBlockEntities;
import com.rennis.farlandspearlcannon.launch.FarlandsTarget;

public class FarlandsCannonCoreBlockEntity extends BlockEntity {
    private boolean pearlLoaded;
    private int fuel;
    private int targetIndex;
    private long lastFireGameTime = -999999L;
    private boolean wasPowered;

    public FarlandsCannonCoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FARLANDS_CANNON_CORE, pos, state);
    }

    public boolean isPearlLoaded() { return pearlLoaded; }
    public int getFuel() { return fuel; }
    public boolean wasPowered() { return wasPowered; }
    public void setWasPowered(boolean powered) { wasPowered = powered; }
    public FarlandsTarget getTarget() { return FarlandsTarget.values()[targetIndex % FarlandsTarget.values().length]; }
    public long getLastFireGameTime() { return lastFireGameTime; }

    public boolean loadPearl() {
        if (pearlLoaded) return false;
        pearlLoaded = true;
        markChanged();
        return true;
    }

    public boolean addFuel(int maxFuel) {
        if (fuel >= maxFuel) return false;
        fuel++;
        markChanged();
        return true;
    }

    public void setTargetIndex(int index) {
        targetIndex = Math.floorMod(index, FarlandsTarget.values().length);
        markChanged();
    }

    public void consumeForFire(boolean consumePearl, boolean consumeFuel, int fuelRequired, long gameTime) {
        if (consumePearl) pearlLoaded = false;
        if (consumeFuel) fuel = Math.max(0, fuel - fuelRequired);
        lastFireGameTime = gameTime;
        markChanged();
    }

    public void markChanged() {
        setChanged();
        if (level != null) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_ALL);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        output.putBoolean("PearlLoaded", pearlLoaded);
        output.putInt("Fuel", fuel);
        output.putInt("TargetIndex", targetIndex);
        output.putLong("LastFireGameTime", lastFireGameTime);
        super.saveAdditional(output);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        pearlLoaded = input.getBooleanOr("PearlLoaded", false);
        fuel = input.getIntOr("Fuel", 0);
        targetIndex = input.getIntOr("TargetIndex", 0);
        lastFireGameTime = input.getLongOr("LastFireGameTime", -999999L);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registryLookup) {
        return saveWithoutMetadata(registryLookup);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
