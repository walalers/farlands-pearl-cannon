package com.rennis.farlandspearlcannon.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import com.rennis.farlandspearlcannon.block.ModBlockEntities;
import com.rennis.farlandspearlcannon.block.custom.FarlandsCannonCoreBlock;
import com.rennis.farlandspearlcannon.config.ModConfig;
import com.rennis.farlandspearlcannon.launch.CannonStructure;
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

    /** True when the cannon is fully built, loaded, fueled, and off cooldown — i.e. armed and ready to fire. */
    public boolean isPrimed(ServerLevel level, BlockPos pos, BlockState state) {
        if (!pearlLoaded || fuel < ModConfig.values.fuelRequired) return false;
        if (level.getGameTime() < lastFireGameTime + ModConfig.cooldownTicks()) return false;
        Direction facing = state.getValue(FarlandsCannonCoreBlock.FACING);
        return CannonStructure.validate(level, pos, facing).valid();
    }

    /** Idle ambience: a primed cannon gently glows so you can read its state at a glance. */
    public static void serverTick(Level level, BlockPos pos, BlockState state, FarlandsCannonCoreBlockEntity cannon) {
        if (!(level instanceof ServerLevel server)) return;
        long time = server.getGameTime();
        if (time % 30L != 0L) return; // sample a few times a second, not every tick
        if (!cannon.isPrimed(server, pos, state)) return;

        double x = pos.getX() + 0.5, y = pos.getY() + 1.05, z = pos.getZ() + 0.5;
        server.sendParticles(ParticleTypes.ENCHANT, x, y, z, 4, 0.28, 0.18, 0.28, 0.02);
        server.sendParticles(ParticleTypes.WITCH, x, y, z, 1, 0.2, 0.15, 0.2, 0.0);
        if (time % 150L == 0L) {
            server.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.16F, 1.5F);
        }
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
