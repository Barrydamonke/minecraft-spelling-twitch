package com.barrydamonke.twitchspelling.twitch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One line from Twitch's IRC chat server, e.g.
 * {@code @color=#FF0000;display-name=Ann :ann!ann@ann.tmi.twitch.tv PRIVMSG #channel :C}.
 */
public final class IrcLine {
	private final Map<String, String> tags;
	private final String prefix;
	private final String command;
	private final List<String> params;

	private IrcLine(Map<String, String> tags, String prefix, String command, List<String> params) {
		this.tags = Collections.unmodifiableMap(tags);
		this.prefix = prefix;
		this.command = command;
		this.params = Collections.unmodifiableList(params);
	}

	public static IrcLine parse(String line) {
		Map<String, String> tags = new HashMap<>();
		String rest = line;

		if (rest.startsWith("@")) {
			int space = rest.indexOf(' ');
			for (String tag : rest.substring(1, space).split(";")) {
				int equals = tag.indexOf('=');
				if (equals < 0) {
					tags.put(tag, "");
				} else {
					tags.put(tag.substring(0, equals), unescapeTag(tag.substring(equals + 1)));
				}
			}
			rest = rest.substring(space + 1);
		}

		String prefix = null;
		if (rest.startsWith(":")) {
			int space = rest.indexOf(' ');
			prefix = rest.substring(1, space);
			rest = rest.substring(space + 1);
		}

		String trailing = null;
		int trailingStart = rest.indexOf(" :");
		if (trailingStart >= 0) {
			trailing = rest.substring(trailingStart + 2);
			rest = rest.substring(0, trailingStart);
		}
		List<String> params = new ArrayList<>(Arrays.asList(rest.split(" ")));
		String command = params.remove(0);
		if (trailing != null) {
			params.add(trailing);
		}
		return new IrcLine(tags, prefix, command, params);
	}

	public Map<String, String> tags() {
		return tags;
	}

	public String prefix() {
		return prefix;
	}

	public String command() {
		return command;
	}

	public List<String> params() {
		return params;
	}

	/** The nickname part of the prefix ({@code ann!ann@ann.tmi.twitch.tv} gives {@code ann}). */
	public String nick() {
		if (prefix == null) {
			return "";
		}
		int bang = prefix.indexOf('!');
		return bang < 0 ? prefix : prefix.substring(0, bang);
	}

	/** The last parameter, which is the message text for PRIVMSG. */
	public String last() {
		return params.isEmpty() ? "" : params.get(params.size() - 1);
	}

	private static String unescapeTag(String value) {
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			if (c == '\\' && i + 1 < value.length()) {
				char next = value.charAt(++i);
				switch (next) {
					case 's':
						out.append(' ');
						break;
					case ':':
						out.append(';');
						break;
					case 'r':
						out.append('\r');
						break;
					case 'n':
						out.append('\n');
						break;
					default:
						out.append(next);
				}
			} else {
				out.append(c);
			}
		}
		return out.toString();
	}
}
