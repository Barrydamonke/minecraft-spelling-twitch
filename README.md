# Twitch Spelling

A Minecraft mod where **your Twitch chat spells words, one letter per message**, to summon mobs, items and blocks right next to you.

```
Ann: C
Bob: O
Cat: W
✨ Chat spelled COW! (Ann, Bob, Cat)      ← a cow appears next to you
```

No Twitch login, bot or password needed. The mod just reads your chat, the same way anyone can watch chat without logging in.

It also shows an **in-game timer** in the bottom-right corner of the screen.

---

## Installing (about 5 minutes)

The mod works on **Minecraft Java Edition 26.3** and **1.16.1**. Pick the one you play and use its files:

| You play | Mod file | Fabric API version |
|---|---|---|
| **26.3** | `twitchspelling-1.1.0+26.3.jar` | any version for 26.3 |
| **1.16.1** | `twitchspelling-1.1.0+1.16.1.jar` | `0.18.0+build.387-1.16.1` |

1. **Install Fabric** (this is what lets Minecraft run mods).
   - Download the installer from <https://fabricmc.net/use/installer/> and open it.
   - Pick your **Minecraft version** (26.3 or 1.16.1), leave everything else as it is, and click **Install**.
2. **Download Fabric API** (a helper that most mods need).
   - Go to <https://modrinth.com/mod/fabric-api/versions>, find the version from the table above, and download it.
3. **Put both mods in your mods folder.**
   - Press **Windows key + R**, type `%appdata%\.minecraft\mods` and press Enter. (If it says the folder doesn't exist, open `%appdata%\.minecraft` and create a folder called `mods`.)
   - Drop in the Fabric API file and the right **twitchspelling** file from the table.
4. **Start Minecraft** from the normal launcher, choose the **fabric-loader** profile for your version, and press Play.

## Connecting to your chat (once)

Open any world and type this in Minecraft chat, using your own Twitch channel name:

```
/twitchspelling connect yourchannelname
```

You'll see **✔ Connected to Twitch chat**. That's it. The mod remembers your channel and reconnects by itself every time you open a world.

You don't need cheats turned on in your own singleplayer world.

---

## How chat plays

- Each message is **one letter**. Lowercase is fine.
- Each letter has to come from a **different person** than the letter before it.
- Anything else (like `hello`, `C!` or an emote) **breaks the word** and chat starts over.
- A letter that can't lead to any word also starts over.
- Long names are spelled without spaces: `DIAMONDSWORD`, `IRONGOLEM`, `OAKLOG`.

| Chat types | What happens |
|---|---|
| C · O · W | 🐄 A cow appears |
| C · hello · O · W | Nothing, "hello" broke it |
| T · N · T | 💥 Lit TNT appears straight away. Run! |
| S · T · O · N · E | 🪨 1 to 16 stone drop on you (things that don't stack, like swords, come one at a time) |
| L·I·G·H·T·N·I·N·G | ⚡ Lightning strikes nearby |
| W·I·T·H·E·R | 💀 A wither, but only once every 10 minutes. Chat is told when the next one is ready |

**Short words inside longer words:** PIG is also the start of PIGLIN, so after P·I·G the mod waits a moment.
If the next letter continues the longer word (L), it keeps going. If anything else comes next, or nobody types for 10 seconds, the pig is summoned.
(TNT is an exception: it goes off straight away, even though TNTMINECART exists.)

While chat is spelling, the letters so far show above your hotbar, like `Letters: C O _`.
Every Twitch message also shows in Minecraft chat as **[Twitch] Name: message**.

## The timer

The bottom-right corner shows the world's in-game time, like `12:34.56`. It counts how long the world has been played, the same idea as a speedrun IGT, and stops while the game is paused.

---

## Commands

Type `/twitchspelling` on its own to see this list in the game.

| Command | What it does |
|---|---|
| `/twitchspelling connect <channel>` | Start reading your Twitch chat (remembered for next time) |
| `/twitchspelling disconnect` | Stop reading chat |
| `/twitchspelling status` | Check whether it's connected |
| `/twitchspelling settings` | Show all settings |
| `/twitchspelling lookup <word>` | See what a word would summon |
| `/twitchspelling sim <name> <message>` | Pretend someone typed in chat, for testing |

### Changing settings

Changes save straight away.

| Command | Example | What it does |
|---|---|---|
| `set mirrorChat` | `set mirrorChat false` | Stop copying Twitch chat into Minecraft (spelled words still show) |
| `set spawnRadius` | `set spawnRadius 8` | How far away things appear (2–32 blocks) |
| `set amount` | `set amount 3` | How many mobs or blocks per word. Several mobs pop in over 2 seconds. (Items are always a random 1–16) |
| `set targetPlayer` | `set targetPlayer Steve` / `set targetPlayer anyone` | Who things appear around |
| `set longerWordWaitSeconds` | `set longerWordWaitSeconds 5` | How long PIG waits for PIGLIN (0 = wait for the next message) |
| `cooldown set/remove/list` | `cooldown set warden 300` | Words that can only be summoned once every so many seconds |
| `instant add/remove/list` | `instant add pig` | Words that go off straight away instead of waiting for a longer word |
| `blocklist add/remove/list` | `blocklist add creeper` | Words chat isn't allowed to spell |
| `alias add/remove/list` | `alias add kitty cat` | Extra words for existing things |
| `special add/remove/list` | `special add boom summon tnt ~ ~ ~ {fuse:20}` | Words that run any Minecraft command |
| `repeat set/remove/list` | `repeat set warden 2` | Words that must be spelled several times in a row |

(All of these start with `/twitchspelling`, e.g. `/twitchspelling set amount 3`.)

Settings are stored in `.minecraft\config\twitchspelling.json`. You can also edit that file and then type `/twitchspelling reload`.

---

## Something not working?

- **"Connected" but no chat shows up.** Check the channel name is spelled right. Twitch doesn't warn about channels that don't exist.
- **Testing with `sim` and long words never work.** If you're connected to a busy Twitch chat, real messages break the word between your test letters. Type `/twitchspelling disconnect` first.
- **"Chat spelled COW! … but nobody is online"**. Someone has to be in the world for things to appear.
- **A word does nothing.** Try `/twitchspelling lookup theword`. It may be blocklisted, or it may not be a Minecraft name in your version.
- **Chat stops while the game is paused.** That's normal in singleplayer. It catches up when you unpause.
- **The mod doesn't load.** Make sure the mod file and Fabric API both match your Minecraft version, and you used the fabric-loader profile.

---

## For developers

The project has one shared part and one part per Minecraft version:

| Folder | What's in it |
|---|---|
| `common/` | Everything version-independent: spelling, cooldowns, Twitch chat, settings, commands (Java 8) |
| `mc26/` | Minecraft 26.3: spawning, chat text, timer (Java 25) |
| `mc1161/` | Minecraft 1.16.1: the same, with 1.16.1's names (Java 8) |

```
./gradlew build             # builds and tests everything
./gradlew collectJars       # copies both mod jars into build/libs
./gradlew :mc26:runClient   # starts Minecraft 26.3 with the mod
./gradlew :mc1161:runClient # starts Minecraft 1.16.1 with the mod
```

Licensed under the MIT License, see [LICENSE](LICENSE).
