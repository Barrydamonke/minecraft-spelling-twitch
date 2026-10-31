package com.barrydamonke.twitchspelling.client;

import com.barrydamonke.twitchspelling.core.TimeFormat;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;

/** Draws the in-game timer in the bottom-right corner of the screen. */
public class TwitchSpellingClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		HudRenderCallback.EVENT.register((poseStack, tickDelta) -> {
			Minecraft minecraft = Minecraft.getInstance();
			if (minecraft.level == null) {
				return;
			}
			// World ticks only advance while the game is running, so pausing pauses the timer too.
			String time = TimeFormat.igt(minecraft.level.getLevelData().getGameTime(), minecraft.isPaused() ? 0 : tickDelta);
			int x = minecraft.getWindow().getGuiScaledWidth() - minecraft.font.width(time) - 2;
			int y = minecraft.getWindow().getGuiScaledHeight() - minecraft.font.lineHeight - 1;
			minecraft.font.drawShadow(poseStack, time, x, y, 0xFFFFFF);
		});
	}
}
