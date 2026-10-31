package com.barrydamonke.twitchspelling.core;

import java.util.Locale;

import com.barrydamonke.twitchspelling.spell.ChatMessage;

/** Formats Twitch chat for Minecraft chat. Everything is plain text, so names and messages can't inject formatting. */
public final class ChatMirror {
	/** The colours Twitch gives users who never picked one. */
	private static final int[] DEFAULT_COLORS = {
			0xFF0000, 0x0000FF, 0x00FF00, 0xB22222, 0xFF7F50, 0x9ACD32, 0xFF4500, 0x2E8B57,
			0xDAA520, 0xD2691E, 0x5F9EA0, 0x1E90FF, 0xFF69B4, 0x8A2BE2, 0x00FF7F};

	private ChatMirror() {
	}

	/** "[Twitch] Ann: hello", with Ann in her Twitch colour. */
	public static Text message(ChatMessage message) {
		Text line = message.simulated()
				? Text.of("[Sim] ", Text.GRAY)
				: Text.of("[Twitch] ", Text.TWITCH_PURPLE);
		return line
				.then(message.user(), nameColor(message))
				.then(": " + message.text(), Text.WHITE);
	}

	/** Shown above the hotbar: "Letters: C O _". */
	public static Text progress(String letters) {
		StringBuilder spaced = new StringBuilder();
		for (char c : letters.toCharArray()) {
			spaced.append(c).append(' ');
		}
		return Text.of("Letters: " + spaced + "_", Text.YELLOW);
	}

	public static Text status(boolean good, String text) {
		return good ? Text.of("✔ " + text, Text.GREEN) : Text.of("✖ " + text, Text.RED);
	}

	private static int nameColor(ChatMessage message) {
		if (message.color() != null) {
			return message.color();
		}
		return DEFAULT_COLORS[Math.floorMod(message.user().toLowerCase(Locale.ROOT).hashCode(), DEFAULT_COLORS.length)];
	}
}
