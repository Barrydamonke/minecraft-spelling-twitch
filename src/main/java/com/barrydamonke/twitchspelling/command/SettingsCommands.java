package com.barrydamonke.twitchspelling.command;

import java.io.IOException;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import com.barrydamonke.twitchspelling.TwitchSpellingMod;
import com.barrydamonke.twitchspelling.config.ModConfig;
import com.barrydamonke.twitchspelling.spell.WordDictionary;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Commands for changing every setting in {@code config/twitchspelling.json} from inside the game.
 * Changes are saved and take effect straight away.
 */
final class SettingsCommands {
	private SettingsCommands() {
	}

	static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
		root.then(Commands.literal("settings")
				.executes(SettingsCommands::show));

		root.then(Commands.literal("set")
				.then(Commands.literal("mirrorChat")
						.then(Commands.argument("on", BoolArgumentType.bool())
								.executes(c -> change(c, config -> {
									config.mirrorChat = BoolArgumentType.getBool(c, "on");
									return "Copying Twitch chat into Minecraft: " + (config.mirrorChat ? "on" : "off");
								}))))
				.then(Commands.literal("spawnRadius")
						.then(Commands.argument("blocks", IntegerArgumentType.integer(2, 32))
								.executes(c -> change(c, config -> {
									config.spawnRadius = IntegerArgumentType.getInteger(c, "blocks");
									return "Things now appear up to " + config.spawnRadius + " blocks away";
								}))))
				.then(Commands.literal("amount")
						.then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
								.executes(c -> change(c, config -> {
									config.amount = IntegerArgumentType.getInteger(c, "count");
									return "Each spelled word now summons " + config.amount + " (items are always 1)";
								}))))
				.then(Commands.literal("longerWordWaitSeconds")
						.then(Commands.argument("seconds", IntegerArgumentType.integer(0, 600))
								.executes(c -> change(c, config -> {
									config.longerWordWaitSeconds = IntegerArgumentType.getInteger(c, "seconds");
									return config.longerWordWaitSeconds == 0
											? "Words like PIG now wait for the next message before summoning"
											: "Words like PIG now summon after " + config.longerWordWaitSeconds
													+ " seconds if nobody continues them into a longer word";
								}))))
				.then(Commands.literal("targetPlayer")
						.then(Commands.literal("anyone")
								.executes(c -> change(c, config -> {
									config.targetPlayer = "";
									return "Things now appear around the first player online";
								})))
						.then(Commands.argument("player", StringArgumentType.word())
								.suggests((c, builder) -> SharedSuggestionProvider.suggest(c.getSource().getServer().getPlayerNames(), builder))
								.executes(c -> change(c, config -> {
									config.targetPlayer = StringArgumentType.getString(c, "player");
									return "Things now appear around " + config.targetPlayer;
								})))));

		root.then(Commands.literal("blocklist")
				.then(Commands.literal("list")
						.executes(c -> list(c, "Blocked words", TwitchSpellingMod.config().blocklist, Function.identity())))
				.then(Commands.literal("add")
						.then(Commands.argument("word", StringArgumentType.word())
								.executes(c -> {
									String word = StringArgumentType.getString(c, "word");
									if (containsWord(TwitchSpellingMod.config().blocklist, word)) {
										return fail(c, WordDictionary.toWord(word) + " is already blocked");
									}
									if (TwitchSpellingMod.dictionary().lookup(WordDictionary.toWord(word)).isEmpty()) {
										return fail(c, WordDictionary.toWord(word) + " isn't a spellable word");
									}
									return change(c, config -> {
										config.blocklist.add(word.toLowerCase(Locale.ROOT));
										return WordDictionary.toWord(word) + " is now blocked";
									});
								})))
				.then(Commands.literal("remove")
						.then(Commands.argument("word", StringArgumentType.word())
								.suggests(suggest(config -> config.blocklist))
								.executes(c -> {
									String word = StringArgumentType.getString(c, "word");
									if (!containsWord(TwitchSpellingMod.config().blocklist, word)) {
										return fail(c, WordDictionary.toWord(word) + " isn't blocked");
									}
									return change(c, config -> {
										removeWord(config.blocklist, word);
										return WordDictionary.toWord(word) + " can be spelled again";
									});
								}))));

		root.then(Commands.literal("alias")
				.then(Commands.literal("list")
						.executes(c -> list(c, "Aliases", TwitchSpellingMod.config().aliases.entrySet(),
								e -> WordDictionary.toWord(e.getKey()) + " summons the same as " + WordDictionary.toWord(e.getValue()))))
				.then(Commands.literal("add")
						.then(Commands.argument("alias", StringArgumentType.word())
								.then(Commands.argument("word", StringArgumentType.word())
										.executes(c -> {
											String alias = StringArgumentType.getString(c, "alias");
											String word = StringArgumentType.getString(c, "word");
											if (!isSpellable(alias)) {
												return fail(c, "Aliases can only use the letters A-Z");
											}
											if (TwitchSpellingMod.dictionary().lookup(WordDictionary.toWord(word)).isEmpty()) {
												return fail(c, WordDictionary.toWord(word) + " isn't a spellable word");
											}
											return change(c, config -> {
												removeWord(config.aliases.keySet(), alias);
												config.aliases.put(alias.toLowerCase(Locale.ROOT), word.toLowerCase(Locale.ROOT));
												return "Spelling " + WordDictionary.toWord(alias) + " now summons the same as " + WordDictionary.toWord(word);
											});
										}))))
				.then(Commands.literal("remove")
						.then(Commands.argument("alias", StringArgumentType.word())
								.suggests(suggest(config -> config.aliases.keySet()))
								.executes(c -> removeFromMap(c, "alias", config -> config.aliases, "an alias")))));

		root.then(Commands.literal("special")
				.then(Commands.literal("list")
						.executes(c -> list(c, "Special words", TwitchSpellingMod.config().specials.entrySet(),
								e -> WordDictionary.toWord(e.getKey()) + " runs /" + e.getValue())))
				.then(Commands.literal("add")
						.then(Commands.argument("word", StringArgumentType.word())
								.then(Commands.argument("command", StringArgumentType.greedyString())
										.executes(c -> {
											String word = StringArgumentType.getString(c, "word");
											String command = StringArgumentType.getString(c, "command");
											if (!isSpellable(word)) {
												return fail(c, "Special words can only use the letters A-Z");
											}
											return change(c, config -> {
												removeWord(config.specials.keySet(), word);
												config.specials.put(word.toLowerCase(Locale.ROOT), command.startsWith("/") ? command.substring(1) : command);
												return "Spelling " + WordDictionary.toWord(word) + " now runs /" + config.specials.get(word.toLowerCase(Locale.ROOT));
											});
										}))))
				.then(Commands.literal("remove")
						.then(Commands.argument("word", StringArgumentType.word())
								.suggests(suggest(config -> config.specials.keySet()))
								.executes(c -> removeFromMap(c, "word", config -> config.specials, "a special word")))));

		root.then(Commands.literal("repeat")
				.then(Commands.literal("list")
						.executes(c -> list(c, "Words that need spelling more than once", TwitchSpellingMod.config().repeatToSummon.entrySet(),
								e -> WordDictionary.toWord(e.getKey()) + " x" + e.getValue())))
				.then(Commands.literal("set")
						.then(Commands.argument("word", StringArgumentType.word())
								.then(Commands.argument("times", IntegerArgumentType.integer(1, 20))
										.executes(c -> {
											String word = StringArgumentType.getString(c, "word");
											int times = IntegerArgumentType.getInteger(c, "times");
											if (TwitchSpellingMod.dictionary().lookup(WordDictionary.toWord(word)).isEmpty()) {
												return fail(c, WordDictionary.toWord(word) + " isn't a spellable word");
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
				.then(Commands.literal("remove")
						.then(Commands.argument("word", StringArgumentType.word())
								.suggests(suggest(config -> config.repeatToSummon.keySet()))
								.executes(c -> removeFromMap(c, "word", config -> config.repeatToSummon, "a repeat word")))));
	}

	/** Applies {@code edit} (which returns a message describing the change), saves, and tells the player. */
	private static int change(CommandContext<CommandSourceStack> context, Function<ModConfig, String> edit) {
		String message = edit.apply(TwitchSpellingMod.config());
		try {
			TwitchSpellingMod.saveConfig();
		} catch (IOException e) {
			return fail(context, message + ", but it couldn't be saved for next time: " + e.getMessage());
		}
		context.getSource().sendSuccess(() -> Component.literal(message).withStyle(ChatFormatting.GREEN), false);
		return 1;
	}

	private static int removeFromMap(CommandContext<CommandSourceStack> context, String argument,
			Function<ModConfig, Map<String, ?>> map, String what) {
		String word = StringArgumentType.getString(context, argument);
		if (!containsWord(map.apply(TwitchSpellingMod.config()).keySet(), word)) {
			return fail(context, WordDictionary.toWord(word) + " isn't " + what);
		}
		return change(context, config -> {
			removeWord(map.apply(config).keySet(), word);
			return WordDictionary.toWord(word) + " is no longer " + what;
		});
	}

	private static <T> int list(CommandContext<CommandSourceStack> context, String title, Collection<T> items, Function<T, String> describe) {
		MutableComponent text = Component.literal(title + " (" + items.size() + "):").withStyle(ChatFormatting.GOLD);
		for (T item : items) {
			text.append(Component.literal("\n  " + describe.apply(item)).withStyle(ChatFormatting.WHITE));
		}
		context.getSource().sendSuccess(() -> text, false);
		return 1;
	}

	private static int show(CommandContext<CommandSourceStack> context) {
		ModConfig config = TwitchSpellingMod.config();
		MutableComponent text = Component.literal("Twitch Spelling settings:").withStyle(ChatFormatting.GOLD);
		line(text, "Channel", config.channel.isBlank() ? "(none, use /twitchspelling connect)" : "#" + config.channel);
		line(text, "Copy Twitch chat into Minecraft", config.mirrorChat ? "on" : "off");
		line(text, "Summon around", config.targetPlayer.isBlank() ? "first player online" : config.targetPlayer);
		line(text, "Spawn radius", config.spawnRadius + " blocks");
		line(text, "Amount per word", config.amount + " (items are always 1)");
		line(text, "Wait for longer word (PIG/PIGLIN)", config.longerWordWaitSeconds == 0
				? "until the next message"
				: config.longerWordWaitSeconds + " seconds");
		line(text, "Blocked words", config.blocklist.size() + "  (/twitchspelling blocklist list)");
		line(text, "Aliases", config.aliases.size() + "  (/twitchspelling alias list)");
		line(text, "Special words", config.specials.size() + "  (/twitchspelling special list)");
		line(text, "Repeat words", config.repeatToSummon.size() + "  (/twitchspelling repeat list)");
		context.getSource().sendSuccess(() -> text, false);
		return 1;
	}

	private static void line(MutableComponent text, String name, String value) {
		text.append(Component.literal("\n  " + name + ": ").withStyle(ChatFormatting.GRAY))
				.append(Component.literal(value).withStyle(ChatFormatting.WHITE));
	}

	private static SuggestionProvider<CommandSourceStack> suggest(Function<ModConfig, Collection<String>> words) {
		return (context, builder) -> SharedSuggestionProvider.suggest(words.apply(TwitchSpellingMod.config()), builder);
	}

	private static boolean containsWord(Collection<String> words, String word) {
		String target = WordDictionary.toWord(word);
		return words.stream().anyMatch(w -> WordDictionary.toWord(w).equals(target));
	}

	/** Removes every entry that spells the same word, however it was written. */
	private static void removeWord(Collection<String> words, String word) {
		String target = WordDictionary.toWord(word);
		words.removeIf(w -> WordDictionary.toWord(w).equals(target));
	}

	private static boolean isSpellable(String word) {
		return WordDictionary.toWord(word).matches("[A-Z]+");
	}

	private static int fail(CommandContext<CommandSourceStack> context, String message) {
		context.getSource().sendFailure(Component.literal(message));
		return 0;
	}
}
