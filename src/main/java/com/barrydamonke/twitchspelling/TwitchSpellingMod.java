package com.barrydamonke.twitchspelling;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.barrydamonke.twitchspelling.chat.ChatMirror;
import com.barrydamonke.twitchspelling.command.TwitchSpellingCommand;
import com.barrydamonke.twitchspelling.config.ModConfig;
import com.barrydamonke.twitchspelling.spawn.Summoner;
import com.barrydamonke.twitchspelling.spell.ChatMessage;
import com.barrydamonke.twitchspelling.spell.RepeatTracker;
import com.barrydamonke.twitchspelling.spell.SpellMatcher;
import com.barrydamonke.twitchspelling.spell.WordDictionary;
import com.barrydamonke.twitchspelling.twitch.TwitchChatClient;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TwitchSpellingMod implements ModInitializer {
	public static final String MOD_ID = "twitchspelling";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/** Chat waiting to be handled on the server thread. Filled by Twitch and by /twitchspelling sim. */
	public static final Queue<ChatMessage> INCOMING = new ConcurrentLinkedQueue<>();
	/** Connection updates from the Twitch thread, shown in chat on the server thread. */
	private static final Queue<Component> STATUS = new ConcurrentLinkedQueue<>();

	private static final TwitchChatClient.Listener TWITCH_LISTENER = new TwitchChatClient.Listener() {
		@Override
		public void onMessage(ChatMessage message) {
			INCOMING.add(message);
		}

		@Override
		public void onStatus(boolean good, String text) {
			STATUS.add(ChatMirror.status(good, text));
		}
	};

	private static ModConfig config = new ModConfig();
	private static WordDictionary dictionary;
	private static SpellMatcher matcher;
	private static RepeatTracker repeatTracker;
	private static TwitchChatClient twitch;
	/** Server tick of the last chat message, for {@link ModConfig#longerWordWaitSeconds}. */
	private static int lastMessageTick;

	@Override
	public void onInitialize() {
		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) ->
				TwitchSpellingCommand.register(dispatcher));

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			String problem = reload();
			if (problem != null) {
				LOGGER.error("{} - using the default settings for now", problem);
			}
			if (!config.channel.isBlank()) {
				startTwitch(config.channel);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> stopTwitch());
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			dictionary = null;
			matcher = null;
			INCOMING.clear();
			STATUS.clear();
		});

		ServerTickEvents.END_SERVER_TICK.register(TwitchSpellingMod::tick);
	}

	/**
	 * Re-reads the config file and rebuilds the word list. Any letters chat had typed are dropped.
	 * Returns a problem to show the player, or null if it worked. On a problem the old settings stay.
	 */
	public static String reload() {
		String problem = null;
		try {
			config = ModConfig.load();
		} catch (IOException e) {
			problem = e.getMessage();
		}
		applyConfig();
		return problem;
	}

	/** Saves a change made by a command and puts it into effect. Any letters chat had typed are dropped. */
	public static void saveConfig() throws IOException {
		config.save();
		applyConfig();
	}

	public static ModConfig config() {
		return config;
	}

	private static void applyConfig() {
		dictionary = WordDictionary.build(config);
		matcher = new SpellMatcher(dictionary.wordList());
		Map<String, Integer> repeats = new HashMap<>();
		config.repeatToSummon.forEach((word, times) -> repeats.put(WordDictionary.toWord(word), times));
		repeatTracker = new RepeatTracker(repeats);
		LOGGER.info("Twitch Spelling ready: {} spellable words", dictionary.wordList().size());
	}

	/** Connects to a channel, and saves it so the mod reconnects next time a world loads. */
	public static void connect(String channel) throws IOException {
		config.channel = channel;
		config.save();
		startTwitch(channel);
	}

	/** Disconnects, and stops the mod reconnecting next time a world loads. */
	public static void disconnect() throws IOException {
		stopTwitch();
		config.channel = "";
		config.save();
	}

	/** e.g. "Connected to #name", for /twitchspelling status. */
	public static Component status() {
		if (twitch == null) {
			return ChatMirror.status(false, "Not connected to Twitch. Use /twitchspelling connect <your channel>");
		}
		return twitch.isConnected()
				? ChatMirror.status(true, "Connected to Twitch chat: #" + twitch.channel())
				: ChatMirror.status(false, "Trying to connect to Twitch chat: #" + twitch.channel());
	}

	private static void startTwitch(String channel) {
		stopTwitch();
		twitch = new TwitchChatClient(channel, TWITCH_LISTENER);
		twitch.start();
	}

	private static void stopTwitch() {
		if (twitch != null) {
			twitch.stop();
			twitch = null;
		}
	}

	private static void tick(MinecraftServer server) {
		Component status;
		while ((status = STATUS.poll()) != null) {
			broadcast(server, status);
		}
		if (matcher == null) {
			return;
		}
		ChatMessage message;
		while ((message = INCOMING.poll()) != null) {
			handle(server, message);
		}

		// Stop waiting for a longer word (PIG could have become PIGLIN) once chat has gone quiet.
		int waitTicks = config.longerWordWaitSeconds * 20;
		if (waitTicks > 0 && matcher.isWordWaiting() && server.getTickCount() - lastMessageTick >= waitTicks) {
			announce(server, repeatTracker.onMessage(matcher.stopWaiting(), matcher.letters()));
		}
	}

	private static void handle(MinecraftServer server, ChatMessage message) {
		lastMessageTick = server.getTickCount();
		String lettersBefore = matcher.letters();
		List<SpellMatcher.Spelled> spelledWords = matcher.accept(message.user(), message.text());
		if (config.mirrorChat || message.simulated()) {
			broadcast(server, ChatMirror.message(message));
		}
		if (!matcher.letters().isEmpty() && !matcher.letters().equals(lettersBefore)) {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				player.sendOverlayMessage(ChatMirror.progress(matcher.letters()));
			}
		}

		// Announce after the message that finished the word, so it reads in order.
		announce(server, repeatTracker.onMessage(spelledWords, matcher.letters()));
	}

	private static void announce(MinecraftServer server, List<RepeatTracker.Event> events) {
		for (RepeatTracker.Event event : events) {
			switch (event.type()) {
				case SUMMON -> summon(server, event);
				case PROGRESS -> {
					int left = event.needed() - event.count();
					broadcast(server, spelledLine(event)
							.append(Component.literal("  Spell it " + left + " more time" + (left == 1 ? "" : "s")
									+ " in a row to summon it!").withStyle(ChatFormatting.YELLOW)));
				}
				case BROKEN -> broadcast(server, Component.literal(event.word() + " streak broken! Back to 0 of "
						+ event.needed() + ".").withStyle(ChatFormatting.RED));
			}
		}
	}

	private static void summon(MinecraftServer server, RepeatTracker.Event event) {
		WordDictionary.Target target = dictionary.lookup(event.word()).orElseThrow();
		String problem = Summoner.summon(server, config, target);

		MutableComponent line = spelledLine(event);
		if (problem != null) {
			line.append(Component.literal("  but " + problem).withStyle(ChatFormatting.RED));
		}
		broadcast(server, line);
	}

	/** "✨ Chat spelled COW! (Ann, Bob, Cat)", or "✨ Chat spelled WITHER 2/3! (...)" for repeat words. */
	private static MutableComponent spelledLine(RepeatTracker.Event event) {
		String times = event.needed() > 1 ? " " + event.count() + "/" + event.needed() : "";
		return Component.literal("✨ Chat spelled " + event.word() + times + "! (" + String.join(", ", event.spelled().users()) + ")")
				.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
	}

	private static void broadcast(MinecraftServer server, Component message) {
		server.getPlayerList().broadcastSystemMessage(message, false);
	}

	public static WordDictionary dictionary() {
		return dictionary;
	}
}
