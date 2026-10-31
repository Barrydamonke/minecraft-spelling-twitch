package com.barrydamonke.twitchspelling.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.barrydamonke.twitchspelling.config.ModConfig;
import com.barrydamonke.twitchspelling.spell.Target;
import com.barrydamonke.twitchspelling.spell.WordDictionary;

/** Runs the game against a pretend Minecraft that just records what it was asked to do. */
class SpellingGameTest {
	@TempDir
	Path configDir;

	private FakePlatform platform;
	private SpellingGame game;

	@BeforeEach
	void setUp() {
		platform = new FakePlatform(configDir);
		game = new SpellingGame(platform, new Random(1));
		game.serverStarted();
	}

	private void spell(String word) {
		for (int i = 0; i < word.length(); i++) {
			game.simulate("user" + (i % 2), String.valueOf(word.charAt(i)));
		}
		game.tick();
	}

	/** Advances time by {@code ticks}, ticking the game each time. */
	private void wait(int ticks) {
		for (int i = 0; i < ticks; i++) {
			platform.tick++;
			game.tick();
		}
	}

	@Test
	void tntIsInstantEvenThoughTntMinecartExists() {
		spell("TNT");
		assertEquals(1, platform.spawned("minecraft:tnt"));
	}

	@Test
	void witherOnlyNeedsSpellingOnce() {
		spell("WITHER");
		game.simulate("x", "hi");
		game.tick();
		assertEquals(1, platform.spawned("minecraft:wither"));
	}

	@Test
	void witherHasATenMinuteCooldown() {
		spell("WITHER");
		game.simulate("x", "hi");
		game.tick();
		assertTrue(platform.lastChat().contains("Next WITHER in 10:00"), platform.lastChat());

		wait(20 * 60);
		spell("WITHER");
		game.simulate("x", "hi");
		game.tick();
		assertEquals(1, platform.spawned("minecraft:wither"));
		assertTrue(platform.lastChat().contains("on cooldown, ready again in 9:00"), platform.lastChat());

		wait(20 * 60 * 9);
		spell("WITHER");
		game.simulate("x", "hi");
		game.tick();
		assertEquals(2, platform.spawned("minecraft:wither"));
	}

	@Test
	void oneMobAppearsStraightAway() {
		spell("COW");
		assertEquals(1, platform.spawned("minecraft:cow"));
	}

	@Test
	void severalMobsAreSpreadOverTwoSeconds() throws IOException {
		game.config().amount = 10;
		game.saveConfig();
		spell("COW");
		int straightAway = platform.spawned("minecraft:cow");
		assertTrue(straightAway < 10, "some cows should wait, got " + straightAway);

		List<Integer> spawnTicks = new ArrayList<>();
		for (int tick = 0; tick <= 40; tick++) {
			int before = platform.spawned("minecraft:cow");
			if (tick > 0) {
				wait(1);
			}
			for (int i = before; i < platform.spawned("minecraft:cow"); i++) {
				spawnTicks.add(tick);
			}
		}
		assertEquals(10, platform.spawned("minecraft:cow"));
		assertTrue(spawnTicks.stream().allMatch(t -> t <= 40));
	}

	@Test
	void itemsComeInRandomAmountsUpToSixteen() {
		for (int i = 0; i < 50; i++) {
			spell("STONE");
		}
		assertEquals(50, platform.itemDrops.size());
		assertTrue(platform.itemDrops.stream().allMatch(n -> n >= 1 && n <= 16), platform.itemDrops.toString());
		assertTrue(platform.itemDrops.stream().anyMatch(n -> n > 1), "should not always be 1");
	}

	@Test
	void unstackableItemsComeOneAtATime() {
		for (int i = 0; i < 10; i++) {
			spell("DIAMONDSWORD");
		}
		assertTrue(platform.itemDrops.stream().allMatch(n -> n == 1), platform.itemDrops.toString());
	}

	@Test
	void smallStacksAreRespected() {
		for (int i = 0; i < 30; i++) {
			spell("EGG");
		}
		assertTrue(platform.itemDrops.stream().allMatch(n -> n >= 1 && n <= 16), platform.itemDrops.toString());
	}

	@Test
	void pigWaitsThenSummonsAfterTenSeconds() {
		spell("PIG");
		assertEquals(0, platform.spawned("minecraft:pig"));
		wait(199);
		assertEquals(0, platform.spawned("minecraft:pig"));
		wait(1);
		assertEquals(1, platform.spawned("minecraft:pig"));
	}

	@Test
	void nobodyOnlineSummonsNothingAndSaysWhy() {
		platform.online = false;
		spell("COW");
		assertEquals(0, platform.spawned("minecraft:cow"));
		assertTrue(platform.lastChat().contains("nobody is online"), platform.lastChat());
	}

	/** A pretend Minecraft with a handful of words. */
	private static final class FakePlatform implements Platform {
		final Path configDir;
		int tick;
		boolean online = true;
		final List<String> chat = new ArrayList<>();
		final List<String> spawnedIds = new ArrayList<>();
		final List<Integer> itemDrops = new ArrayList<>();

		FakePlatform(Path configDir) {
			this.configDir = configDir;
		}

		int spawned(String id) {
			int count = 0;
			for (String s : spawnedIds) {
				if (s.equals(id)) {
					count++;
				}
			}
			return count;
		}

		String lastChat() {
			return chat.isEmpty() ? "" : chat.get(chat.size() - 1);
		}

		@Override
		public int tickCount() {
			return tick;
		}

		@Override
		public Map<String, Target> registryWords() {
			Map<String, Target> words = new LinkedHashMap<>();
			for (String mob : new String[] {"cow", "pig", "piglin", "wither", "wither_skeleton", "tnt", "tnt_minecart"}) {
				WordDictionary.addRegistryEntry(words, "minecraft", mob, Target.Kind.ENTITY);
			}
			for (String item : new String[] {"stone", "diamond_sword", "egg"}) {
				WordDictionary.addRegistryEntry(words, "minecraft", item, Target.Kind.ITEM);
			}
			return words;
		}

		@Override
		public Path configDir() {
			return configDir;
		}

		@Override
		public void log(String message) {
		}

		@Override
		public void warn(String message) {
		}

		@Override
		public void broadcast(Text text) {
			chat.add(text.plain());
		}

		@Override
		public void showOverlay(Text text) {
		}

		@Override
		public String targetProblem(ModConfig config) {
			return online ? null : "nobody is online to summon it near";
		}

		@Override
		public void spawnEntity(Target target, ModConfig config) {
			spawnedIds.add(target.id());
		}

		@Override
		public void dropItem(Target target, int count, ModConfig config) {
			itemDrops.add(count);
		}

		@Override
		public void placeBlock(Target target, ModConfig config) {
		}

		@Override
		public void runCommand(Target target, ModConfig config) {
		}

		@Override
		public int maxStackSize(Target item) {
			switch (item.id()) {
				case "minecraft:diamond_sword":
					return 1;
				case "minecraft:egg":
					return 16;
				default:
					return 64;
			}
		}
	}

	@Test
	void configFileIsWrittenWithTheNewDefaults() throws IOException {
		String json = new String(Files.readAllBytes(configDir.resolve("twitchspelling.json")), "UTF-8");
		assertTrue(json.contains("\"instantWords\""), json);
		assertTrue(json.contains("\"wither\": 600"), json);
	}
}
