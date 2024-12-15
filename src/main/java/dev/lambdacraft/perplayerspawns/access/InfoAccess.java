package dev.lambdacraft.perplayerspawns.access;

import net.minecraft.entity.SpawnGroup;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.ChunkPos;

public interface InfoAccess {
    void fabric_per_player_spawns$setChunkManager(ServerChunkManagerMixinAccess chunkManager);

    void fabric_per_player_spawns$incrementPlayerMobCount(ServerPlayerEntity playerEntity, SpawnGroup spawnGroup);

    int fabric_per_player_spawns$isBelowChunkCap(SpawnGroup group, long chunkPosLong);

    void fabric_per_player_spawns$setCurrentIterChunkPos(ChunkPos chunkPos);
}
