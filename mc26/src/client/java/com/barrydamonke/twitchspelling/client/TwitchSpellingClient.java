package com.barrydamonke.twitchspelling.client;

import com.barrydamonke.twitchspelling.TwitchSpellingMod;
import com.barrydamonke.twitchspelling.core.TimeFormat;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/** Draws the in-game timer in the bottom-right corner of the screen. */
public class TwitchSpellingClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(TwitchSpellingMod.MOD_ID, "igt"), (graphics, deltaTracker) -> {
			Minecraft minecraft = Minecraft.getInstance();
			if (minecraft.level == null) {
				return;
			}
			// World ticks only advance while the game is running, so pausing pauses the timer too.
			String time = TimeFormat.igt(minecraft.level.getLevelData().getGameTime(),
					minecraft.isPaused() ? 0 : deltaTracker.getGameTimeDeltaPartialTick(false));
			int x = graphics.guiWidth() - minecraft.font.width(time) - 2;
			int y = graphics.guiHeight() - minecraft.font.lineHeight - 1;
			graphics.text(minecraft.font, time, x, y, 0xFFFFFFFF, true);
		});
	}
}
