package com.barrydamonke.twitchspelling.spell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import com.barrydamonke.twitchspelling.Platform26;
import com.barrydamonke.twitchspelling.config.ModConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

/** Checks the dictionary against the real 26.3 game registries. */
class WordDictionaryTest {
	private static Map<String, Target> registryWords;
	private static WordDictionary dictionary;

	@BeforeAll
	static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		registryWords = new Platform26().registryWords();
		dictionary = WordDictionary.build(registryWords, new ModConfig());
	}

	private static String describe(String word) {
		Target t = dictionary.lookup(word);
		if (t == null) {
			return "none";
		}
		return t.kind() == Target.Kind.SPECIAL ? "SPECIAL " + t.command() : t.kind().name() + " " + t.id();
	}

	@Test
	void blocklistedWordsAreLeftOut() {
		assertEquals("none", describe("BEDROCK"));
		assertEquals("none", describe("COMMANDBLOCK"));
		// Big mobs aren't blocked. The wither has a cooldown instead.
		assertEquals("ENTITY minecraft:wither", describe("WITHER"));
		assertEquals("ENTITY minecraft:ender_dragon", describe("ENDERDRAGON"));
		assertEquals("ENTITY minecraft:warden", describe("WARDEN"));
		assertEquals("ENTITY minecraft:elder_guardian", describe("ELDERGUARDIAN"));
	}

	@Test
	void aliasesAndSpecials() {
		assertEquals("ENTITY minecraft:wolf", describe("DOG"));
		assertEquals("SPECIAL summon lightning_bolt ~3 ~ ~", describe("LIGHTNING"));
	}

	@Test
	void configWordsCanBeWrittenAnyWay() {
		ModConfig config = new ModConfig();
		config.blocklist.add("Diamond Sword");
		config.specials.put("Boom", "/summon tnt ~ ~ ~");
		WordDictionary custom = WordDictionary.build(registryWords, config);
		assertNull(custom.lookup("DIAMONDSWORD"));
		assertEquals("summon tnt ~ ~ ~", custom.lookup("BOOM").command());
	}

	@Test
	void mobsWinOverItems() {
		assertEquals("ENTITY minecraft:cow", describe("COW"));
		assertEquals("ENTITY minecraft:chicken", describe("CHICKEN"));
		assertEquals("ENTITY minecraft:tnt", describe("TNT"));
	}

	@Test
	void itemsWinOverBlocks() {
		assertEquals("ITEM minecraft:diamond_sword", describe("DIAMONDSWORD"));
		assertEquals("ITEM minecraft:stone", describe("STONE"));
		assertEquals("ITEM minecraft:beef", describe("BEEF"));
	}

	@Test
	void tntFiresImmediately() {
		// TNTMINECART exists, but TNT is an instant word by default.
		assertTrue(dictionary.wordList().hasLonger("TNT"));
		assertTrue(dictionary.wordList().firesImmediately("TNT"));
	}

	@Test
	void obsidian() {
		assertEquals("ITEM minecraft:obsidian", describe("OBSIDIAN"));
		assertFalse(dictionary.wordList().hasLonger("OBSIDIAN"));
		SpellMatcher matcher = new SpellMatcher(dictionary.wordList());
		List<SpellMatcher.Spelled> spelled = List.of();
		String[] users = {"A", "B"};
		for (int i = 0; i < "OBSIDIAN".length(); i++) {
			spelled = matcher.accept(users[i % 2], String.valueOf("OBSIDIAN".charAt(i)));
		}
		assertEquals("OBSIDIAN", spelled.getFirst().word());
	}

	@Test
	void blocksWithoutItemsAreIncluded() {
		assertEquals("BLOCK minecraft:water", describe("WATER"));
	}

	@Test
	void unspellableThingsAreLeftOut() {
		assertEquals("none", describe("PLAYER"));
		assertEquals("none", describe("AIR"));
		assertEquals("none", describe("COWSPAWNEGG"));
		assertEquals("none", describe("MUSICDISC13"));
	}

	@Test
	void realWordsMatchTheAgreedExamples() {
		WordList words = dictionary.wordList();
		// COW fires right away, PIG and BEE wait for a possible longer word.
		assertFalse(words.hasLonger("COW"));
		assertTrue(words.hasLonger("PIG"));
		assertTrue(words.hasLonger("BEE"));

		SpellMatcher matcher = new SpellMatcher(words);
		matcher.accept("A", "C");
		matcher.accept("B", "O");
		assertEquals(List.of(new SpellMatcher.Spelled("COW", List.of("A", "B", "C"))), matcher.accept("C", "W"));
	}
}
