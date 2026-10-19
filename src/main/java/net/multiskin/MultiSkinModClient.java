package net.multiskin;

import net.minecraft.client.MinecraftClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.multiskin.bodyparts.BodyPartClient;
import net.multiskin.skin.SkinClient;
import net.multiskin.skin.screen.SkinKeybinds;
import net.multiskin.skin.screen.SkinLibrary;
import net.multiskin.skin.screen.SkinScreen;

public class MultiSkinModClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        SkinClient.initialize();
        BodyPartClient.initialize();
        SkinLibrary.initialize();
        SkinKeybinds.initialize();
        SkinScreen.initialize();

        ClientTickEvents.END_CLIENT_TICK.register(MultiSkinModClient::tick);
    }

    private static void tick(MinecraftClient client) {
        SkinKeybinds.tick(client);
        while (SkinKeybinds.OPEN_MENU.wasPressed()) {
            if (client.currentScreen == null) {
                client.setScreen(new SkinScreen());
            }
        }
    }
}