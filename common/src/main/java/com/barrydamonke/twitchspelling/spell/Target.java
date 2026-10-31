package com.barrydamonke.twitchspelling.spell;

import java.util.Locale;
import java.util.Objects;

/** What a word summons: a mob, item or block by its id, or a special word's command. */
public final class Target {
	public enum Kind { ENTITY, ITEM, BLOCK, SPECIAL }

	private final Kind kind;
	/** e.g. "minecraft:cow". Null for specials. */
	private final String id;
	/** The command to run, without a leading slash. Only for specials. */
	private final String command;

	private Target(Kind kind, String id, String command) {
		this.kind = kind;
		this.id = id;
		this.command = command;
	}

	public static Target of(Kind kind, String id) {
		return new Target(kind, id, null);
	}

	public static Target special(String command) {
		return new Target(Kind.SPECIAL, null, command.startsWith("/") ? command.substring(1) : command);
	}

	public Kind kind() {
		return kind;
	}

	public String id() {
		return id;
	}

	public String command() {
		return command;
	}

	public String describe() {
		return kind == Kind.SPECIAL ? "command /" + command : kind.name().toLowerCase(Locale.ROOT) + " " + id;
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof Target)) {
			return false;
		}
		Target other = (Target) o;
		return kind == other.kind && Objects.equals(id, other.id) && Objects.equals(command, other.command);
	}

	@Override
	public int hashCode() {
		return Objects.hash(kind, id, command);
	}

	@Override
	public String toString() {
		return describe();
	}
}
