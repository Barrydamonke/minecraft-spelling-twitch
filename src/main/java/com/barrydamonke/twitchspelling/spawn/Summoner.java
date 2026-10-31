package com.barrydamonke.twitchspelling.spawn;

import com.barrydamonke.twitchspelling.config.ModConfig;
import com.barrydamonke.twitchspelling.spell.WordDictionary;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

/** Makes the thing chat spelled appear next to the streamer. */
public final class Summoner {
	private Summoner() {
	}

	/** Summons {@code target} near the target player. Returns a problem to show in chat, or null if it worked. */
	public static String summon(MinecraftServer server, ModConfig config, WordDictionary.Target target) {
		ServerPlayer player = findPlayer(server, config);
		if (player == null) {
			return config.targetPlayer.isBlank()
					? "nobody is online to summon it near"
					: config.targetPlayer + " isn't online";
		}
		ServerLevel level = player.level();
		int amount = Math.clamp(config.amount, 1, 64);
		int radius = Math.max(2, config.spawnRadius);

		switch (target.kind()) {
			case ENTITY -> {
				var type = BuiltInRegistries.ENTITY_TYPE.getValue(target.id());
				for (int i = 0; i < amount; i++) {
					type.spawn(level, findSpot(level, player.blockPosition(), radius, level.getRandom()), EntitySpawnReason.COMMAND);
				}
			}
			case ITEM -> {
				// Always a single item, whatever the amount setting says.
				ItemEntity drop = new ItemEntity(level, player.getX(), player.getY() + 1, player.getZ(),
						new ItemStack(BuiltInRegistries.ITEM.getValue(target.id())));
				level.addFreshEntity(drop);
			}
			case BLOCK -> {
				var state = BuiltInRegistries.BLOCK.getValue(target.id()).defaultBlockState();
				for (int i = 0; i < amount; i++) {
					level.setBlockAndUpdate(findSpot(level, player.blockPosition(), radius, level.getRandom()), state);
				}
			}
			case SPECIAL -> {
				CommandSourceStack source = server.createCommandSourceStack()
						.withLevel(level)
						.withPosition(player.position())
						.withEntity(player)
						.withSuppressedOutput();
				for (int i = 0; i < amount; i++) {
					server.getCommands().performPrefixedCommand(source, target.command());
				}
			}
		}
		return null;
	}

	private static ServerPlayer findPlayer(MinecraftServer server, ModConfig config) {
		if (!config.targetPlayer.isBlank()) {
			return server.getPlayerList().getPlayerByName(config.targetPlayer);
		}
		var players = server.getPlayerList().getPlayers();
		return players.isEmpty() ? null : players.getFirst();
	}

	/**
	 * Picks a random open spot near {@code center}: two blocks of air with ground underneath,
	 * at least two blocks away from the player so nothing lands on their head.
	 */
	private static BlockPos findSpot(ServerLevel level, BlockPos center, int radius, RandomSource random) {
		for (int attempt = 0; attempt < 30; attempt++) {
			int dx = random.nextInt(radius * 2 + 1) - radius;
			int dz = random.nextInt(radius * 2 + 1) - radius;
			if (Math.abs(dx) < 2 && Math.abs(dz) < 2) {
				continue;
			}
			for (int dy = 3; dy >= -3; dy--) {
				BlockPos pos = center.offset(dx, dy, dz);
				if (level.isEmptyBlock(pos) && level.isEmptyBlock(pos.above()) && !level.isEmptyBlock(pos.below())) {
					return pos;
				}
			}
		}
		// Nowhere open nearby (e.g. underwater or in a tight tunnel), so use the player's own spot.
		return center;
	}
}
