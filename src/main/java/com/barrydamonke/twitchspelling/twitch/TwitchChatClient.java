package com.barrydamonke.twitchspelling.twitch;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ThreadLocalRandom;

import javax.net.ssl.SSLSocketFactory;

import com.barrydamonke.twitchspelling.TwitchSpellingMod;
import com.barrydamonke.twitchspelling.spell.ChatMessage;

/**
 * Reads a Twitch channel's chat as an anonymous viewer, so no account or password is needed.
 * Runs on its own thread and reconnects by itself if the connection drops.
 */
public final class TwitchChatClient {
	/** Called from the chat thread, so implementations must hand work over to the server thread. */
	public interface Listener {
		void onMessage(ChatMessage message);

		void onStatus(boolean good, String text);
	}

	private static final String HOST = "irc.chat.twitch.tv";
	private static final int PORT = 6697;
	/** Twitch pings about every 5 minutes, so a longer silence means the connection is dead. */
	private static final int READ_TIMEOUT_MS = 6 * 60 * 1000;

	private final String channel;
	private final Listener listener;
	private final String nick = "justinfan" + ThreadLocalRandom.current().nextInt(10000, 99999);

	private volatile boolean running = true;
	private volatile boolean connected;
	private volatile Socket socket;
	private Thread thread;
	private int failures;

	public TwitchChatClient(String channel, Listener listener) {
		this.channel = channel;
		this.listener = listener;
	}

	public void start() {
		thread = new Thread(this::run, "TwitchSpelling chat");
		thread.setDaemon(true);
		thread.start();
	}

	public void stop() {
		running = false;
		closeSocket();
		if (thread != null) {
			thread.interrupt();
		}
	}

	public String channel() {
		return channel;
	}

	public boolean isConnected() {
		return connected;
	}

	private void run() {
		while (running) {
			try {
				session();
			} catch (IOException e) {
				// stop() closes the socket on purpose, which isn't worth a warning.
				if (running) {
					TwitchSpellingMod.LOGGER.warn("Twitch chat connection problem: {}", e.toString());
				}
			}
			if (!running) {
				break;
			}

			if (connected) {
				listener.onStatus(false, "Lost connection to Twitch chat, reconnecting...");
			} else if (failures == 0) {
				listener.onStatus(false, "Couldn't reach Twitch chat, retrying...");
			}
			connected = false;
			failures++;

			try {
				Thread.sleep(Math.min(30, 1L << Math.min(failures, 5)) * 1000);
			} catch (InterruptedException e) {
				// stop() was called, the loop condition handles it.
			}
		}
		connected = false;
	}

	private void session() throws IOException {
		try (Socket s = SSLSocketFactory.getDefault().createSocket()) {
			socket = s;
			s.connect(new InetSocketAddress(HOST, PORT), 10_000);
			s.setSoTimeout(READ_TIMEOUT_MS);
			BufferedReader reader = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
			Writer writer = new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8);

			send(writer, "CAP REQ :twitch.tv/tags twitch.tv/commands");
			send(writer, "NICK " + nick);
			send(writer, "JOIN #" + channel);

			String line;
			while (running && (line = reader.readLine()) != null) {
				if (!handle(writer, IrcLine.parse(line))) {
					return;
				}
			}
		} finally {
			socket = null;
		}
	}

	/** Returns false when Twitch asks us to reconnect. */
	private boolean handle(Writer writer, IrcLine line) throws IOException {
		switch (line.command()) {
			case "PING" -> send(writer, "PONG :" + line.last());
			case "JOIN" -> {
				if (line.nick().equals(nick)) {
					connected = true;
					failures = 0;
					listener.onStatus(true, "Connected to Twitch chat: #" + channel);
				}
			}
			case "PRIVMSG" -> {
				String name = line.tags().getOrDefault("display-name", "");
				if (name.isBlank()) {
					name = line.nick();
				}
				listener.onMessage(new ChatMessage(name, parseColor(line.tags().get("color")), stripAction(line.last()), false));
			}
			case "NOTICE" -> listener.onStatus(false, "Twitch says: " + line.last());
			case "RECONNECT" -> {
				return false;
			}
			default -> {
			}
		}
		return true;
	}

	private static void send(Writer writer, String line) throws IOException {
		writer.write(line + "\r\n");
		writer.flush();
	}

	/** "/me waves" arrives wrapped as {@code \u0001ACTION waves\u0001}. */
	private static String stripAction(String text) {
		if (text.startsWith("\u0001ACTION ") && text.endsWith("\u0001")) {
			return text.substring(8, text.length() - 1);
		}
		return text;
	}

	/** "#FF0000" becomes 0xFF0000. Null when the user never picked a colour. */
	private static Integer parseColor(String hex) {
		if (hex == null || hex.length() != 7 || !hex.startsWith("#")) {
			return null;
		}
		try {
			return Integer.parseInt(hex.substring(1), 16);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private void closeSocket() {
		Socket s = socket;
		if (s != null) {
			try {
				s.close();
			} catch (IOException ignored) {
				// Closing anyway.
			}
		}
	}

	/** Turns "#Name", "twitch.tv/name" or a full URL into the bare lowercase channel name. */
	public static String normalizeChannel(String input) {
		String channel = input.trim().toLowerCase(java.util.Locale.ROOT);
		int slash = channel.lastIndexOf('/');
		if (slash >= 0) {
			channel = channel.substring(slash + 1);
		}
		if (channel.startsWith("#") || channel.startsWith("@")) {
			channel = channel.substring(1);
		}
		return channel;
	}
}
