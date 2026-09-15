package com.rennis.farlandspearlcannon.item.custom;


import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.core.particles.ParticleTypes;

public class AnchorPearlItem extends Item {
    public AnchorPearlItem(Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResult use(Level level, Player user, InteractionHand hand) {
        if (!(user instanceof ServerPlayer player)) return InteractionResult.SUCCESS;
        return teleportHome(player, hand, true);
    }

    public static InteractionResult teleportHome(ServerPlayer player, InteractionHand hand, boolean consume) {
        ServerLevel level = (ServerLevel) player.level();
        BlockPos target = null;
        var respawnConfig = player.getRespawnConfig();
        if (respawnConfig != null) {
            var respawnData = respawnConfig.respawnData();
            target = respawnData.pos();
            ServerLevel respawnLevel = level.getServer().getLevel(respawnData.dimension());
            if (respawnLevel != null) level = respawnLevel;
        }
        if (target == null) {
            var worldSpawn = level.getRespawnData();
            target = worldSpawn.pos();
            ServerLevel spawnLevel = level.getServer().getLevel(worldSpawn.dimension());
            if (spawnLevel != null) level = spawnLevel;
        }
        BlockPos safe = target.above();
        player.teleportTo(level, safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5, java.util.Set.of(), player.getYRot(), player.getXRot(), false);
        level.sendParticles(ParticleTypes.PORTAL, safe.getX() + 0.5, safe.getY() + 1.0, safe.getZ() + 0.5, 60, 0.7, 0.9, 0.7, 0.06);
        level.playSound(null, safe, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
        if (consume && !player.getAbilities().instabuild) {
            ItemStack stack = player.getItemInHand(hand);
            stack.shrink(1);
        }
        return InteractionResult.SUCCESS;
    }

}
