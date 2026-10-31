package com.barrydamonke.twitchspelling.command;

import java.io.IOException;
import java.util.function.Predicate;

import com.barrydamonke.twitchspelling.TwitchSpellingMod;
import com.barrydamonke.twitchspelling.spell.ChatMessage;
import com.barrydamonke.twitchspelling.spell.WordDictionary;
import com.barrydamonke.twitchspelling.twitch.TwitchChatClient;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

/** {@code /twitchspelling ...} */
public final class TwitchSpellingCommand {
	private TwitchSpellingCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		Predicate<CommandSourceStack> isOp = Commands.hasPermission(Commands.LEVEL_GAMEMASTERS);
		LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("twitchspelling")
				// The owner of a singleplayer world can always use it, even with cheats off.
				.requires(source -> isOp.test(source) || isWorldOwner(source))
				.executes(TwitchSpellingCommand::help)
				// Start reading a channel's chat. Remembered for next time.
				.then(Commands.literal("connect")
						.then(Commands.argument("channel", StringArgumentType.greedyString())
								.executes(TwitchSpellingCommand::connect)))
				.then(Commands.literal("disconnect")
						.executes(TwitchSpellingCommand::disconnect))
				.then(Commands.literal("status")
						.executes(TwitchSpellingCommand::status))
				// Pretend a Twitch user typed a message, for testing without a stream.
				.then(Commands.literal("sim")
						.then(Commands.argument("user", StringArgumentType.word())
								.then(Commands.argument("message", StringArgumentType.greedyString())
										.executes(TwitchSpellingCommand::sim))))
				// Show what a word would summon, if anything.
				.then(Commands.literal("lookup")
						.then(Commands.argument("word", StringArgumentType.word())
								.executes(TwitchSpellingCommand::lookup)))
				// Re-read config/twitchspelling.json after editing it.
				.then(Commands.literal("reload")
						.executes(TwitchSpellingCommand::reload));
		SettingsCommands.register(root);
		dispatcher.register(root);
	}

	private static final String[][] HELP = {
			{"connect <channel>", "start reading your Twitch chat (remembered)"},
			{"disconnect", "stop reading Twitch chat"},
			{"status", "is it connected?"},
			{"settings", "show all settings"},
			{"set mirrorChat|spawnRadius|amount|targetPlayer|longerWordWaitSeconds <value>", "change a setting"},
			{"blocklist list|add|remove", "words chat can't spell"},
			{"alias list|add|remove", "extra words, e.g. DOG = WOLF"},
			{"special list|add|remove", "words that run a command"},
			{"repeat list|set|remove", "words that must be spelled several times"},
			{"lookup <word>", "what would a word summon?"},
			{"sim <user> <message>", "pretend someone typed in Twitch chat"},
			{"reload", "re-read the settings file after editing it"},
	};

	private static boolean isWorldOwner(CommandSourceStack source) {
		ServerPlayer player = source.getPlayer();
		return player != null && source.getServer().isSingleplayerOwner(player.nameAndId());
	}

	private static int help(CommandContext<CommandSourceStack> context) {
		MutableComponent text = Component.literal("Twitch Spelling commands:").withStyle(ChatFormatting.GOLD);
		for (String[] entry : HELP) {
			text.append(Component.literal("\n/twitchspelling " + entry[0]).withStyle(ChatFormatting.YELLOW))
					.append(Component.literal(" - " + entry[1]).withStyle(ChatFormatting.GRAY));
		}
		context.getSource().sendSuccess(() -> text, false);
		return 1;
	}

	private static int connect(CommandContext<CommandSourceStack> context) {
		String channel = TwitchChatClient.normalizeChannel(StringArgumentType.getString(context, "channel"));
		if (!channel.matches("[a-z0-9_]{2,25}")) {
			context.getSource().sendFailure(Component.literal("\"" + channel + "\" doesn't look like a Twitch channel name"));
			return 0;
		}
		try {
			TwitchSpellingMod.connect(channel);
		} catch (IOException e) {
			context.getSource().sendFailure(Component.literal("Connecting, but couldn't save the channel for next time: " + e.getMessage()));
			return 1;
		}
		context.getSource().sendSuccess(() -> Component.literal("Connecting to #" + channel
				+ "... This will happen automatically from now on."), false);
		return 1;
	}

	private static int disconnect(CommandContext<CommandSourceStack> context) {
		try {
			TwitchSpellingMod.disconnect();
		} catch (IOException e) {
			context.getSource().sendFailure(Component.literal("Disconnected, but couldn't save that: " + e.getMessage()));
			return 1;
		}
		context.getSource().sendSuccess(() -> Component.literal(
				"Disconnected from Twitch chat. Use /twitchspelling connect to start again."), false);
		return 1;
	}

	private static int status(CommandContext<CommandSourceStack> context) {
		context.getSource().sendSuccess(TwitchSpellingMod::status, false);
		return 1;
	}

	private static int sim(CommandContext<CommandSourceStack> context) {
		TwitchSpellingMod.INCOMING.add(new ChatMessage(
				StringArgumentType.getString(context, "user"),
				null,
				StringArgumentType.getString(context, "message"),
				true));
		return 1;
	}

	private static int lookup(CommandContext<CommandSourceStack> context) {
		String word = WordDictionary.toWord(StringArgumentType.getString(context, "word"));
		WordDictionary.Target target = TwitchSpellingMod.dictionary().lookup(word).orElse(null);
		if (target == null) {
			context.getSource().sendFailure(Component.literal(word + " isn't a spellable word"));
			return 0;
		}
		context.getSource().sendSuccess(() -> Component.literal(word + " -> " + target.describe()), false);
		return 1;
	}

	private static int reload(CommandContext<CommandSourceStack> context) {
		String problem = TwitchSpellingMod.reload();
		if (problem != null) {
			context.getSource().sendFailure(Component.literal(problem + " (keeping the old settings)"));
			return 0;
		}
		context.getSource().sendSuccess(() -> Component.literal("Twitch Spelling settings reloaded"), false);
		return 1;
	}
}
