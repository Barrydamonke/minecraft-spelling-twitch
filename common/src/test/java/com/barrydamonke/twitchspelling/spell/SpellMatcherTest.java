package com.barrydamonke.twitchspelling.spell;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

class SpellMatcherTest {
	private static final WordList WORDS = new WordList(Arrays.asList(
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
		assertEquals(Arrays.asList("COW"), chat("A:C", "B:O", "C:W"));
	}

	@Test
	void otherMessageBreaksTheChain() {
		assertEquals(Arrays.asList(), chat("A:C", "B:HELLO", "C:O", "D:W"));
	}

	@Test
	void sameUserTwiceStartsOver() {
		assertEquals(Arrays.asList(), chat("A:C", "A:O", "B:W"));
	}

	@Test
	void sameUserTwiceKeepsTheirSecondLetter() {
		// A's second C starts a fresh sequence, which B and C then finish.
		assertEquals(Arrays.asList("COW"), chat("A:C", "A:C", "B:O", "C:W"));
	}

	@Test
	void pigFiresWhenNextLetterCannotContinue() {
		assertEquals(Arrays.asList("PIG"), chat("A:P", "B:I", "C:G", "D:A"));
	}

	@Test
	void breakingLetterStartsTheNextWord() {
		assertEquals(Arrays.asList("PIG", "TNT"), chat("A:P", "B:I", "C:G", "D:T", "E:N", "F:T"));
	}

	@Test
	void piglinDoesNotAlsoSummonPig() {
		assertEquals(Arrays.asList(), chat("A:P", "B:I", "C:G", "D:L", "E:I", "F:N"));
		assertEquals(Arrays.asList("PIGLIN"), chat("A:P", "B:I", "C:G", "D:L", "E:I", "F:N", "G:HELLO"));
	}

	@Test
	void longestWordFiresImmediately() {
		assertEquals(Arrays.asList("PIGLINBRUTE"),
				chat("A:P", "B:I", "C:G", "D:L", "E:I", "F:N", "G:B", "H:R", "I:U", "J:T", "K:E"));
	}

	@Test
	void pendingWordFiresOnOtherMessage() {
		assertEquals(Arrays.asList("PIG"), chat("A:P", "B:I", "C:G", "D:HELLO"));
	}

	@Test
	void pendingWordFiresWhenSameUserTypesAgain() {
		assertEquals(Arrays.asList("PIG"), chat("A:P", "B:I", "C:G", "C:L"));
	}

	@Test
	void beeWaitsForBeef() {
		assertEquals(Arrays.asList("BEEF"), chat("A:B", "B:E", "C:E", "D:F"));
	}

	@Test
	void incompleteWordIsDropped() {
		// PIGL isn't a word, so a break after it summons nothing.
		assertEquals(Arrays.asList(), chat("A:P", "B:I", "C:G", "D:L", "E:HELLO"));
	}

	@Test
	void badLetterStartsOver() {
		assertEquals(Arrays.asList(), chat("A:C", "B:O", "C:X"));
		assertEquals(Arrays.asList("COW"), chat("A:C", "B:O", "C:X", "D:C", "E:O", "F:W"));
	}

	@Test
	void caseAndSpacesAreIgnored() {
		assertEquals(Arrays.asList("COW"), chat("A:c", "B: O ", "C:w"));
	}

	@Test
	void punctuationAndDoubleLettersBreakTheChain() {
		assertEquals(Arrays.asList(), chat("A:C", "B:O!", "C:W"));
		assertEquals(Arrays.asList(), chat("A:C", "B:OO", "C:W"));
	}

	@Test
	void userNamesCompareCaseInsensitively() {
		assertEquals(Arrays.asList(), chat("Ann:C", "ann:O", "Bob:W"));
	}

	@Test
	void instantWordsDoNotWaitForLongerWords() {
		WordList words = new WordList(Arrays.asList("TNT", "TNTMINECART", "PIG", "PIGLIN"), Arrays.asList("TNT"));
		SpellMatcher matcher = new SpellMatcher(words);
		matcher.accept("A", "T");
		matcher.accept("B", "N");
		assertEquals(Arrays.asList(new SpellMatcher.Spelled("TNT", Arrays.asList("A", "B", "C"))), matcher.accept("C", "T"));
		// Other words still wait.
		matcher.accept("A", "P");
		matcher.accept("B", "I");
		assertEquals(Arrays.asList(), matcher.accept("C", "G"));
		assertEquals("PIG", matcher.letters());
	}

	@Test
	void stopWaitingFiresTheWaitingWord() {
		SpellMatcher matcher = new SpellMatcher(WORDS);
		matcher.accept("A", "P");
		matcher.accept("B", "I");
		matcher.accept("C", "G");
		assertEquals(true, matcher.isWordWaiting());
		assertEquals(Arrays.asList(new SpellMatcher.Spelled("PIG", Arrays.asList("A", "B", "C"))), matcher.stopWaiting());
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
		assertEquals(Arrays.asList(), matcher.stopWaiting());
		assertEquals("PIGL", matcher.letters());
	}

	@Test
	void spelledWordListsItsUsers() {
		SpellMatcher matcher = new SpellMatcher(WORDS);
		matcher.accept("Ann", "C");
		matcher.accept("Bob", "O");
		List<SpellMatcher.Spelled> spelled = matcher.accept("Cat", "W");
		assertEquals(Arrays.asList(new SpellMatcher.Spelled("COW", Arrays.asList("Ann", "Bob", "Cat"))), spelled);
		assertEquals("", matcher.letters());
	}
}
