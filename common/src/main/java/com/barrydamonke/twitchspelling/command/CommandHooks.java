package com.barrydamonke.twitchspelling.command;

import java.util.Collection;

import com.barrydamonke.twitchspelling.core.Text;

/** What the shared commands need from each Minecraft version's command system. */
public interface CommandHooks<S> {
	/** Whether this player (or the console) may use /twitchspelling. */
	boolean canUse(S source);

	void reply(S source, Text text);

	void fail(S source, Text text);

	/** Names of the players online, for autocompleting. */
	Collection<String> playerNames(S source);
}
