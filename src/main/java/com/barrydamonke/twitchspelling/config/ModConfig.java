package com.barrydamonke.twitchspelling.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Settings stored in {@code config/twitchspelling.json}. Words can be written however is easiest
 * ("ender_dragon", "EnderDragon" and "ENDERDRAGON" all mean the same word).
 */
public final class ModConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	/** The Twitch channel to read chat from. Set with /twitchspelling connect. */
	public String channel = "";

	/** Copy every Twitch chat message into Minecraft chat. */
	public boolean mirrorChat = true;

	/** Who things get summoned around. Leave empty to use the first player online. */
	public String targetPlayer = "";

	/** How many blocks away from the player mobs and blocks can appear. */
	public int spawnRadius = 5;

	/** How many mobs, blocks or special commands per spelled word. Items are always given one at a time. */
	public int amount = 1;

	/**
	 * When a finished word could still become a longer one (PIG could become PIGLIN), how many seconds
	 * to wait for the next letter before summoning it anyway. 0 means wait until the next message.
	 */
	public int longerWordWaitSeconds = 10;

	/** Words chat is not allowed to spell. */
	public List<String> blocklist = new ArrayList<>(List.of(
			"giant",
			"command_block", "chain_command_block", "repeating_command_block", "command_block_minecart",
			"structure_block", "structure_void", "jigsaw", "barrier", "light", "bedrock",
			"end_portal", "end_portal_frame", "end_gateway", "nether_portal",
			"debug_stick", "knowledge_book", "test_block", "test_instance_block"));

	/** Words that only summon after chat spells them this many times in a row. */
	public Map<String, Integer> repeatToSummon = new LinkedHashMap<>(Map.of(
			"wither", 3));

	/** Extra words that summon the same thing as an existing word, e.g. DOG summons a wolf. */
	public Map<String, String> aliases = new LinkedHashMap<>(Map.of(
			"dog", "wolf"));

	/** Words that run a command at the player instead. These win over everything else. */
	public Map<String, String> specials = new LinkedHashMap<>(Map.of(
			"lightning", "summon lightning_bolt ~3 ~ ~",
			"firework", "summon firework_rocket ~ ~1 ~ {LifeTime:30}"));

	public static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("twitchspelling.json");
	}

	/**
	 * Reads the config file, creating it with the defaults the first time.
	 *
	 * @throws IOException if the file can't be read or isn't valid JSON
	 */
	public static ModConfig load() throws IOException {
		Path path = path();
		if (!Files.exists(path)) {
			ModConfig defaults = new ModConfig();
			defaults.save();
			return defaults;
		}
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			ModConfig config = GSON.fromJson(reader, ModConfig.class);
			return config != null ? config : new ModConfig();
		} catch (JsonParseException e) {
			throw new IOException("twitchspelling.json has a mistake in it: " + e.getMessage(), e);
		}
	}

	public void save() throws IOException {
		Files.createDirectories(path().getParent());
		try (Writer writer = Files.newBufferedWriter(path(), StandardCharsets.UTF_8)) {
			GSON.toJson(this, writer);
		}
	}
}
