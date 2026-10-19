package net.multiskin;

import net.multiskin.bodyparts.BodyPartCommand;
import net.multiskin.bodyparts.BodyPartRules;
import net.multiskin.bodyparts.BodyPartNetworking;
import net.multiskin.item.ModItems;
import net.fabricmc.api.ModInitializer;
import net.multiskin.skin.SkinCommand;
import net.multiskin.skin.SkinJoinHandler;
import net.multiskin.skin.SkinNetworking;
import net.multiskin.config.MultiSkinConfig;
import net.multiskin.config.MultiSkinConfigCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class MultiSkinMod implements ModInitializer {
    public static final String MOD_ID = "multi-skin";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ModItems.registerModItems();
        ModGroups.registerGroups();

        try {
            MultiSkinConfig.load();
        } catch (IOException e) {
            LOGGER.error("Impossible de charger la configuration Multi-Skin.", e);
        }

        MultiSkinConfigCommand.register();
        SkinCommand.register();
        SkinNetworking.initialize();
        SkinJoinHandler.initialize();
        BodyPartCommand.register();
        BodyPartNetworking.initialize();
        BodyPartRules.initialize();
    }
}