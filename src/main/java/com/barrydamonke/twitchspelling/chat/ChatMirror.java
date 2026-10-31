package com.barrydamonke.twitchspelling.chat;

import com.barrydamonke.twitchspelling.spell.ChatMessage;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Formats Twitch chat for Minecraft chat. Everything is plain text, so names and messages can't inject formatting. */
public final class ChatMirror {
	private static final int TWITCH_PURPLE = 0x9146FF;
	/** The colours Twitch gives users who never picked one. */
	private static final int[] DEFAULT_COLORS = {
			0xFF0000, 0x0000FF, 0x00FF00, 0xB22222, 0xFF7F50, 0x9ACD32, 0xFF4500, 0x2E8B57,
			0xDAA520, 0xD2691E, 0x5F9EA0, 0x1E90FF, 0xFF69B4, 0x8A2BE2, 0x00FF7F};

	private ChatMirror() {
	}

	/** "[Twitch] Ann: hello", with Ann in her Twitch colour. */
	public static Component message(ChatMessage message) {
		MutableComponent tag = message.simulated()
				? Component.literal("[Sim] ").withStyle(ChatFormatting.GRAY)
				: Component.literal("[Twitch] ").withColor(TWITCH_PURPLE);
		return tag
				.append(Component.literal(message.user()).withColor(nameColor(message)))
				.append(Component.literal(": " + message.text()).withStyle(ChatFormatting.WHITE));
	}

	/** Shown above the hotbar: "Letters: C O _". */
	public static Component progress(String letters) {
		return Component.literal("Letters: " + String.join(" ", letters.split("")) + " _").withStyle(ChatFormatting.YELLOW);
	}

	public static Component status(boolean good, String text) {
		return good
				? Component.literal("✔ " + text).withStyle(ChatFormatting.GREEN)
				: Component.literal("✖ " + text).withStyle(ChatFormatting.RED);
	}

	private static int nameColor(ChatMessage message) {
		if (message.color() != null) {
			return message.color();
		}
		return DEFAULT_COLORS[Math.floorMod(message.user().toLowerCase().hashCode(), DEFAULT_COLORS.length)];
	}
}
