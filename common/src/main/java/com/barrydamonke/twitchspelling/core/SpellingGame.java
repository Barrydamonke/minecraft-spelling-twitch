package com.barrydamonke.twitchspelling.core;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.barrydamonke.twitchspelling.config.ModConfig;
import com.barrydamonke.twitchspelling.spell.ChatMessage;
import com.barrydamonke.twitchspelling.spell.RepeatTracker;
import com.barrydamonke.twitchspelling.spell.SpellMatcher;
import com.barrydamonke.twitchspelling.spell.Target;
import com.barrydamonke.twitchspelling.spell.WordDictionary;
import com.barrydamonke.twitchspelling.twitch.TwitchChatClient;

/**
 * The whole mod apart from the Minecraft-specific bits: reads chat, works out spelled words,
 * applies cooldowns and delays, summons things through the {@link Platform}, and announces it all.
 * Each Minecraft version creates one and calls {@link #serverStarted}, {@link #tick} and
 * {@link #serverStopping}.
 */
public final class SpellingGame {
	/** The most mob spawns can be spread out by, when more than one is summoned. */
	private static final int MAX_SPAWN_DELAY_TICKS = 40;
	/** Items come in a random amount up to this, or up to the stack size if that's smaller. */
	private static final int MAX_ITEMS = 16;

	private final Platform platform;
	private final Random random;
	/** Chat waiting to be handled on the server thread. Filled by Twitch and by /twitchspelling sim. */
	private final Queue<ChatMessage> incoming = new ConcurrentLinkedQueue<>();
	/** Connection updates from the Twitch thread, shown in chat on the server thread. */
	private final Queue<Text> statusUpdates = new ConcurrentLinkedQueue<>();
	private final TwitchChatClient.Listener twitchListener = new TwitchChatClient.Listener() {
		@Override
		public void onMessage(ChatMessage message) {
			incoming.add(message);
		}

		@Override
		public void onStatus(boolean good, String text) {
			statusUpdates.add(ChatMirror.status(good, text));
		}

		@Override
		public void onLog(String text) {
			platform.warn(text);
		}
	};

	private ModConfig config = new ModConfig();
	private Map<String, Target> registryWords = new HashMap<>();
	private WordDictionary dictionary;
	private SpellMatcher matcher;
	private RepeatTracker repeatTracker;
	private Map<String, Integer> cooldownTicks = new HashMap<>();
	/** Tick at which each word on cooldown can be summoned again. */
	private final Map<String, Integer> readyAt = new HashMap<>();
	/** Mobs waiting for their random delay to pass. */
	private final List<PendingSpawn> pendingSpawns = new ArrayList<>();
	private TwitchChatClient twitch;
	/** Server tick of the last chat message, for {@link ModConfig#longerWordWaitSeconds}. */
	private int lastMessageTick;

	private static final class PendingSpawn {
		final Target target;
		final int dueTick;

		PendingSpawn(Target target, int dueTick) {
			this.target = target;
			this.dueTick = dueTick;
		}
	}

	public SpellingGame(Platform platform) {
		this(platform, new Random());
	}

	/** {@code random} decides spawn delays and item amounts. Tests pass a seeded one. */
	public SpellingGame(Platform platform, Random random) {
		this.platform = platform;
		this.random = random;
		applyConfig();
	}

	// ----- World lifecycle -----

	public void serverStarted() {
		registryWords = platform.registryWords();
		String problem = reload();
		if (problem != null) {
			platform.warn(problem + " - using the default settings for now");
		}
		lastMessageTick = platform.tickCount();
		if (!config.channel.isEmpty()) {
			startTwitch(config.channel);
		}
	}

	public void serverStopping() {
		stopTwitch();
		incoming.clear();
		statusUpdates.clear();
		pendingSpawns.clear();
		readyAt.clear();
	}

	// ----- Settings -----

	private Path configPath() {
		return platform.configDir().resolve("twitchspelling.json");
	}

	/**
	 * Re-reads the config file and rebuilds the word list. Any letters chat had typed are dropped.
	 * Returns a problem to show the player, or null if it worked. On a problem the old settings stay.
	 */
	public String reload() {
		String problem = null;
		try {
			config = ModConfig.load(configPath());
		} catch (IOException e) {
			problem = e.getMessage();
		}
		applyConfig();
		return problem;
	}

	/** Saves a change made by a command and puts it into effect. Any letters chat had typed are dropped. */
	public void saveConfig() throws IOException {
		config.save(configPath());
		applyConfig();
	}

	public ModConfig config() {
		return config;
	}

	public WordDictionary dictionary() {
		return dictionary;
	}

	private void applyConfig() {
		dictionary = WordDictionary.build(registryWords, config);
		matcher = new SpellMatcher(dictionary.wordList());
		Map<String, Integer> repeats = new HashMap<>();
		for (Map.Entry<String, Integer> entry : config.repeatToSummon.entrySet()) {
			repeats.put(WordDictionary.toWord(entry.getKey()), entry.getValue());
		}
		repeatTracker = new RepeatTracker(repeats);
		cooldownTicks = new HashMap<>();
		for (Map.Entry<String, Integer> entry : config.cooldownSeconds.entrySet()) {
			cooldownTicks.put(WordDictionary.toWord(entry.getKey()), entry.getValue() * 20);
		}
	}

	// ----- Twitch -----

	/** Connects to a channel, and saves it so the mod reconnects next time a world loads. */
	public void connect(String channel) throws IOException {
		config.channel = channel;
		saveConfig();
		startTwitch(channel);
	}

	/** Disconnects, and stops the mod reconnecting next time a world loads. */
	public void disconnect() throws IOException {
		stopTwitch();
		config.channel = "";
		saveConfig();
	}

	/** e.g. "Connected to #name", for /twitchspelling status. */
	public Text status() {
		if (twitch == null) {
			return ChatMirror.status(false, "Not connected to Twitch. Use /twitchspelling connect <your channel>");
		}
		return twitch.isConnected()
				? ChatMirror.status(true, "Connected to Twitch chat: #" + twitch.channel())
				: ChatMirror.status(false, "Trying to connect to Twitch chat: #" + twitch.channel());
	}

	private void startTwitch(String channel) {
		stopTwitch();
		twitch = new TwitchChatClient(channel, twitchListener);
		twitch.start();
	}

	private void stopTwitch() {
		if (twitch != null) {
			twitch.stop();
			twitch = null;
		}
	}

	/** Pretends a Twitch user typed a message. */
	public void simulate(String user, String text) {
		incoming.add(new ChatMessage(user, null, text, true));
	}

	// ----- Every tick -----

	public void tick() {
		Text status;
		while ((status = statusUpdates.poll()) != null) {
			platform.broadcast(status);
		}

		ChatMessage message;
		while ((message = incoming.poll()) != null) {
			handle(message);
		}

		// Stop waiting for a longer word (PIG could have become PIGLIN) once chat has gone quiet.
		int waitTicks = config.longerWordWaitSeconds * 20;
		if (waitTicks > 0 && matcher.isWordWaiting() && platform.tickCount() - lastMessageTick >= waitTicks) {
			announce(repeatTracker.onMessage(matcher.stopWaiting(), matcher.letters()));
		}

		spawnDueMobs();
	}

	private void handle(ChatMessage message) {
		lastMessageTick = platform.tickCount();
		String lettersBefore = matcher.letters();
		List<SpellMatcher.Spelled> spelledWords = matcher.accept(message.user(), message.text());
		if (config.mirrorChat || message.simulated()) {
			platform.broadcast(ChatMirror.message(message));
		}
		if (!matcher.letters().isEmpty() && !matcher.letters().equals(lettersBefore)) {
			platform.showOverlay(ChatMirror.progress(matcher.letters()));
		}

		// Announce after the message that finished the word, so it reads in order.
		announce(repeatTracker.onMessage(spelledWords, matcher.letters()));
	}

	private void announce(List<RepeatTracker.Event> events) {
		for (RepeatTracker.Event event : events) {
			switch (event.type()) {
				case SUMMON:
					summon(event);
					break;
				case PROGRESS:
					int left = event.needed() - event.count();
					platform.broadcast(spelledLine(event)
							.then("  Spell it " + left + " more time" + (left == 1 ? "" : "s") + " in a row to summon it!", Text.YELLOW));
					break;
				case BROKEN:
					platform.broadcast(Text.of(event.word() + " streak broken! Back to 0 of " + event.needed() + ".", Text.RED));
					break;
				default:
					break;
			}
		}
	}

	// ----- Summoning -----

	private void summon(RepeatTracker.Event event) {
		String word = event.word();
		Target target = dictionary.lookup(word);
		int now = platform.tickCount();

		Integer ready = readyAt.get(word);
		if (ready != null && ready > now) {
			platform.broadcast(spelledLine(event)
					.then("  but " + word + " is on cooldown, ready again in " + TimeFormat.countdown(ready - now), Text.RED));
			return;
		}

		String problem = platform.targetProblem(config);
		if (problem != null) {
			platform.broadcast(spelledLine(event).then("  but " + problem, Text.RED));
			return;
		}

		summonTarget(target, now);
		Text line = spelledLine(event);
		Integer cooldown = cooldownTicks.get(word);
		if (cooldown != null && cooldown > 0) {
			readyAt.put(word, now + cooldown);
			line.then("  Next " + word + " in " + TimeFormat.countdown(cooldown), Text.GRAY);
		}
		platform.broadcast(line);
	}

	private void summonTarget(Target target, int now) {
		int amount = Math.max(1, Math.min(64, config.amount));
		switch (target.kind()) {
			case ENTITY:
				if (amount == 1) {
					platform.spawnEntity(target, config);
				} else {
					// Several mobs trickle in over up to 2 seconds rather than all at once.
					for (int i = 0; i < amount; i++) {
						pendingSpawns.add(new PendingSpawn(target, now + random.nextInt(MAX_SPAWN_DELAY_TICKS + 1)));
					}
					spawnDueMobs();
				}
				break;
			case ITEM:
				int stack = Math.min(MAX_ITEMS, platform.maxStackSize(target));
				platform.dropItem(target, stack <= 1 ? 1 : 1 + random.nextInt(stack), config);
				break;
			case BLOCK:
				for (int i = 0; i < amount; i++) {
					platform.placeBlock(target, config);
				}
				break;
			case SPECIAL:
				for (int i = 0; i < amount; i++) {
					platform.runCommand(target, config);
				}
				break;
			default:
				break;
		}
	}

	private void spawnDueMobs() {
		int now = platform.tickCount();
		for (Iterator<PendingSpawn> it = pendingSpawns.iterator(); it.hasNext(); ) {
			PendingSpawn spawn = it.next();
			if (spawn.dueTick <= now) {
				it.remove();
				if (platform.targetProblem(config) == null) {
					platform.spawnEntity(spawn.target, config);
				}
			}
		}
	}

	/** "✨ Chat spelled COW! (Ann, Bob, Cat)", or "✨ Chat spelled WITHER 2/3! (...)" for repeat words. */
	private static Text spelledLine(RepeatTracker.Event event) {
		String times = event.needed() > 1 ? " " + event.count() + "/" + event.needed() : "";
		return Text.bold("✨ Chat spelled " + event.word() + times + "! (" + String.join(", ", event.spelled().users()) + ")", Text.GOLD);
	}
}
