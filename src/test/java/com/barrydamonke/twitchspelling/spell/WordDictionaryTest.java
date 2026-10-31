package com.barrydamonke.twitchspelling.spell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import com.barrydamonke.twitchspelling.config.ModConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

/** Checks the dictionary against the real game registries. */
class WordDictionaryTest {
	private static WordDictionary dictionary;

	@BeforeAll
	static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		dictionary = WordDictionary.build(new ModConfig());
	}

	private static String describe(String word) {
		return dictionary.lookup(word)
				.map(t -> t.kind() == WordDictionary.Kind.SPECIAL ? "SPECIAL " + t.command() : t.kind().name() + " " + t.id())
				.orElse("none");
	}

	@Test
	void blocklistedWordsAreLeftOut() {
		assertEquals("none", describe("BEDROCK"));
		assertEquals("none", describe("COMMANDBLOCK"));
		// Big mobs aren't blocked. The wither needs spelling three times instead.
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
		WordDictionary custom = WordDictionary.build(config);
		assertTrue(custom.lookup("DIAMONDSWORD").isEmpty());
		assertEquals("summon tnt ~ ~ ~", custom.lookup("BOOM").orElseThrow().command());
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
