package com.barrydamonke.twitchspelling.spell;

/** One chat message from Twitch (or from {@code /twitchspelling sim}). */
public final class ChatMessage {
	private final String user;
	private final Integer color;
	private final String text;
	private final boolean simulated;

	/**
	 * @param color the user's Twitch name colour as 0xRRGGBB, or null if they never picked one
	 * @param simulated true when it came from {@code /twitchspelling sim}
	 */
	public ChatMessage(String user, Integer color, String text, boolean simulated) {
		this.user = user;
		this.color = color;
		this.text = text;
		this.simulated = simulated;
	}

	public String user() {
		return user;
	}

	public Integer color() {
		return color;
	}

	public String text() {
		return text;
	}

	public boolean simulated() {
		return simulated;
	}
}
