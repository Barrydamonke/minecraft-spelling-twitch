package com.barrydamonke.twitchspelling.core;

import java.nio.file.Path;
import java.util.Map;

import com.barrydamonke.twitchspelling.config.ModConfig;
import com.barrydamonke.twitchspelling.spell.Target;

/**
 * Everything the shared code needs from the game. Each Minecraft version implements this.
 * All methods are called on the server thread while a world is running.
 */
public interface Platform {
	/** Server ticks since the world was opened (20 per second). Stops while the game is paused. */
	int tickCount();

	/** Every mob, item and block chat could spell, built with {@code WordDictionary.addRegistryEntry}. */
	Map<String, Target> registryWords();

	Path configDir();

	void log(String message);

	void warn(String message);

	/** Shows a line in every player's chat. */
	void broadcast(Text text);

	/** Shows a short message above every player's hotbar. */
	void showOverlay(Text text);

	/** Why nothing can be summoned right now (e.g. nobody online), or null if it can. */
	String targetProblem(ModConfig config);

	/** Spawns one mob at a random spot near the target player. */
	void spawnEntity(Target target, ModConfig config);

	/** Drops {@code count} of an item on the target player. */
	void dropItem(Target target, int count, ModConfig config);

	/** Places one block at a random spot near the target player. */
	void placeBlock(Target target, ModConfig config);

	/** Runs a special word's command once, at the target player. */
	void runCommand(Target target, ModConfig config);

	/** How many of an item fit in one stack (1 for swords, 16 for ender pearls, 64 for most). */
	int maxStackSize(Target item);
}
