package com.barrydamonke.twitchspelling.spell;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Tracks the letters chat has typed so far and decides when a word has been spelled.
 *
 * <ul>
 *   <li>Each message must be a single letter, and each letter must come from a different user
 *       than the one before.</li>
 *   <li>A complete word fires straight away if no longer word starts with it (COW), or if it is
 *       an instant word (TNT).</li>
 *   <li>If a longer word could still follow (PIG, then PIGLIN), the word waits. It fires as soon as
 *       anything breaks the chain: a letter that can't continue it, a message that isn't a single
 *       letter, or the same user typing twice.</li>
 * </ul>
 */
public final class SpellMatcher {
	/** A word chat finished spelling, and the users who typed its letters, in order. */
	public static final class Spelled {
		private final String word;
		private final List<String> users;

		public Spelled(String word, List<String> users) {
			this.word = word;
			this.users = Collections.unmodifiableList(new ArrayList<>(users));
		}

		public String word() {
			return word;
		}

		public List<String> users() {
			return users;
		}

		@Override
		public boolean equals(Object o) {
			return o instanceof Spelled && word.equals(((Spelled) o).word) && users.equals(((Spelled) o).users);
		}

		@Override
		public int hashCode() {
			return Objects.hash(word, users);
		}

		@Override
		public String toString() {
			return word + " " + users;
		}
	}

	private final WordList words;
	private final StringBuilder buffer = new StringBuilder();
	private final List<String> users = new ArrayList<>();
	private String lastUser;

	public SpellMatcher(WordList words) {
		this.words = words;
	}

	/** Feeds one chat message in. Returns the words it caused to be spelled (usually none or one). */
	public List<Spelled> accept(String user, String message) {
		List<Spelled> spelled = new ArrayList<>();
		String text = message.trim();
		if (text.length() != 1 || !isAsciiLetter(text.charAt(0))) {
			flush(spelled);
			return spelled;
		}
		char letter = Character.toUpperCase(text.charAt(0));

		if (user.equalsIgnoreCase(lastUser)) {
			flush(spelled);
		}

		String candidate = buffer.toString() + letter;
		if (!words.isPrefix(candidate)) {
			flush(spelled);
			candidate = String.valueOf(letter);
			if (!words.isPrefix(candidate)) {
				return spelled;
			}
		}

		buffer.append(letter);
		users.add(user);
		lastUser = user;

		if (words.firesImmediately(candidate)) {
			flush(spelled);
		}
		return spelled;
	}

	/** True when the letters so far are a complete word that is waiting in case a longer word follows (PIG, PIGLIN). */
	public boolean isWordWaiting() {
		return buffer.length() > 0 && words.isWord(buffer.toString());
	}

	/** Gives up waiting for a longer word and fires the waiting one, if there is one. Unfinished letters are kept. */
	public List<Spelled> stopWaiting() {
		List<Spelled> spelled = new ArrayList<>();
		if (isWordWaiting()) {
			flush(spelled);
		}
		return spelled;
	}

	/** The letters typed so far, e.g. "PIG". Empty when nothing is in progress. */
	public String letters() {
		return buffer.toString();
	}

	/** Fires the current letters if they form a word, then starts over. */
	private void flush(List<Spelled> spelled) {
		if (buffer.length() > 0 && words.isWord(buffer.toString())) {
			spelled.add(new Spelled(buffer.toString(), users));
		}
		buffer.setLength(0);
		users.clear();
		lastUser = null;
	}

	private static boolean isAsciiLetter(char c) {
		return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
	}
}
