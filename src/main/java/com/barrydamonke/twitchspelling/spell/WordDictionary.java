package com.barrydamonke.twitchspelling.spell;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.barrydamonke.twitchspelling.config.ModConfig;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;

/**
 * Every vanilla mob, item and block that chat can spell, keyed by its spelled form
 * ({@code diamond_sword} becomes {@code DIAMONDSWORD}), plus the config's aliases and specials.
 */
public final class WordDictionary {
	public enum Kind { ENTITY, ITEM, BLOCK, SPECIAL }

	/** What a word summons. {@code id} is set for entities, items and blocks; {@code command} for specials. */
	public record Target(Kind kind, Identifier id, String command) {
		public String describe() {
			return kind == Kind.SPECIAL ? "command /" + command : kind.name().toLowerCase(Locale.ROOT) + " " + id;
		}
	}

	private final Map<String, Target> targets;
	private final WordList wordList;

	private WordDictionary(Map<String, Target> targets) {
		this.targets = Collections.unmodifiableMap(targets);
		this.wordList = new WordList(targets.keySet());
	}

	/** Builds the dictionary from the game registries. When names clash, mobs win over items, and items over blocks. */
	public static WordDictionary build(ModConfig config) {
		Map<String, Target> targets = new LinkedHashMap<>();

		for (Identifier id : vanillaIds(BuiltInRegistries.ENTITY_TYPE)) {
			if (BuiltInRegistries.ENTITY_TYPE.getValue(id).canSummon()) {
				put(targets, id, Kind.ENTITY);
			}
		}
		for (Identifier id : vanillaIds(BuiltInRegistries.ITEM)) {
			if (BuiltInRegistries.ITEM.getValue(id) != Items.AIR && !id.getPath().endsWith("_spawn_egg")) {
				put(targets, id, Kind.ITEM);
			}
		}
		for (Identifier id : vanillaIds(BuiltInRegistries.BLOCK)) {
			if (!BuiltInRegistries.BLOCK.getValue(id).defaultBlockState().isAir()) {
				put(targets, id, Kind.BLOCK);
			}
		}

		for (String word : config.blocklist) {
			targets.remove(toWord(word));
		}
		config.aliases.forEach((alias, word) -> {
			Target target = targets.get(toWord(word));
			if (target != null && isSpellable(toWord(alias))) {
				targets.put(toWord(alias), target);
			}
		});
		config.specials.forEach((word, command) -> {
			if (isSpellable(toWord(word))) {
				targets.put(toWord(word), new Target(Kind.SPECIAL, null, command.startsWith("/") ? command.substring(1) : command));
			}
		});
		return new WordDictionary(targets);
	}

	private static List<Identifier> vanillaIds(Registry<?> registry) {
		return registry.keySet().stream()
				.filter(id -> id.getNamespace().equals(Identifier.DEFAULT_NAMESPACE))
				.sorted()
				.toList();
	}

	private static void put(Map<String, Target> targets, Identifier id, Kind kind) {
		String word = toWord(id.getPath());
		// Skip ids with digits or other characters chat can't spell one letter at a time.
		if (isSpellable(word)) {
			targets.putIfAbsent(word, new Target(kind, id, null));
		}
	}

	private static boolean isSpellable(String word) {
		return !word.isEmpty() && word.chars().allMatch(c -> c >= 'A' && c <= 'Z');
	}

	/** {@code diamond_sword} becomes {@code DIAMONDSWORD}. */
	public static String toWord(String name) {
		return name.replace("_", "").replace(" ", "").toUpperCase(Locale.ROOT);
	}

	public Optional<Target> lookup(String word) {
		return Optional.ofNullable(targets.get(word));
	}

	public WordList wordList() {
		return wordList;
	}
}
