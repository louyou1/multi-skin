package net.multiskin.skin;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import net.minecraft.server.network.ServerPlayerEntity;
import net.multiskin.MultiSkinMod;
import net.multiskin.skin.size.SizeManager;

public class SkinJoinHandler {
    public static void initialize() {
        ServerPlayConnectionEvents.JOIN.register(
                (handler, sender, server) -> {
                    ServerPlayerEntity joiningPlayer = handler.getPlayer();
                    MultiSkinMod.LOGGER.info("{} rejoint", joiningPlayer.getName().getString());
                    applySavedSize(joiningPlayer);
                }
        );

        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> applySavedSize(newPlayer));
    }

    private static void applySavedSize(ServerPlayerEntity player) {
        try {
            SkinManager.SkinData skin = SkinManager.loadSkinData(player);
            if (skin == null) {
                SizeManager.reset(player);
                return;
            }

            SizeManager.apply(player, skin.heightCm());
            SkinNetworking.broadcastSkin(player, skin.url(), skin.model(), skin.heightCm());
        } catch (Exception e) {
            MultiSkinMod.LOGGER.warn(
                    "Impossible d'appliquer la taille sauvegardée de {}",
                    player.getName().getString(),
                    e
            );
            SizeManager.reset(player);
        }
    }
}