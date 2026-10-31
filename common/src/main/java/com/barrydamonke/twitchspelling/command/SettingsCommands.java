package com.barrydamonke.twitchspelling.command;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import com.barrydamonke.twitchspelling.config.ModConfig;
import com.barrydamonke.twitchspelling.core.Text;
import com.barrydamonke.twitchspelling.core.TimeFormat;
import com.barrydamonke.twitchspelling.spell.WordDictionary;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

/**
 * Commands for changing every setting in {@code config/twitchspelling.json} from inside the game.
 * Changes are saved and take effect straight away.
 */
final class SettingsCommands<S> {
	private final TwitchSpellingCommand<S> command;

	SettingsCommands(TwitchSpellingCommand<S> command) {
		this.command = command;
	}

	private ModConfig config() {
		return command.game.config();
	}

	void register(LiteralArgumentBuilder<S> root) {
		root.then(command.literal("settings")
				.executes(this::show));

		root.then(command.literal("set")
				.then(command.literal("mirrorChat")
						.then(command.argument("on", BoolArgumentType.bool())
								.executes(c -> change(c, config -> {
									config.mirrorChat = BoolArgumentType.getBool(c, "on");
									return "Copying Twitch chat into Minecraft: " + (config.mirrorChat ? "on" : "off");
								}))))
				.then(command.literal("spawnRadius")
						.then(command.argument("blocks", IntegerArgumentType.integer(2, 32))
								.executes(c -> change(c, config -> {
									config.spawnRadius = IntegerArgumentType.getInteger(c, "blocks");
									return "Things now appear up to " + config.spawnRadius + " blocks away";
								}))))
				.then(command.literal("amount")
						.then(command.argument("count", IntegerArgumentType.integer(1, 64))
								.executes(c -> change(c, config -> {
									config.amount = IntegerArgumentType.getInteger(c, "count");
									return "Each spelled word now summons " + config.amount + " (items are a random 1-16)";
								}))))
				.then(command.literal("longerWordWaitSeconds")
						.then(command.argument("seconds", IntegerArgumentType.integer(0, 600))
								.executes(c -> change(c, config -> {
									config.longerWordWaitSeconds = IntegerArgumentType.getInteger(c, "seconds");
									return config.longerWordWaitSeconds == 0
											? "Words like PIG now wait for the next message before summoning"
											: "Words like PIG now summon after " + config.longerWordWaitSeconds
													+ " seconds if nobody continues them into a longer word";
								}))))
				.then(command.literal("targetPlayer")
						.then(command.literal("anyone")
								.executes(c -> change(c, config -> {
									config.targetPlayer = "";
									return "Things now appear around the first player online";
								})))
						.then(command.argument("player", StringArgumentType.word())
								.suggests(command.suggest(command.hooks::playerNames))
								.executes(c -> change(c, config -> {
									config.targetPlayer = StringArgumentType.getString(c, "player");
									return "Things now appear around " + config.targetPlayer;
								})))));

		root.then(command.literal("blocklist")
				.then(command.literal("list")
						.executes(c -> list(c, "Blocked words", config().blocklist, w -> WordDictionary.toWord(w))))
				.then(command.literal("add")
						.then(command.argument("word", StringArgumentType.word())
								.executes(c -> {
									String word = StringArgumentType.getString(c, "word");
									if (containsWord(config().blocklist, word)) {
										return command.fail(c, WordDictionary.toWord(word) + " is already blocked");
									}
									if (!isKnownWord(word)) {
										return command.fail(c, WordDictionary.toWord(word) + " isn't a spellable word");
									}
									return change(c, config -> {
										config.blocklist.add(word.toLowerCase(Locale.ROOT));
										return WordDictionary.toWord(word) + " is now blocked";
									});
								})))
				.then(command.literal("remove")
						.then(command.argument("word", StringArgumentType.word())
								.suggests(command.suggest(s -> config().blocklist))
								.executes(c -> removeFromList(c, config -> config.blocklist, "blocked", "can be spelled again")))));

		root.then(command.literal("instant")
				.then(command.literal("list")
						.executes(c -> list(c, "Words that summon without waiting for a longer word", config().instantWords,
								w -> WordDictionary.toWord(w))))
				.then(command.literal("add")
						.then(command.argument("word", StringArgumentType.word())
								.executes(c -> {
									String word = StringArgumentType.getString(c, "word");
									if (containsWord(config().instantWords, word)) {
										return command.fail(c, WordDictionary.toWord(word) + " is already instant");
									}
									if (!isKnownWord(word)) {
										return command.fail(c, WordDictionary.toWord(word) + " isn't a spellable word");
									}
									return change(c, config -> {
										config.instantWords.add(word.toLowerCase(Locale.ROOT));
										return WordDictionary.toWord(word) + " now summons the moment it's spelled";
									});
								})))
				.then(command.literal("remove")
						.then(command.argument("word", StringArgumentType.word())
								.suggests(command.suggest(s -> config().instantWords))
								.executes(c -> removeFromList(c, config -> config.instantWords, "instant",
										"waits for longer words again")))));

		root.then(command.literal("alias")
				.then(command.literal("list")
						.executes(c -> list(c, "Aliases", config().aliases.entrySet(),
								e -> WordDictionary.toWord(e.getKey()) + " summons the same as " + WordDictionary.toWord(e.getValue()))))
				.then(command.literal("add")
						.then(command.argument("alias", StringArgumentType.word())
								.then(command.argument("word", StringArgumentType.word())
										.executes(c -> {
											String alias = StringArgumentType.getString(c, "alias");
											String word = StringArgumentType.getString(c, "word");
											if (!WordDictionary.isSpellable(WordDictionary.toWord(alias))) {
												return command.fail(c, "Aliases can only use the letters A-Z");
											}
											if (!isKnownWord(word)) {
												return command.fail(c, WordDictionary.toWord(word) + " isn't a spellable word");
											}
											return change(c, config -> {
												removeWord(config.aliases.keySet(), alias);
												config.aliases.put(alias.toLowerCase(Locale.ROOT), word.toLowerCase(Locale.ROOT));
												return "Spelling " + WordDictionary.toWord(alias) + " now summons the same as " + WordDictionary.toWord(word);
											});
										}))))
				.then(command.literal("remove")
						.then(command.argument("alias", StringArgumentType.word())
								.suggests(command.suggest(s -> config().aliases.keySet()))
								.executes(c -> removeFromMap(c, "alias", config -> config.aliases, "an alias")))));

		root.then(command.literal("special")
				.then(command.literal("list")
						.executes(c -> list(c, "Special words", config().specials.entrySet(),
								e -> WordDictionary.toWord(e.getKey()) + " runs /" + e.getValue())))
				.then(command.literal("add")
						.then(command.argument("word", StringArgumentType.word())
								.then(command.argument("command", StringArgumentType.greedyString())
										.executes(c -> {
											String word = StringArgumentType.getString(c, "word");
											String text = StringArgumentType.getString(c, "command");
											String commandText = text.startsWith("/") ? text.substring(1) : text;
											if (!WordDictionary.isSpellable(WordDictionary.toWord(word))) {
												return command.fail(c, "Special words can only use the letters A-Z");
											}
											return change(c, config -> {
												removeWord(config.specials.keySet(), word);
												config.specials.put(word.toLowerCase(Locale.ROOT), commandText);
												return "Spelling " + WordDictionary.toWord(word) + " now runs /" + commandText;
											});
										}))))
				.then(command.literal("remove")
						.then(command.argument("word", StringArgumentType.word())
								.suggests(command.suggest(s -> config().specials.keySet()))
								.executes(c -> removeFromMap(c, "word", config -> config.specials, "a special word")))));

		root.then(command.literal("cooldown")
				.then(command.literal("list")
						.executes(c -> list(c, "Words with a cooldown", config().cooldownSeconds.entrySet(),
								e -> WordDictionary.toWord(e.getKey()) + " once every " + TimeFormat.countdown(e.getValue() * 20))))
				.then(command.literal("set")
						.then(command.argument("word", StringArgumentType.word())
								.then(command.argument("seconds", IntegerArgumentType.integer(0, 86400))
										.executes(c -> {
											String word = StringArgumentType.getString(c, "word");
											int seconds = IntegerArgumentType.getInteger(c, "seconds");
											if (!isKnownWord(word)) {
												return command.fail(c, WordDictionary.toWord(word) + " isn't a spellable word");
											}
											return change(c, config -> {
												removeWord(config.cooldownSeconds.keySet(), word);
												if (seconds == 0) {
													return WordDictionary.toWord(word) + " no longer has a cooldown";
												}
												config.cooldownSeconds.put(word.toLowerCase(Locale.ROOT), seconds);
												return WordDictionary.toWord(word) + " can now be summoned once every " + TimeFormat.countdown(seconds * 20);
											});
										}))))
				.then(command.literal("remove")
						.then(command.argument("word", StringArgumentType.word())
								.suggests(command.suggest(s -> config().cooldownSeconds.keySet()))
								.executes(c -> removeFromMap(c, "word", config -> config.cooldownSeconds, "on cooldown")))));

		root.then(command.literal("repeat")
				.then(command.literal("list")
						.executes(c -> list(c, "Words that need spelling more than once", config().repeatToSummon.entrySet(),
								e -> WordDictionary.toWord(e.getKey()) + " x" + e.getValue())))
				.then(command.literal("set")
						.then(command.argument("word", StringArgumentType.word())
								.then(command.argument("times", IntegerArgumentType.integer(1, 20))
										.executes(c -> {
											String word = StringArgumentType.getString(c, "word");
											int times = IntegerArgumentType.getInteger(c, "times");
											if (!isKnownWord(word)) {
												return command.fail(c, WordDictionary.toWord(word) + " isn't a spellable word");
											}
											return change(c, config -> {
												removeWord(config.repeatToSummon.keySet(), word);
												if (times == 1) {
													return WordDictionary.toWord(word) + " now summons the first time it's spelled";
												}
												config.repeatToSummon.put(word.toLowerCase(Locale.ROOT), times);
												return WordDictionary.toWord(word) + " now has to be spelled " + times + " times in a row";
											});
										}))))
				.then(command.literal("remove")
						.then(command.argument("word", StringArgumentType.word())
								.suggests(command.suggest(s -> config().repeatToSummon.keySet()))
								.executes(c -> removeFromMap(c, "word", config -> config.repeatToSummon, "a repeat word")))));
	}

	/** Applies {@code edit} (which returns a message describing the change), saves, and tells the player. */
	private int change(CommandContext<S> context, Function<ModConfig, String> edit) {
		String message = edit.apply(config());
		try {
			command.game.saveConfig();
		} catch (IOException e) {
			return command.fail(context, message + ", but it couldn't be saved for next time: " + e.getMessage());
		}
		return command.ok(context, message);
	}

	private int removeFromList(CommandContext<S> context, Function<ModConfig, List<String>> list, String what, String done) {
		String word = StringArgumentType.getString(context, "word");
		if (!containsWord(list.apply(config()), word)) {
			return command.fail(context, WordDictionary.toWord(word) + " isn't " + what);
		}
		return change(context, config -> {
			removeWord(list.apply(config), word);
			return WordDictionary.toWord(word) + " " + done;
		});
	}

	private int removeFromMap(CommandContext<S> context, String argument, Function<ModConfig, Map<String, ?>> map, String what) {
		String word = StringArgumentType.getString(context, argument);
		if (!containsWord(map.apply(config()).keySet(), word)) {
			return command.fail(context, WordDictionary.toWord(word) + " isn't " + what);
		}
		return change(context, config -> {
			removeWord(map.apply(config).keySet(), word);
			return WordDictionary.toWord(word) + " is no longer " + what;
		});
	}

	private <T> int list(CommandContext<S> context, String title, Collection<T> items, Function<T, String> describe) {
		Text text = Text.of(title + " (" + items.size() + "):", Text.GOLD);
		for (T item : new ArrayList<>(items)) {
			text.then("\n  " + describe.apply(item), Text.WHITE);
		}
		return command.reply(context, text);
	}

	private int show(CommandContext<S> context) {
		ModConfig config = config();
		Text text = Text.of("Twitch Spelling settings:", Text.GOLD);
		line(text, "Channel", config.channel.isEmpty() ? "(none, use /twitchspelling connect)" : "#" + config.channel);
		line(text, "Copy Twitch chat into Minecraft", config.mirrorChat ? "on" : "off");
		line(text, "Summon around", config.targetPlayer.isEmpty() ? "first player online" : config.targetPlayer);
		line(text, "Spawn radius", config.spawnRadius + " blocks");
		line(text, "Mobs/blocks per word", config.amount + " (items are a random 1-16)");
		line(text, "Wait for longer word (PIG/PIGLIN)", config.longerWordWaitSeconds == 0
				? "until the next message"
				: config.longerWordWaitSeconds + " seconds");
		line(text, "Blocked words", config.blocklist.size() + "  (/twitchspelling blocklist list)");
		line(text, "Instant words", config.instantWords.size() + "  (/twitchspelling instant list)");
		line(text, "Cooldowns", config.cooldownSeconds.size() + "  (/twitchspelling cooldown list)");
		line(text, "Aliases", config.aliases.size() + "  (/twitchspelling alias list)");
		line(text, "Special words", config.specials.size() + "  (/twitchspelling special list)");
		line(text, "Repeat words", config.repeatToSummon.size() + "  (/twitchspelling repeat list)");
		return command.reply(context, text);
	}

	private static void line(Text text, String name, String value) {
		text.then("\n  " + name + ": ", Text.GRAY).then(value, Text.WHITE);
	}

	private boolean isKnownWord(String word) {
		return command.game.dictionary().lookup(WordDictionary.toWord(word)) != null;
	}

	private static boolean containsWord(Collection<String> words, String word) {
		String target = WordDictionary.toWord(word);
		for (String w : words) {
			if (WordDictionary.toWord(w).equals(target)) {
				return true;
			}
		}
		return false;
	}

	/** Removes every entry that spells the same word, however it was written. */
	private static void removeWord(Collection<String> words, String word) {
		String target = WordDictionary.toWord(word);
		for (Iterator<String> it = words.iterator(); it.hasNext(); ) {
			if (WordDictionary.toWord(it.next()).equals(target)) {
				it.remove();
			}
		}
	}
}
