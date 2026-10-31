package com.barrydamonke.twitchspelling.spell;

/**
 * One chat message from Twitch (or from {@code /twitchspelling sim}).
 *
 * @param color the user's Twitch name colour as 0xRRGGBB, or null if they never picked one
 * @param simulated true when it came from {@code /twitchspelling sim}
 */
public record ChatMessage(String user, Integer color, String text, boolean simulated) {
}
