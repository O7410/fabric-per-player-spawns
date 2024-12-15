package dev.lambdacraft.perplayerspawns.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.lambdacraft.perplayerspawns.access.InfoAccess;
import dev.lambdacraft.perplayerspawns.access.ServerChunkManagerMixinAccess;
import dev.lambdacraft.perplayerspawns.util.PlayerDistanceMap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.SpawnHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Iterator;
import java.util.List;

@Mixin(ServerChunkManager.class)
public class ServerChunkManagerMixin implements ServerChunkManagerMixinAccess {
	@Shadow @Final private ServerWorld world;

	@Shadow private boolean spawnAnimals;
	@Shadow private boolean spawnMonsters;
	@Unique private final PlayerDistanceMap playerDistanceMap = new PlayerDistanceMap();

	@Override
	public PlayerDistanceMap fabric_per_player_spawns$getPlayerDistanceMap() {
		return playerDistanceMap;
	}

	@Inject(method = "tickChunks(Lnet/minecraft/util/profiler/Profiler;JLjava/util/List;)V", at = @At(value = "FIELD",
			target = "Lnet/minecraft/server/world/ServerChunkManager;spawnInfo:Lnet/minecraft/world/SpawnHelper$Info;"))
	private void setupSpawning(CallbackInfo ci, @Local SpawnHelper.Info info) {

		/*
			Every all-chunks tick:
			1. Update distance map by adding all players
			2. Reset player's nearby mob counts
			3. Loop through all world's entities and add them to player's counts
	 	*/
		// update distance map
		int watchDistance = ((ServerChunkLoadingManagerAccessor) ((ServerChunkManager) (Object) this).chunkLoadingManager).getWatchDistance();
		playerDistanceMap.update(this.world.getPlayers(), watchDistance);
		InfoAccess infoAccess = (InfoAccess) info;
		infoAccess.fabric_per_player_spawns$setChunkManager(this);

		// calculate mob counts
		Iterator<Entity> worldEntitiesIterator = world.iterateEntities().iterator();
		out:
		while (true) {
			Entity entity;
			MobEntity mobEntity;
			do {
				if (!worldEntitiesIterator.hasNext()) break out;
				entity = worldEntitiesIterator.next();
				if (!(entity instanceof MobEntity)) break;
				mobEntity = (MobEntity) entity;
			} while (mobEntity.isPersistent() || mobEntity.cannotDespawn());

			SpawnGroup spawnGroup = entity.getType().getSpawnGroup();
			if (spawnGroup != SpawnGroup.MISC) {
				BlockPos blockPos = entity.getBlockPos();
				long ll = ChunkPos.toLong(blockPos.getX() >> 4, blockPos.getZ() >> 4);
				// Find players in range of entity
				for (ServerPlayerEntity player : this.playerDistanceMap.getPlayersInRange(ll)) {
					// Increment player's sighting of entity
					infoAccess.fabric_per_player_spawns$incrementPlayerMobCount(player, spawnGroup);
				}
			}
		}

		/* debugging */
/*
		PlayerMobCountMap map = infoAccess.fabric_per_player_spawns$getPlayerMobCountMap();
		for (ServerPlayerEntity player : this.world.getPlayers()) {
			if (!player.getMainHandStack().isOf(Items.GLISTERING_MELON_SLICE)) continue;

			//System.out.println(player.getName().asString() + ": " + Arrays.toString(((PlayerEntityAccess) player).getMobCounts()));
			if (player.isCreative()) {
				int x = ((int) player.getX()) >> 4;
				int z = ((int) player.getZ()) >> 4;
				ServerPlayerEntity playerM = player;
				int mobCountNearPlayer = map.getPlayerMobCount(player, SpawnGroup.MONSTER);
				int mobCountNearPlayerM = map.getPlayerMobCount(playerM, SpawnGroup.MONSTER);
				for (ServerPlayerEntity playerN : playerDistanceMap.getPlayersInRange(ChunkPos.toLong(x, z))) {
					int mobCountNearPlayerN = map.getPlayerMobCount(playerN, SpawnGroup.MONSTER);
					if (mobCountNearPlayerN > mobCountNearPlayerM) {
						playerM = playerN;
						mobCountNearPlayerM = mobCountNearPlayerN;
					}
				}
				player.sendMessage(Text.literal(playerDistanceMap.posMapSize() + " Chunks stored. Caps: You: " + mobCountNearPlayer + "; Highest here - " + playerM.getName().getString() + ": " + mobCountNearPlayerM), true);
			} else if (player.isSpectator()) {
				StringBuilder str = new StringBuilder();
				str.append(playerDistanceMap.posMapSize()).append(" Chunks stored. ");
				str.append("Players affecting this chunk: ");
				int x = ((int) player.getX()) / 16;
				int z = ((int) player.getZ()) / 16;
				for (ServerPlayerEntity playerN : playerDistanceMap.getPlayersInRange(ChunkPos.toLong(x, z))) {
					str.append(playerN.getName().getString()).append(" ")
							.append(map.getPlayerMobCount(playerN, SpawnGroup.MONSTER)).append(", ");
				}
				player.sendMessage(Text.literal(str.toString()), true);
			}
			//if(player.isCreative() && player.isOnFire() && player.isSneaking() && player.isHolding(Items.STRUCTURE_VOID)){
			//	Gson gson = new GsonBuilder().create();
			//	File plF = new File("playerDump.txt");
			//	plF.createNewFile();
			//	System.out.println(gson.toJson(player));
			//	System.out.println(gson.toJson(mobDistanceMap));
			//}
		}
		*/
	}

	@ModifyVariable(method = "tickChunks(Lnet/minecraft/util/profiler/Profiler;JLjava/util/List;)V",
			at = @At(value = "INVOKE", target = "Ljava/util/List;isEmpty()Z"), ordinal = 1)
	private List<SpawnGroup> changeSpawnableGroupsForChunk(List<SpawnGroup> original, @Local ChunkPos chunkPos, @Local(ordinal = 0) boolean bl, @Local SpawnHelper.Info info) {
        if (!bl || (!this.spawnMonsters && !this.spawnAnimals)) {
			return original;
        }
        boolean bl2 = this.world.getLevelProperties().getTime() % 400L == 0L;
		((InfoAccess) info).fabric_per_player_spawns$setCurrentIterChunkPos(chunkPos);
        return SpawnHelper.collectSpawnableGroups(info, this.spawnAnimals, this.spawnMonsters, bl2);
	}
}
