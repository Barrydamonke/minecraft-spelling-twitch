package com.barrydamonke.twitchspelling.spell;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class SpellMatcherTest {
	private static final WordList WORDS = new WordList(List.of(
			"COW", "PIG", "PIGLIN", "PIGLINBRUTE", "BEE", "BEEF", "BEEHIVE", "TNT", "WITHER", "DIAMONDSWORD"));

	/** Feeds "user:message" lines in order and returns every word spelled. */
	private static List<String> chat(String... lines) {
		SpellMatcher matcher = new SpellMatcher(WORDS);
		List<String> spelled = new ArrayList<>();
		for (String line : lines) {
			int colon = line.indexOf(':');
			matcher.accept(line.substring(0, colon), line.substring(colon + 1))
					.forEach(s -> spelled.add(s.word()));
		}
		return spelled;
	}

	@Test
	void threeUsersSpellCow() {
		assertEquals(List.of("COW"), chat("A:C", "B:O", "C:W"));
	}

	@Test
	void otherMessageBreaksTheChain() {
		assertEquals(List.of(), chat("A:C", "B:HELLO", "C:O", "D:W"));
	}

	@Test
	void sameUserTwiceStartsOver() {
		assertEquals(List.of(), chat("A:C", "A:O", "B:W"));
	}

	@Test
	void sameUserTwiceKeepsTheirSecondLetter() {
		// A's second C starts a fresh sequence, which B and C then finish.
		assertEquals(List.of("COW"), chat("A:C", "A:C", "B:O", "C:W"));
	}

	@Test
	void pigFiresWhenNextLetterCannotContinue() {
		assertEquals(List.of("PIG"), chat("A:P", "B:I", "C:G", "D:A"));
	}

	@Test
	void breakingLetterStartsTheNextWord() {
		assertEquals(List.of("PIG", "TNT"), chat("A:P", "B:I", "C:G", "D:T", "E:N", "F:T"));
	}

	@Test
	void piglinDoesNotAlsoSummonPig() {
		assertEquals(List.of(), chat("A:P", "B:I", "C:G", "D:L", "E:I", "F:N"));
		assertEquals(List.of("PIGLIN"), chat("A:P", "B:I", "C:G", "D:L", "E:I", "F:N", "G:HELLO"));
	}

	@Test
	void longestWordFiresImmediately() {
		assertEquals(List.of("PIGLINBRUTE"),
				chat("A:P", "B:I", "C:G", "D:L", "E:I", "F:N", "G:B", "H:R", "I:U", "J:T", "K:E"));
	}

	@Test
	void pendingWordFiresOnOtherMessage() {
		assertEquals(List.of("PIG"), chat("A:P", "B:I", "C:G", "D:HELLO"));
	}

	@Test
	void pendingWordFiresWhenSameUserTypesAgain() {
		assertEquals(List.of("PIG"), chat("A:P", "B:I", "C:G", "C:L"));
	}

	@Test
	void beeWaitsForBeef() {
		assertEquals(List.of("BEEF"), chat("A:B", "B:E", "C:E", "D:F"));
	}

	@Test
	void incompleteWordIsDropped() {
		// PIGL isn't a word, so a break after it summons nothing.
		assertEquals(List.of(), chat("A:P", "B:I", "C:G", "D:L", "E:HELLO"));
	}

	@Test
	void badLetterStartsOver() {
		assertEquals(List.of(), chat("A:C", "B:O", "C:X"));
		assertEquals(List.of("COW"), chat("A:C", "B:O", "C:X", "D:C", "E:O", "F:W"));
	}

	@Test
	void caseAndSpacesAreIgnored() {
		assertEquals(List.of("COW"), chat("A:c", "B: O ", "C:w"));
	}

	@Test
	void punctuationAndDoubleLettersBreakTheChain() {
		assertEquals(List.of(), chat("A:C", "B:O!", "C:W"));
		assertEquals(List.of(), chat("A:C", "B:OO", "C:W"));
	}

	@Test
	void userNamesCompareCaseInsensitively() {
		assertEquals(List.of(), chat("Ann:C", "ann:O", "Bob:W"));
	}

	@Test
	void stopWaitingFiresTheWaitingWord() {
		SpellMatcher matcher = new SpellMatcher(WORDS);
		matcher.accept("A", "P");
		matcher.accept("B", "I");
		matcher.accept("C", "G");
		assertEquals(true, matcher.isWordWaiting());
		assertEquals(List.of(new SpellMatcher.Spelled("PIG", List.of("A", "B", "C"))), matcher.stopWaiting());
		assertEquals("", matcher.letters());
	}

	@Test
	void stopWaitingKeepsUnfinishedLetters() {
		SpellMatcher matcher = new SpellMatcher(WORDS);
		matcher.accept("A", "P");
		matcher.accept("B", "I");
		matcher.accept("C", "G");
		matcher.accept("D", "L");
		assertEquals(false, matcher.isWordWaiting());
		assertEquals(List.of(), matcher.stopWaiting());
		assertEquals("PIGL", matcher.letters());
	}

	@Test
	void spelledWordListsItsUsers() {
		SpellMatcher matcher = new SpellMatcher(WORDS);
		matcher.accept("Ann", "C");
		matcher.accept("Bob", "O");
		List<SpellMatcher.Spelled> spelled = matcher.accept("Cat", "W");
		assertEquals(List.of(new SpellMatcher.Spelled("COW", List.of("Ann", "Bob", "Cat"))), spelled);
		assertEquals("", matcher.letters());
	}
}
