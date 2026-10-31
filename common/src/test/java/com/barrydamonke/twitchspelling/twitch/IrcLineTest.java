package com.barrydamonke.twitchspelling.twitch;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

class IrcLineTest {
	@Test
	void chatMessageWithTags() {
		IrcLine line = IrcLine.parse("@badge-info=;color=#1E90FF;display-name=Ann_B;emotes= :ann_b!ann_b@ann_b.tmi.twitch.tv PRIVMSG #streamer :C");
		assertEquals("PRIVMSG", line.command());
		assertEquals("Ann_B", line.tags().get("display-name"));
		assertEquals("#1E90FF", line.tags().get("color"));
		assertEquals("", line.tags().get("emotes"));
		assertEquals("ann_b", line.nick());
		assertEquals(Arrays.asList("#streamer", "C"), line.params());
	}

	@Test
	void messageTextCanContainColonsAndSpaces() {
		IrcLine line = IrcLine.parse(":bob!bob@bob.tmi.twitch.tv PRIVMSG #streamer :hello :) how are you");
		assertEquals("hello :) how are you", line.last());
	}

	@Test
	void ping() {
		IrcLine line = IrcLine.parse("PING :tmi.twitch.tv");
		assertEquals("PING", line.command());
		assertEquals("tmi.twitch.tv", line.last());
	}

	@Test
	void ourJoin() {
		IrcLine line = IrcLine.parse(":justinfan12345!justinfan12345@justinfan12345.tmi.twitch.tv JOIN #streamer");
		assertEquals("JOIN", line.command());
		assertEquals("justinfan12345", line.nick());
	}

	@Test
	void escapedTagValues() {
		IrcLine line = IrcLine.parse("@system-msg=Ann\\ssubscribed\\:\\sthanks :tmi.twitch.tv USERNOTICE #streamer");
		assertEquals("Ann subscribed; thanks", line.tags().get("system-msg"));
	}

	@Test
	void channelNamesAreCleanedUp() {
		assertEquals("streamer", TwitchChatClient.normalizeChannel("Streamer"));
		assertEquals("streamer", TwitchChatClient.normalizeChannel("#streamer"));
		assertEquals("streamer", TwitchChatClient.normalizeChannel("https://www.twitch.tv/Streamer"));
		assertEquals("streamer", TwitchChatClient.normalizeChannel(" twitch.tv/streamer "));
	}
}
