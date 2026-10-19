package net.multiskin.bodyparts;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Collection;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class BodyPartCommand {
    private BodyPartCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(literal("bodypart")
                        .requires(source -> source.hasPermissionLevel(2))
                        .then(createPartBranch("remove", true))
                        .then(createPartBranch("add", false))
                        .then(literal("reset")
                                .then(argument("player", EntityArgumentType.players())
                                        .executes(context -> resetPlayers(
                                                context.getSource(),
                                                EntityArgumentType.getPlayers(context, "player")
                                        )))
                        )
                )
        );
    }

    private static LiteralArgumentBuilder<ServerCommandSource> createPartBranch(
            String action,
            boolean removed
    ) {
        return literal(action)
                .then(argument("player", EntityArgumentType.players())
                        .then(createLimbBranch("arm", BodyPart.RIGHT_ARM, BodyPart.LEFT_ARM, removed))
                        .then(createLimbBranch("leg", BodyPart.RIGHT_LEG, BodyPart.LEFT_LEG, removed))
                );
    }

    private static LiteralArgumentBuilder<ServerCommandSource> createLimbBranch(
            String limb,
            BodyPart rightPart,
            BodyPart leftPart,
            boolean removed
    ) {
        return literal(limb)
                .then(literal("right")
                        .executes(context -> updatePlayers(
                                context.getSource(),
                                EntityArgumentType.getPlayers(context, "player"),
                                rightPart,
                                removed
                        )))
                .then(literal("left")
                        .executes(context -> updatePlayers(
                                context.getSource(),
                                EntityArgumentType.getPlayers(context, "player"),
                                leftPart,
                                removed
                        )));
    }

    private static int updatePlayers(
            ServerCommandSource source,
            Collection<ServerPlayerEntity> players,
            BodyPart part,
            boolean removed
    ) {
        for (ServerPlayerEntity player : players) {
            int mask = BodyPartManager.setPartRemoved(player, part, removed);
            BodyPartNetworking.broadcast(player, mask);
        }
        if (players.size() == 1) {
            ServerPlayerEntity player = players.iterator().next();
            source.sendFeedback(
                    () -> Text.translatable(
                            removed
                                    ? "command.multi-skin.bodyparts.removed_single"
                                    : "command.multi-skin.bodyparts.restored_single",
                            Text.translatable(part.translationKey()),
                            player.getName()
                    ).formatted(Formatting.GREEN),
                    true
            );
        } else {
            source.sendFeedback(
                    () -> Text.translatable(
                            removed
                                    ? "command.multi-skin.bodyparts.removed"
                                    : "command.multi-skin.bodyparts.restored",
                            Text.translatable(part.translationKey()),
                            players.size()
                    ).formatted(Formatting.GREEN),
                    true
            );
        }
        return players.size();
    }

    private static int resetPlayers(ServerCommandSource source, Collection<ServerPlayerEntity> players) {
        for (ServerPlayerEntity player : players) {
            BodyPartManager.reset(player);
            BodyPartNetworking.broadcast(player, 0);
        }
        if (players.size() == 1) {
            ServerPlayerEntity player = players.iterator().next();
            source.sendFeedback(
                    () -> Text.translatable("command.multi-skin.bodyparts.reset_single", player.getName())
                            .formatted(Formatting.GREEN),
                    true
            );
        } else {
            source.sendFeedback(
                    () -> Text.translatable("command.multi-skin.bodyparts.reset", players.size())
                            .formatted(Formatting.GREEN),
                    true
            );
        }
        return players.size();
    }
}
