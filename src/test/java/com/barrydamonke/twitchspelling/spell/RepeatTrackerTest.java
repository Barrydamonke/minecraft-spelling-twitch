package com.barrydamonke.twitchspelling.spell;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class RepeatTrackerTest {
	private static final WordList WORDS = new WordList(List.of("WITHER", "WITHERSKELETON", "COW"));

	/** Feeds messages from alternating users and returns the events, e.g. "PROGRESS WITHER 1/3". */
	private static List<String> chat(String... messages) {
		SpellMatcher matcher = new SpellMatcher(WORDS);
		RepeatTracker tracker = new RepeatTracker(Map.of("WITHER", 3));
		List<String> events = new ArrayList<>();
		for (int i = 0; i < messages.length; i++) {
			List<SpellMatcher.Spelled> spelled = matcher.accept("user" + (i % 2), messages[i]);
			for (RepeatTracker.Event e : tracker.onMessage(spelled, matcher.letters())) {
				events.add(e.type() + " " + e.word() + " " + e.count() + "/" + e.needed());
			}
		}
		return events;
	}

	private static String[] letters(String word) {
		return word.split("");
	}

	private static String[] concat(String[]... parts) {
		List<String> all = new ArrayList<>();
		for (String[] part : parts) {
			all.addAll(List.of(part));
		}
		return all.toArray(String[]::new);
	}

	@Test
	void normalWordsSummonStraightAway() {
		assertEquals(List.of("SUMMON COW 1/1"), chat(letters("COW")));
	}

	@Test
	void witherThreeTimesInARowSummons() {
		assertEquals(List.of("PROGRESS WITHER 1/3", "PROGRESS WITHER 2/3", "SUMMON WITHER 3/3"),
				chat(concat(letters("WITHERWITHERWITHER"), new String[] {"hi"})));
	}

	@Test
	void otherMessageBetweenGoesBreaksTheStreak() {
		// "hi" finishes the first WITHER, but "lol" then interrupts before another starts.
		assertEquals(List.of("PROGRESS WITHER 1/3", "BROKEN WITHER 1/3"),
				chat(concat(letters("WITHER"), new String[] {"hi", "lol"})));
	}

	@Test
	void finishingMessageThenNewAttemptKeepsTheStreak() {
		assertEquals(List.of("PROGRESS WITHER 1/3", "PROGRESS WITHER 2/3"),
				chat(concat(letters("WITHER"), new String[] {"hi"}, letters("WITHER"), new String[] {"hi"})));
	}

	@Test
	void differentLetterBreaksTheStreak() {
		assertEquals(List.of("PROGRESS WITHER 1/3", "BROKEN WITHER 1/3"),
				chat(concat(letters("WITHER"), letters("WIC"))));
	}

	@Test
	void differentWordBreaksTheStreak() {
		assertEquals(List.of("PROGRESS WITHER 1/3", "BROKEN WITHER 1/3", "SUMMON COW 1/1"),
				chat(concat(letters("WITHER"), letters("COW"))));
	}

	@Test
	void streakStartsAgainAfterBreaking() {
		assertEquals(List.of("PROGRESS WITHER 1/3", "BROKEN WITHER 1/3",
						"PROGRESS WITHER 1/3", "PROGRESS WITHER 2/3", "SUMMON WITHER 3/3"),
				chat(concat(letters("WITHER"), new String[] {"hi", "lol"}, letters("WITHERWITHERWITHER"), new String[] {"hi"})));
	}
}
