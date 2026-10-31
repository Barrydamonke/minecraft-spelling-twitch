package com.barrydamonke.twitchspelling.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A chat line made of coloured pieces. Each Minecraft version turns this into its own chat
 * component, so the shared code never touches Minecraft's text classes.
 */
public final class Text {
	public static final int WHITE = 0xFFFFFF;
	public static final int GRAY = 0xAAAAAA;
	public static final int DARK_GRAY = 0x555555;
	public static final int GOLD = 0xFFAA00;
	public static final int YELLOW = 0xFFFF55;
	public static final int GREEN = 0x55FF55;
	public static final int RED = 0xFF5555;
	public static final int TWITCH_PURPLE = 0x9146FF;

	public static final class Part {
		public final String text;
		/** 0xRRGGBB. */
		public final int color;
		public final boolean bold;

		Part(String text, int color, boolean bold) {
			this.text = text;
			this.color = color;
			this.bold = bold;
		}
	}

	private final List<Part> parts = new ArrayList<>();

	private Text() {
	}

	public static Text of(String text, int color) {
		return new Text().then(text, color);
	}

	public static Text bold(String text, int color) {
		Text result = new Text();
		result.parts.add(new Part(text, color, true));
		return result;
	}

	public Text then(String text, int color) {
		parts.add(new Part(text, color, false));
		return this;
	}

	public Text then(Text other) {
		parts.addAll(other.parts);
		return this;
	}

	public List<Part> parts() {
		return Collections.unmodifiableList(parts);
	}

	/** The words without colours, for logs and tests. */
	public String plain() {
		StringBuilder out = new StringBuilder();
		for (Part part : parts) {
			out.append(part.text);
		}
		return out.toString();
	}

	@Override
	public String toString() {
		return plain();
	}
}
