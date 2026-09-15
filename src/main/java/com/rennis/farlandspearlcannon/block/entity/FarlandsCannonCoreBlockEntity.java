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
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
    private static final long NEVER_FIRED = -999999L;

    private boolean pearlLoaded;
    private int fuel;
    private int targetIndex;
    private long lastFireGameTime = NEVER_FIRED;
    private boolean wasPowered;
    // Server config values sent along with the update tag so the client checklist matches the server (-1 = use local config).
    private int syncedFuelRequired = -1;
    private long syncedCooldownTicks = -1L;

    public FarlandsCannonCoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FARLANDS_CANNON_CORE, pos, state);
    }

    public int fuelRequired() { return syncedFuelRequired >= 0 ? syncedFuelRequired : ModConfig.values.fuelRequired; }
    public long cooldownTicks() { return syncedCooldownTicks >= 0 ? syncedCooldownTicks : ModConfig.cooldownTicks(); }
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

    /**
     * Comparator readout so the cannon can be wired into redstone displays: 15 while primed, otherwise a
     * 0-14 progress value that climbs as the pearl and fuel go in.
     */
    public int comparatorSignal(ServerLevel level, BlockPos pos, BlockState state) {
        if (isPrimed(level, pos, state)) return 15;
        int parts = (pearlLoaded ? 1 : 0) + Math.min(fuel, ModConfig.values.fuelRequired);
        int total = 1 + Math.max(1, ModConfig.values.fuelRequired);
        return Math.round(parts * 14.0F / total);
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

    /**
     * Puts a consumed shot back (the rider vanished mid-charge, or the launch could not complete) and
     * clears the cooldown so the player can simply fire again.
     */
    public void refundShot(boolean refundPearl, boolean refundFuel, int fuelRequired) {
        if (refundPearl) pearlLoaded = true;
        if (refundFuel) fuel = Math.min(fuelRequired, fuel + fuelRequired);
        lastFireGameTime = NEVER_FIRED;
        markChanged();
    }

    /** Breaking a loaded core spits its pearl and fuel back out instead of eating them. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level == null || level.isClientSide()) return;
        double x = pos.getX() + 0.5, y = pos.getY() + 0.5, z = pos.getZ() + 0.5;
        if (pearlLoaded) Containers.dropItemStack(level, x, y, z, new ItemStack(Items.ENDER_PEARL));
        if (fuel > 0) Containers.dropItemStack(level, x, y, z, new ItemStack(Items.BLAZE_POWDER, fuel));
        pearlLoaded = false;
        fuel = 0;
    }

    public void markChanged() {
        setChanged();
        if (level != null) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_ALL);
            level.updateNeighbourForOutputSignal(worldPosition, state.getBlock());
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
        lastFireGameTime = input.getLongOr("LastFireGameTime", NEVER_FIRED);
        syncedFuelRequired = input.getIntOr("FuelRequired", -1);
        syncedCooldownTicks = input.getLongOr("CooldownTicks", -1L);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registryLookup) {
        CompoundTag tag = saveWithoutMetadata(registryLookup);
        tag.putInt("FuelRequired", ModConfig.values.fuelRequired);
        tag.putLong("CooldownTicks", ModConfig.cooldownTicks());
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
