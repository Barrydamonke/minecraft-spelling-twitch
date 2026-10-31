package com.barrydamonke.twitchspelling;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.barrydamonke.twitchspelling.config.ModConfig;
import com.barrydamonke.twitchspelling.core.Platform;
import com.barrydamonke.twitchspelling.core.Text;
import com.barrydamonke.twitchspelling.spell.Target;
import com.barrydamonke.twitchspelling.spell.WordDictionary;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.Util;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** The Minecraft 1.16.1 side of the mod: spawning, chat and registries. */
public final class Platform1161 implements Platform {
	private MinecraftServer server;

	void setServer(MinecraftServer server) {
		this.server = server;
	}

	@Override
	public int tickCount() {
		return server.getTickCount();
	}

	@Override
	public Map<String, Target> registryWords() {
		Map<String, Target> words = new LinkedHashMap<>();
		// Mobs first, then items, then blocks: when names clash, the first one wins.
		for (ResourceLocation id : sorted(Registry.ENTITY_TYPE)) {
			if (Registry.ENTITY_TYPE.get(id).canSummon()) {
				WordDictionary.addRegistryEntry(words, id.getNamespace(), id.getPath(), Target.Kind.ENTITY);
			}
		}
		for (ResourceLocation id : sorted(Registry.ITEM)) {
			if (Registry.ITEM.get(id) != Items.AIR && !id.getPath().endsWith("_spawn_egg")) {
				WordDictionary.addRegistryEntry(words, id.getNamespace(), id.getPath(), Target.Kind.ITEM);
			}
		}
		for (ResourceLocation id : sorted(Registry.BLOCK)) {
			if (!Registry.BLOCK.get(id).defaultBlockState().isAir()) {
				WordDictionary.addRegistryEntry(words, id.getNamespace(), id.getPath(), Target.Kind.BLOCK);
			}
		}
		return words;
	}

	private static List<ResourceLocation> sorted(Registry<?> registry) {
		List<ResourceLocation> ids = new ArrayList<>(registry.keySet());
		Collections.sort(ids);
		return ids;
	}

	@Override
	public Path configDir() {
		return FabricLoader.getInstance().getConfigDir();
	}

	@Override
	public void log(String message) {
		TwitchSpellingMod.LOGGER.info(message);
	}

	@Override
	public void warn(String message) {
		TwitchSpellingMod.LOGGER.warn(message);
	}

	@Override
	public void broadcast(Text text) {
		server.getPlayerList().broadcastMessage(toComponent(text), ChatType.SYSTEM, Util.NIL_UUID);
	}

	@Override
	public void showOverlay(Text text) {
		Component component = toComponent(text);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			player.displayClientMessage(component, true);
		}
	}

	static Component toComponent(Text text) {
		MutableComponent out = new TextComponent("");
		for (Text.Part part : text.parts()) {
			out.append(new TextComponent(part.text)
					.withStyle(style -> style.withColor(TextColor.fromRgb(part.color)).withBold(part.bold)));
		}
		return out;
	}

	@Override
	public String targetProblem(ModConfig config) {
		if (findPlayer(config) != null) {
			return null;
		}
		return config.targetPlayer.isEmpty()
				? "nobody is online to summon it near"
				: config.targetPlayer + " isn't online";
	}

	private ServerPlayer findPlayer(ModConfig config) {
		if (!config.targetPlayer.isEmpty()) {
			return server.getPlayerList().getPlayerByName(config.targetPlayer);
		}
		List<ServerPlayer> players = server.getPlayerList().getPlayers();
		return players.isEmpty() ? null : players.get(0);
	}

	@Override
	public void spawnEntity(Target target, ModConfig config) {
		ServerPlayer player = findPlayer(config);
		if (player == null) {
			return;
		}
		ServerLevel level = player.getLevel();
		Registry.ENTITY_TYPE.get(new ResourceLocation(target.id())).spawn(level, null, null, null,
				findSpot(level, player.blockPosition(), config.spawnRadius, level.getRandom()), MobSpawnType.COMMAND, false, false);
	}

	@Override
	public void dropItem(Target target, int count, ModConfig config) {
		ServerPlayer player = findPlayer(config);
		if (player == null) {
			return;
		}
		ServerLevel level = player.getLevel();
		level.addFreshEntity(new ItemEntity(level, player.getX(), player.getY() + 1, player.getZ(),
				new ItemStack(Registry.ITEM.get(new ResourceLocation(target.id())), count)));
	}

	@Override
	public void placeBlock(Target target, ModConfig config) {
		ServerPlayer player = findPlayer(config);
		if (player == null) {
			return;
		}
		ServerLevel level = player.getLevel();
		level.setBlockAndUpdate(findSpot(level, player.blockPosition(), config.spawnRadius, level.getRandom()),
				Registry.BLOCK.get(new ResourceLocation(target.id())).defaultBlockState());
	}

	@Override
	public void runCommand(Target target, ModConfig config) {
		ServerPlayer player = findPlayer(config);
		if (player == null) {
			return;
		}
		CommandSourceStack source = server.createCommandSourceStack()
				.withLevel(player.getLevel())
				.withPosition(player.position())
				.withEntity(player)
				.withSuppressedOutput();
		server.getCommands().performCommand(source, target.command());
	}

	@Override
	public int maxStackSize(Target item) {
		return Registry.ITEM.get(new ResourceLocation(item.id())).getMaxStackSize();
	}

	/**
	 * Picks a random open spot near {@code center}: two blocks of air with ground underneath,
	 * at least two blocks away from the player so nothing lands on their head.
	 */
	private static BlockPos findSpot(ServerLevel level, BlockPos center, int spawnRadius, Random random) {
		int radius = Math.max(2, spawnRadius);
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
