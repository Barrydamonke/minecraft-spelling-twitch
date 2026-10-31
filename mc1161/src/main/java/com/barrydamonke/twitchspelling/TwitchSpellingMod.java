package com.barrydamonke.twitchspelling;

import java.util.Arrays;
import java.util.Collection;

import com.barrydamonke.twitchspelling.command.CommandHooks;
import com.barrydamonke.twitchspelling.command.TwitchSpellingCommand;
import com.barrydamonke.twitchspelling.core.SpellingGame;
import com.barrydamonke.twitchspelling.core.Text;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TwitchSpellingMod implements ModInitializer {
	public static final String MOD_ID = "twitchspelling";
	public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

	private static final Platform1161 PLATFORM = new Platform1161();
	private static final SpellingGame GAME = new SpellingGame(PLATFORM);

	@Override
	public void onInitialize() {
		CommandRegistrationCallback.EVENT.register((dispatcher, dedicated) ->
				dispatcher.register(TwitchSpellingCommand.build(GAME, new Hooks())));

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			PLATFORM.setServer(server);
			GAME.serverStarted();
			LOGGER.info("Twitch Spelling ready: {} spellable words", GAME.dictionary().wordList().size());
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> GAME.serverStopping());
		ServerTickEvents.END_SERVER_TICK.register(server -> GAME.tick());
	}

	private static final class Hooks implements CommandHooks<CommandSourceStack> {
		@Override
		public boolean canUse(CommandSourceStack source) {
			// The owner of a singleplayer world can always use it, even with cheats off.
			Entity entity = source.getEntity();
			return source.hasPermission(2)
					|| (entity instanceof ServerPlayer && source.getServer().isSingleplayerOwner(((ServerPlayer) entity).getGameProfile()));
		}

		@Override
		public void reply(CommandSourceStack source, Text text) {
			source.sendSuccess(Platform1161.toComponent(text), false);
		}

		@Override
		public void fail(CommandSourceStack source, Text text) {
			source.sendFailure(Platform1161.toComponent(text));
		}

		@Override
		public Collection<String> playerNames(CommandSourceStack source) {
			return Arrays.asList(source.getServer().getPlayerNames());
		}
	}
}
