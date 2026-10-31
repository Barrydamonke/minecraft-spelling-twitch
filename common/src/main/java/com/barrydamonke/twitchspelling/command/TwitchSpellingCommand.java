package com.barrydamonke.twitchspelling.command;

import java.io.IOException;
import java.util.Collection;
import java.util.Locale;
import java.util.function.Function;

import com.barrydamonke.twitchspelling.core.SpellingGame;
import com.barrydamonke.twitchspelling.core.Text;
import com.barrydamonke.twitchspelling.spell.Target;
import com.barrydamonke.twitchspelling.spell.WordDictionary;
import com.barrydamonke.twitchspelling.twitch.TwitchChatClient;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import com.mojang.brigadier.arguments.ArgumentType;

/**
 * {@code /twitchspelling ...}, built with plain Brigadier so every Minecraft version can share it.
 * {@code S} is that version's command source type.
 */
public final class TwitchSpellingCommand<S> {
	private static final String[][] HELP = {
			{"connect <channel>", "start reading your Twitch chat (remembered)"},
			{"disconnect", "stop reading Twitch chat"},
			{"status", "is it connected?"},
			{"settings", "show all settings"},
			{"set mirrorChat|spawnRadius|amount|targetPlayer|longerWordWaitSeconds <value>", "change a setting"},
			{"blocklist list|add|remove", "words chat can't spell"},
			{"alias list|add|remove", "extra words, e.g. DOG = WOLF"},
			{"special list|add|remove", "words that run a command"},
			{"cooldown list|set|remove", "words that can only be summoned every so often"},
			{"instant list|add|remove", "words that summon without waiting for a longer word"},
			{"repeat list|set|remove", "words that must be spelled several times"},
			{"lookup <word>", "what would a word summon?"},
			{"sim <user> <message>", "pretend someone typed in Twitch chat"},
			{"reload", "re-read the settings file after editing it"},
	};

	final SpellingGame game;
	final CommandHooks<S> hooks;

	private TwitchSpellingCommand(SpellingGame game, CommandHooks<S> hooks) {
		this.game = game;
		this.hooks = hooks;
	}

	/** The whole command tree, ready for {@code dispatcher.register(...)}. */
	public static <S> LiteralArgumentBuilder<S> build(SpellingGame game, CommandHooks<S> hooks) {
		TwitchSpellingCommand<S> command = new TwitchSpellingCommand<>(game, hooks);
		LiteralArgumentBuilder<S> root = command.literal("twitchspelling")
				.requires(hooks::canUse)
				.executes(command::help)
				// Start reading a channel's chat. Remembered for next time.
				.then(command.literal("connect")
						.then(command.argument("channel", StringArgumentType.greedyString())
								.executes(command::connect)))
				.then(command.literal("disconnect")
						.executes(command::disconnect))
				.then(command.literal("status")
						.executes(c -> command.reply(c, game.status())))
				// Pretend a Twitch user typed a message, for testing without a stream.
				.then(command.literal("sim")
						.then(command.argument("user", StringArgumentType.word())
								.then(command.argument("message", StringArgumentType.greedyString())
										.executes(command::sim))))
				// Show what a word would summon, if anything.
				.then(command.literal("lookup")
						.then(command.argument("word", StringArgumentType.word())
								.executes(command::lookup)))
				// Re-read config/twitchspelling.json after editing it.
				.then(command.literal("reload")
						.executes(command::reload));
		new SettingsCommands<>(command).register(root);
		return root;
	}

	// ----- Small helpers shared with SettingsCommands -----

	LiteralArgumentBuilder<S> literal(String name) {
		return LiteralArgumentBuilder.literal(name);
	}

	<T> RequiredArgumentBuilder<S, T> argument(String name, ArgumentType<T> type) {
		return RequiredArgumentBuilder.argument(name, type);
	}

	int reply(CommandContext<S> context, Text text) {
		hooks.reply(context.getSource(), text);
		return 1;
	}

	int ok(CommandContext<S> context, String message) {
		return reply(context, Text.of(message, Text.GREEN));
	}

	int fail(CommandContext<S> context, String message) {
		hooks.fail(context.getSource(), Text.of(message, Text.RED));
		return 0;
	}

	SuggestionProvider<S> suggest(Function<S, Collection<String>> options) {
		return (context, builder) -> {
			String typed = builder.getRemaining().toLowerCase(Locale.ROOT);
			for (String option : options.apply(context.getSource())) {
				if (option.toLowerCase(Locale.ROOT).startsWith(typed)) {
					builder.suggest(option);
				}
			}
			return builder.buildFuture();
		};
	}

	// ----- Commands -----

	private int help(CommandContext<S> context) {
		Text text = Text.of("Twitch Spelling commands:", Text.GOLD);
		for (String[] entry : HELP) {
			text.then("\n/twitchspelling " + entry[0], Text.YELLOW).then(" - " + entry[1], Text.GRAY);
		}
		return reply(context, text);
	}

	private int connect(CommandContext<S> context) {
		String channel = TwitchChatClient.normalizeChannel(StringArgumentType.getString(context, "channel"));
		if (!channel.matches("[a-z0-9_]{2,25}")) {
			return fail(context, "\"" + channel + "\" doesn't look like a Twitch channel name");
		}
		try {
			game.connect(channel);
		} catch (IOException e) {
			fail(context, "Connecting, but couldn't save the channel for next time: " + e.getMessage());
			return 1;
		}
		return reply(context, Text.of("Connecting to #" + channel + "... This will happen automatically from now on.", Text.WHITE));
	}

	private int disconnect(CommandContext<S> context) {
		try {
			game.disconnect();
		} catch (IOException e) {
			fail(context, "Disconnected, but couldn't save that: " + e.getMessage());
			return 1;
		}
		return reply(context, Text.of("Disconnected from Twitch chat. Use /twitchspelling connect to start again.", Text.WHITE));
	}

	private int sim(CommandContext<S> context) {
		game.simulate(StringArgumentType.getString(context, "user"), StringArgumentType.getString(context, "message"));
		return 1;
	}

	private int lookup(CommandContext<S> context) {
		String word = WordDictionary.toWord(StringArgumentType.getString(context, "word"));
		Target target = game.dictionary().lookup(word);
		if (target == null) {
			return fail(context, word + " isn't a spellable word");
		}
		return reply(context, Text.of(word + " -> " + target.describe(), Text.WHITE));
	}

	private int reload(CommandContext<S> context) {
		String problem = game.reload();
		if (problem != null) {
			return fail(context, problem + " (keeping the old settings)");
		}
		return ok(context, "Twitch Spelling settings reloaded");
	}
}
