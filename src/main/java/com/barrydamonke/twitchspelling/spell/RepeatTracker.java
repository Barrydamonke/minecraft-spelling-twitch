package com.barrydamonke.twitchspelling.spell;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Some words (WITHER, ENDERDRAGON) only summon once chat has spelled them several times in a row.
 * Anything that isn't heading towards another copy of the same word breaks the streak.
 */
public final class RepeatTracker {
	public enum Type {
		/** Summon the word now. */
		SUMMON,
		/** A repeat word was spelled but needs more goes. */
		PROGRESS,
		/** A streak was interrupted and starts over from zero. */
		BROKEN
	}

	/** {@code spelled} is null for BROKEN. {@code count} of {@code needed} spellings done so far. */
	public record Event(Type type, String word, SpellMatcher.Spelled spelled, int count, int needed) {
	}

	/** Words that need repeating, and how many times. */
	private final Map<String, Integer> required;
	private String streakWord;
	private int count;

	public RepeatTracker(Map<String, Integer> required) {
		this.required = required;
	}

	/** Takes the words one chat message spelled and the letters left over after it. */
	public List<Event> onMessage(List<SpellMatcher.Spelled> spelledWords, String letters) {
		List<Event> events = new ArrayList<>();
		for (SpellMatcher.Spelled spelled : spelledWords) {
			String word = spelled.word();
			int needed = required.getOrDefault(word, 1);
			if (needed <= 1) {
				breakStreak(events);
				events.add(new Event(Type.SUMMON, word, spelled, 1, 1));
				continue;
			}
			if (!word.equals(streakWord)) {
				breakStreak(events);
				streakWord = word;
				count = 0;
			}
			count++;
			if (count >= needed) {
				events.add(new Event(Type.SUMMON, word, spelled, count, needed));
				streakWord = null;
			} else {
				events.add(new Event(Type.PROGRESS, word, spelled, count, needed));
			}
		}

		// The streak survives only while chat is on its way to spelling the same word again.
		if (streakWord != null) {
			boolean onTrack = letters.isEmpty()
					? !spelledWords.isEmpty()
					: streakWord.startsWith(letters);
			if (!onTrack) {
				breakStreak(events);
			}
		}
		return events;
	}

	private void breakStreak(List<Event> events) {
		if (streakWord != null) {
			events.add(new Event(Type.BROKEN, streakWord, null, count, required.get(streakWord)));
			streakWord = null;
		}
	}
}
