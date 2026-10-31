package com.barrydamonke.twitchspelling.spell;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.barrydamonke.twitchspelling.config.ModConfig;

/**
 * Every word chat can spell and what it summons: the game's mobs, items and blocks (gathered by
 * each Minecraft version from its registries), plus the config's aliases and specials.
 */
public final class WordDictionary {
	private final Map<String, Target> targets;
	private final WordList wordList;

	private WordDictionary(Map<String, Target> targets, Set<String> instantWords) {
		this.targets = Collections.unmodifiableMap(targets);
		this.wordList = new WordList(targets.keySet(), instantWords);
	}

	/**
	 * @param registryWords the game's mobs, items and blocks by spelled word, already in priority
	 *     order (see {@link #addRegistryEntry})
	 */
	public static WordDictionary build(Map<String, Target> registryWords, ModConfig config) {
		Map<String, Target> targets = new LinkedHashMap<>(registryWords);

		for (String word : config.blocklist) {
			targets.remove(toWord(word));
		}
		for (Map.Entry<String, String> alias : config.aliases.entrySet()) {
			Target target = targets.get(toWord(alias.getValue()));
			if (target != null && isSpellable(toWord(alias.getKey()))) {
				targets.put(toWord(alias.getKey()), target);
			}
		}
		for (Map.Entry<String, String> special : config.specials.entrySet()) {
			if (isSpellable(toWord(special.getKey()))) {
				targets.put(toWord(special.getKey()), Target.special(special.getValue()));
			}
		}

		Set<String> instant = new TreeSet<>();
		for (String word : config.instantWords) {
			instant.add(toWord(word));
		}
		return new WordDictionary(targets, instant);
	}

	/**
	 * Adds one registry entry, e.g. ("minecraft", "diamond_sword", ITEM). Call it for mobs first,
	 * then items, then blocks: when names clash, the first one added wins. Names with digits or
	 * other characters chat can't spell one letter at a time are skipped.
	 */
	public static void addRegistryEntry(Map<String, Target> words, String namespace, String path, Target.Kind kind) {
		String word = toWord(path);
		if (namespace.equals("minecraft") && isSpellable(word) && !words.containsKey(word)) {
			words.put(word, Target.of(kind, namespace + ":" + path));
		}
	}

	public static boolean isSpellable(String word) {
		return word.matches("[A-Z]+");
	}

	/** {@code diamond_sword} becomes {@code DIAMONDSWORD}. */
	public static String toWord(String name) {
		return name.replace("_", "").replace(" ", "").toUpperCase(Locale.ROOT);
	}

	/** The target for an already-spelled word, or null. */
	public Target lookup(String word) {
		return targets.get(word);
	}

	public WordList wordList() {
		return wordList;
	}
}
