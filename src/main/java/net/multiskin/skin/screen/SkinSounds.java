package net.multiskin.skin.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundEvents;

public class SkinSounds {
    public static void click() {
        play(SoundEvents.UI_BUTTON_CLICK.value(), 0.7F, 1.0F);
    }

    public static void error() {
        play(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), 0.5F, 0.8F);
    }

    private static void play(net.minecraft.sound.SoundEvent sound, float volume, float pitch) {
        MinecraftClient client = MinecraftClient.getInstance();

        if (client.player != null) {
            client.player.playSound(sound, volume, pitch);
        }
    }
}