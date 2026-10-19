package net.multiskin.config;

import com.mojang.brigadier.arguments.FloatArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.multiskin.MultiSkinMod;
import net.multiskin.skin.SkinManager;
import net.multiskin.skin.size.SizeManager;

import java.io.IOException;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class MultiSkinConfigCommand {
    private MultiSkinConfigCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(literal("multiskinconfig")
                        .requires(source -> source.hasPermissionLevel(2))
                        .then(literal("get")
                                .executes(context -> sendConfig(context.getSource()))
                                .then(literal("size")
                                        .then(literal("speed")
                                                .executes(context -> sendSpeed(context.getSource())))
                                        .then(literal("reach")
                                                .executes(context -> sendReach(context.getSource())))))
                        .then(literal("size")
                                .then(literal("speed")
                                        .then(argument("coefficient", FloatArgumentType.floatArg(
                                                MultiSkinConfig.MIN_EFFECT, MultiSkinConfig.MAX_EFFECT
                                        )).executes(context -> setSpeed(
                                                context.getSource(),
                                                FloatArgumentType.getFloat(context, "coefficient")
                                        ))))
                                .then(literal("reach")
                                        .then(argument("coefficient", FloatArgumentType.floatArg(
                                                MultiSkinConfig.MIN_EFFECT, MultiSkinConfig.MAX_EFFECT
                                        )).executes(context -> setReach(
                                                context.getSource(),
                                                FloatArgumentType.getFloat(context, "coefficient")
                                        )))))
                        .then(literal("reload").executes(context -> reload(context.getSource())))
                )
        );
    }

    private static int sendConfig(ServerCommandSource source) {
        sendSpeed(source);
        sendReach(source);
        return 1;
    }

    private static int sendSpeed(ServerCommandSource source) {
        source.sendFeedback(() -> Text.translatable(
                "command.multi-skin.config.speed_status",
                MultiSkinConfig.getMovementSpeedEffect()
        ).formatted(Formatting.YELLOW), false);
        return 1;
    }

    private static int sendReach(ServerCommandSource source) {
        source.sendFeedback(() -> Text.translatable(
                "command.multi-skin.config.reach_status",
                MultiSkinConfig.getReachEffect()
        ).formatted(Formatting.YELLOW), false);
        return 1;
    }

    private static int setSpeed(ServerCommandSource source, float value) {
        try {
            MultiSkinConfig.setMovementSpeedEffect(value);
            reapplyToPlayers(source);
            source.sendFeedback(() -> Text.translatable(
                    "command.multi-skin.config.speed_set", value
            ).formatted(Formatting.GREEN), true);
            return 1;
        } catch (IOException e) {
            return sendSaveError(source, e);
        }
    }

    private static int setReach(ServerCommandSource source, float value) {
        try {
            MultiSkinConfig.setReachEffect(value);
            reapplyToPlayers(source);
            source.sendFeedback(() -> Text.translatable(
                    "command.multi-skin.config.reach_set", value
            ).formatted(Formatting.GREEN), true);
            return 1;
        } catch (IOException e) {
            return sendSaveError(source, e);
        }
    }

    private static int reload(ServerCommandSource source) {
        try {
            MultiSkinConfig.load();
            reapplyToPlayers(source);
            source.sendFeedback(() -> Text.translatable("command.multi-skin.config.reloaded")
                    .formatted(Formatting.GREEN), true);
            return 1;
        } catch (IOException e) {
            source.sendError(Text.translatable("command.multi-skin.config.reload_error", e.getMessage())
                    .formatted(Formatting.RED));
            return 0;
        }
    }

    private static int sendSaveError(ServerCommandSource source, IOException exception) {
        source.sendError(Text.translatable("command.multi-skin.config.save_error", exception.getMessage())
                .formatted(Formatting.RED));
        return 0;
    }

    private static void reapplyToPlayers(ServerCommandSource source) {
        for (ServerPlayerEntity player : source.getServer().getPlayerManager().getPlayerList()) {
            try {
                SkinManager.SkinData skin = SkinManager.loadSkinData(player);
                if (skin != null) {
                    SizeManager.apply(player, skin.heightCm());
                }
            } catch (IOException e) {
                MultiSkinMod.LOGGER.warn(
                        "Impossible de réappliquer la taille de {}",
                        player.getName().getString(),
                        e
                );
            }
        }
    }
}
