package com.barrydamonke.twitchspelling.spell;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * The set of spellable words, with fast prefix lookups.
 * Words are expected to be uppercase A-Z only.
 */
public final class WordList {
	private final Set<String> words = new HashSet<>();
	/** Every prefix of every word, including the word itself. */
	private final Set<String> prefixes = new HashSet<>();
	/** Prefixes that are strictly shorter than some word, i.e. can still grow. */
	private final Set<String> extendable = new HashSet<>();

	public WordList(Collection<String> words) {
		for (String word : words) {
			add(word);
		}
	}

	private void add(String word) {
		words.add(word);
		for (int i = 1; i <= word.length(); i++) {
			prefixes.add(word.substring(0, i));
			if (i < word.length()) {
				extendable.add(word.substring(0, i));
			}
		}
	}

	public boolean isWord(String s) {
		return words.contains(s);
	}

	/** True if {@code s} is the start of (or equal to) at least one word. */
	public boolean isPrefix(String s) {
		return prefixes.contains(s);
	}

	/** True if some longer word starts with {@code s}. */
	public boolean hasLonger(String s) {
		return extendable.contains(s);
	}

	public int size() {
		return words.size();
	}
}
