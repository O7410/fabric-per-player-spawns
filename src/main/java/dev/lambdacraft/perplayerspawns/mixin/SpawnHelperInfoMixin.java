package dev.lambdacraft.perplayerspawns.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.lambdacraft.perplayerspawns.access.InfoAccess;
import dev.lambdacraft.perplayerspawns.access.ServerChunkManagerMixinAccess;
import dev.lambdacraft.perplayerspawns.util.PlayerMobCountMap;
import dev.lambdacraft.perplayerspawns.util.PlayerDistanceMap;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.SpawnHelper;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SpawnHelper.Info.class)
public class SpawnHelperInfoMixin implements InfoAccess {

    @Unique private final PlayerMobCountMap playerMobCountMap = new PlayerMobCountMap();
    @Unique private PlayerDistanceMap playerDistanceMap;
    @Unique private ChunkPos currentIterChunkPos;

    @Override
    public void fabric_per_player_spawns$incrementPlayerMobCount(ServerPlayerEntity playerEntity, SpawnGroup spawnGroup) {
        this.playerMobCountMap.incrementPlayerMobCount(playerEntity, spawnGroup);
    }

    @Override
    public void fabric_per_player_spawns$setChunkManager(ServerChunkManagerMixinAccess chunkManager) {
        //this.world = chunkManager.getServerWorld();
        this.playerDistanceMap = chunkManager.fabric_per_player_spawns$getPlayerDistanceMap();
    }

    @Override
    public int fabric_per_player_spawns$isBelowChunkCap(SpawnGroup spawnGroup, long chunkPosLong) {
        //if ( // too lazy to add proper settings
        //        !world.getPlayers(p -> !p.isSpectator()).size() >= 2
        //) return isBelowCap(spawnGroup); else {

        // Compute if mobs should be spawned between all players in range of chunk
        int cap = spawnGroup.getCapacity();
        for (ServerPlayerEntity player : playerDistanceMap.getPlayersInRange(chunkPosLong)) {
            int mobCountNearPlayer = playerMobCountMap.getPlayerMobCount(player, spawnGroup);
            if (cap <= mobCountNearPlayer) return Integer.MAX_VALUE;
        }
        return 0;

        //}
    }

    @Override
    public void fabric_per_player_spawns$setCurrentIterChunkPos(ChunkPos currentIterChunkPos) {
        this.currentIterChunkPos = currentIterChunkPos;
    }

    @Inject(method = "run", at = @At("HEAD"))
    private void addSpawnedMobToMap(MobEntity entity, Chunk chunk, CallbackInfo callbackInfo) {
        for (ServerPlayerEntity player : playerDistanceMap.getPlayersInRange(chunk.getPos().toLong())) {
            // Increment player's sighting of entity
            fabric_per_player_spawns$incrementPlayerMobCount(player, entity.getType().getSpawnGroup());
        }
    }

    // My way to ensure chunk is right
    @ModifyExpressionValue(method = "isBelowCap", at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/objects/Object2IntOpenHashMap;getInt(Ljava/lang/Object;)I", remap = false))
    private int isBelowChunkCap0(int original, SpawnGroup group) {
        return group.isRare() || this.currentIterChunkPos == null ? original : this.fabric_per_player_spawns$isBelowChunkCap(group, this.currentIterChunkPos.toLong());
    }

}
