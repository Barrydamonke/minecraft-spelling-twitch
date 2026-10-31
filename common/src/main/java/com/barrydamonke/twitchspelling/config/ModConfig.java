package com.barrydamonke.twitchspelling.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

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

	/**
	 * How many mobs, blocks or special commands per spelled word. When it's more than one mob, each
	 * appears after a random delay of up to 2 seconds. Items are a random 1-16 instead.
	 */
	public int amount = 1;

	/**
	 * When a finished word could still become a longer one (PIG could become PIGLIN), how many seconds
	 * to wait for the next letter before summoning it anyway. 0 means wait until the next message.
	 */
	public int longerWordWaitSeconds = 10;

	/** Words that summon the moment they're spelled, even though a longer word starts with them. */
	public List<String> instantWords = new ArrayList<>(Arrays.asList("tnt"));

	/** Words that can only be summoned once every so many seconds. */
	public Map<String, Integer> cooldownSeconds = mapOf("wither", 600);

	/** Words chat is not allowed to spell. */
	public List<String> blocklist = new ArrayList<>(Arrays.asList(
			"giant",
			"command_block", "chain_command_block", "repeating_command_block", "command_block_minecart",
			"structure_block", "structure_void", "jigsaw", "barrier", "light", "bedrock",
			"end_portal", "end_portal_frame", "end_gateway", "nether_portal",
			"debug_stick", "knowledge_book", "test_block", "test_instance_block"));

	/** Words that only summon after chat spells them this many times in a row. */
	public Map<String, Integer> repeatToSummon = new LinkedHashMap<>();

	/** Extra words that summon the same thing as an existing word, e.g. DOG summons a wolf. */
	public Map<String, String> aliases = mapOf("dog", "wolf");

	/** Words that run a command at the player instead. These win over everything else. */
	public Map<String, String> specials = mapOf(
			"lightning", "summon lightning_bolt ~3 ~ ~",
			"firework", "summon firework_rocket ~ ~1 ~ {LifeTime:30}");

	/**
	 * Reads the config file, creating it with the defaults the first time.
	 *
	 * @throws IOException if the file can't be read or isn't valid JSON
	 */
	public static ModConfig load(Path path) throws IOException {
		if (!Files.exists(path)) {
			ModConfig defaults = new ModConfig();
			defaults.save(path);
			return defaults;
		}
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			ModConfig config = GSON.fromJson(reader, ModConfig.class);
			return config != null ? config.fillMissing() : new ModConfig();
		} catch (JsonParseException e) {
			throw new IOException("twitchspelling.json has a mistake in it: " + e.getMessage(), e);
		}
	}

	public void save(Path path) throws IOException {
		Files.createDirectories(path.getParent());
		try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
			GSON.toJson(this, writer);
		}
	}

	/** A list or map written as {@code null} in the file would otherwise crash later. */
	private ModConfig fillMissing() {
		ModConfig defaults = new ModConfig();
		if (channel == null) channel = defaults.channel;
		if (targetPlayer == null) targetPlayer = defaults.targetPlayer;
		if (instantWords == null) instantWords = defaults.instantWords;
		if (cooldownSeconds == null) cooldownSeconds = defaults.cooldownSeconds;
		if (blocklist == null) blocklist = defaults.blocklist;
		if (repeatToSummon == null) repeatToSummon = defaults.repeatToSummon;
		if (aliases == null) aliases = defaults.aliases;
		if (specials == null) specials = defaults.specials;
		return this;
	}

	@SuppressWarnings("unchecked")
	private static <V> Map<String, V> mapOf(Object... keysAndValues) {
		Map<String, V> map = new LinkedHashMap<>();
		for (int i = 0; i < keysAndValues.length; i += 2) {
			map.put((String) keysAndValues[i], (V) keysAndValues[i + 1]);
		}
		return map;
	}
}
