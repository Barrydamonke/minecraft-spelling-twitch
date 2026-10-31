package com.barrydamonke.twitchspelling.core;

/** Time as people read it. */
public final class TimeFormat {
	private TimeFormat() {
	}

	/**
	 * The in-game timer, from world ticks plus how far into the current tick the frame is:
	 * "12:34.56", or "1:02:03.45" after an hour.
	 */
	public static String igt(long ticks, float partialTick) {
		long centis = (long) ((ticks + Math.max(0, Math.min(1, partialTick))) * 5);
		long hours = centis / 360_000;
		long minutes = centis / 6000 % 60;
		long seconds = centis / 100 % 60;
		long hundredths = centis % 100;
		return hours > 0
				? String.format("%d:%02d:%02d.%02d", hours, minutes, seconds, hundredths)
				: String.format("%d:%02d.%02d", minutes, seconds, hundredths);
	}

	/** A countdown: "7:32", or "1:05:00" after an hour. Rounds up so it never shows 0:00 early. */
	public static String countdown(int ticks) {
		int total = (ticks + 19) / 20;
		int hours = total / 3600;
		return hours > 0
				? String.format("%d:%02d:%02d", hours, total / 60 % 60, total % 60)
				: String.format("%d:%02d", total / 60, total % 60);
	}
}
