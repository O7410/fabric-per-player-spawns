package dev.lambdacraft.perplayerspawns.access;

import dev.lambdacraft.perplayerspawns.util.PlayerDistanceMap;

public interface ServerChunkManagerMixinAccess {
    PlayerDistanceMap fabric_per_player_spawns$getPlayerDistanceMap();
}
