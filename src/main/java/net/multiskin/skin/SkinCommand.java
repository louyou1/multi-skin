package net.multiskin.skin;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.multiskin.bodyparts.BodyPartManager;
import net.multiskin.bodyparts.BodyPartNetworking;
import net.multiskin.skin.size.SizeManager;

import java.util.Collection;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.io.IOException;
import java.awt.image.BufferedImage;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class SkinCommand {
    private static final ConcurrentHashMap<UUID, Long> DOWNLOADS_IN_PROGRESS = new ConcurrentHashMap<>();
    private static final AtomicLong DOWNLOAD_IDS = new AtomicLong();

    private static long beginDownload(UUID playerUuid) {
        long downloadId = DOWNLOAD_IDS.incrementAndGet();
        DOWNLOADS_IN_PROGRESS.put(playerUuid, downloadId);
        return downloadId;
    }

    private static boolean isCurrentDownload(UUID playerUuid, long downloadId) {
        return Objects.equals(DOWNLOADS_IN_PROGRESS.get(playerUuid), downloadId);
    }

    private static void finishDownload(UUID playerUuid, long downloadId) {
        DOWNLOADS_IN_PROGRESS.remove(playerUuid, downloadId);
    }

    public static long beginUploadedSkinRequest(UUID playerUuid) {
        return beginDownload(playerUuid);
    }

    public static boolean isCurrentUploadedSkinRequest(UUID playerUuid, long requestId) {
        return isCurrentDownload(playerUuid, requestId);
    }

    public static void cancelUploadedSkinRequest(UUID playerUuid, long requestId) {
        finishDownload(playerUuid, requestId);
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) -> {
                    dispatcher.register(literal("skin")
                            .requires(source -> source.getEntity() instanceof ServerPlayerEntity)
                            .then(createSkinBranch("slim", source -> Objects.requireNonNull(source.getSource().getPlayer())))
                            .then(createSkinBranch("steve", source -> Objects.requireNonNull(source.getSource().getPlayer())))
                            .then(literal("reset")
                                    .executes(context -> resetSkin(
                                            Objects.requireNonNull(context.getSource().getPlayer())
                                    ))
                            )
                    );

                    dispatcher.register(literal("skinadmin")
                            .requires(source -> source.hasPermissionLevel(2))
                            .then(argument("players", EntityArgumentType.players())
                                    .then(createAdminSkinBranch("slim"))
                                    .then(createAdminSkinBranch("steve"))
                                    .then(literal("reset")
                                            .executes(SkinCommand::resetTargetSkins)
                                    )
                            )
                    );
                }
        );
    }

    private static Collection<ServerPlayerEntity> getTargetPlayers(CommandContext<ServerCommandSource> context) {
        try {
            return EntityArgumentType.getPlayers(context, "players");
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            throw new IllegalStateException("La cible de la commande est introuvable.", e);
        }
    }

    private static LiteralArgumentBuilder<ServerCommandSource> createAdminSkinBranch(String model) {
        return literal(model)
                .then(argument("urlAndHeight", StringArgumentType.greedyString())
                        .executes(context -> {
                            int result = 0;
                            for (ServerPlayerEntity player : getTargetPlayers(context)) {
                                result += applySkinFromInput(
                                        player,
                                        model,
                                        StringArgumentType.getString(context, "urlAndHeight")
                                );
                            }
                            return result;
                        }));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> createSkinBranch(
            String model,
            java.util.function.Function<CommandContext<ServerCommandSource>, ServerPlayerEntity> playerSupplier
    ) {
        return literal(model)
                .then(argument("urlAndHeight", StringArgumentType.greedyString())
                        .executes(context -> applySkinFromInput(
                                playerSupplier.apply(context),
                                model,
                                StringArgumentType.getString(context, "urlAndHeight")
                        ))
                );
    }

    private static int applySkinFromInput(ServerPlayerEntity player, String model, String input) {
        String value = input.trim();
        String url = value;
        int heightCm = SizeManager.DEFAULT_HEIGHT_CM;

        int separator = value.lastIndexOf(' ');
        if (separator > 0) {
            String heightValue = value.substring(separator + 1);
            try {
                heightCm = Integer.parseInt(heightValue);
                url = value.substring(0, separator).trim();
            } catch (NumberFormatException ignored) {
                // The complete argument is treated as the URL when no size is provided.
            }
        }

        return applySkin(player, model, url, heightCm);
    }

    public static void applySkinFromScreen(
            ServerPlayerEntity player,
            String model,
            String url,
            int heightCm,
            int removedPartsMask
    ) {
        if (!model.equalsIgnoreCase("slim") && !model.equalsIgnoreCase("steve")) {
            player.sendMessage(Text.translatable("command.multi-skin.skin.invalid_model")
                    .formatted(Formatting.RED), false);
            return;
        }

        applySkin(player, model, url, heightCm, removedPartsMask & 0xF);
    }

    public static void refreshOwnSkin(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        UUID playerUuid = player.getUuid();
        SkinManager.SkinData currentSkin;
        try {
            currentSkin = SkinManager.loadSkinData(playerUuid);
        } catch (IOException e) {
            player.sendMessage(SkinManager.errorText(e, "command.multi-skin.skin.read_current_error")
                    .formatted(Formatting.RED), false);
            return;
        }

        if (currentSkin == null) {
            return;
        }

        if (currentSkin.url().equals(SkinManager.UPLOADED_SKIN_SOURCE)) {
            SizeManager.apply(player, currentSkin.heightCm());
            SkinNetworking.broadcastSkin(
                    player, currentSkin.url(), currentSkin.model(), currentSkin.heightCm());
            return;
        }

        long downloadId = beginDownload(playerUuid);

        Thread.startVirtualThread(() -> {
            try {
                BufferedImage image = SkinManager.validateSkinUrl(currentSkin.url());
                server.execute(() -> {
                    try {
                        if (!isCurrentDownload(playerUuid, downloadId)) {
                            return;
                        }
                        ServerPlayerEntity currentPlayer = server.getPlayerManager().getPlayer(playerUuid);
                        if (currentPlayer != null) {
                            SkinManager.saveDownloadedSkin(playerUuid, image);
                            SizeManager.apply(currentPlayer, currentSkin.heightCm());
                            SkinNetworking.broadcastSkin(
                                    currentPlayer,
                                    currentSkin.url(),
                                    currentSkin.model(),
                                    currentSkin.heightCm()
                            );
                        }
                    } catch (Exception e) {
                        ServerPlayerEntity currentPlayer = server.getPlayerManager().getPlayer(playerUuid);
                        if (currentPlayer != null && isCurrentDownload(playerUuid, downloadId)) {
                            currentPlayer.sendMessage(
                                    SkinManager.errorText(e, "command.multi-skin.skin.download_error")
                                            .formatted(Formatting.RED),
                                    false
                            );
                        }
                    } finally {
                        finishDownload(playerUuid, downloadId);
                    }
                });
            } catch (Exception e) {
                server.execute(() -> {
                    ServerPlayerEntity currentPlayer = server.getPlayerManager().getPlayer(playerUuid);
                    if (currentPlayer != null && isCurrentDownload(playerUuid, downloadId)) {
                        currentPlayer.sendMessage(
                                SkinManager.errorText(e, "command.multi-skin.skin.download_error")
                                        .formatted(Formatting.RED),
                                false
                        );
                    }
                    finishDownload(playerUuid, downloadId);
                });
            }
        });
    }

    private static int applySkin(ServerPlayerEntity player, String model, String url, int heightCm) {
        return applySkin(player, model, url, heightCm, BodyPartManager.getMask(player));
    }

    private static int applySkin(
            ServerPlayerEntity player,
            String model,
            String url,
            int heightCm,
            int removedPartsMask
    ) {
        if (heightCm < SizeManager.MIN_HEIGHT_CM || heightCm > SizeManager.MAX_HEIGHT_CM) {
            player.sendMessage(
                    Text.translatable("command.multi-skin.skin.invalid_height",
                            SizeManager.MIN_HEIGHT_CM, SizeManager.MAX_HEIGHT_CM)
                            .formatted(Formatting.RED),
                    false
            );
            return 0;
        }

        if (url.isBlank()) {
            player.sendMessage(Text.translatable("command.multi-skin.skin.required_url")
                    .formatted(Formatting.RED), false);
            return 0;
        }

        MinecraftServer server = player.getServer();
        if (server == null) {
            player.sendMessage(Text.translatable("command.multi-skin.skin.server_unavailable")
                    .formatted(Formatting.RED), false);
            return 0;
        }

        UUID playerUuid = player.getUuid();
        SkinManager.SkinData currentSkin;
        try {
            currentSkin = SkinManager.loadSkinData(playerUuid);
        } catch (IOException e) {
            player.sendMessage(SkinManager.errorText(e, "command.multi-skin.skin.read_current_error")
                    .formatted(Formatting.RED), false);
            return 0;
        }

        DOWNLOADS_IN_PROGRESS.remove(playerUuid);

        if (currentSkin != null
                && currentSkin.url().equals(url)
                && currentSkin.model().equalsIgnoreCase(model)
                && currentSkin.heightCm() == heightCm
                && BodyPartManager.getMask(player) == removedPartsMask
                && SkinManager.hasDownloadedSkin(playerUuid)) {
            SizeManager.apply(player, heightCm);
            SkinNetworking.broadcastSkin(player, url, model, heightCm);
            return 1;
        }

        boolean downloadRequired = currentSkin == null
                || !currentSkin.url().equals(url)
                || !SkinManager.hasDownloadedSkin(playerUuid);

        if (!downloadRequired) {
            try {
                SkinManager.saveSkinData(playerUuid, url, model, heightCm);
                setRemovedParts(player, removedPartsMask);
                SizeManager.apply(player, heightCm);
                SkinNetworking.broadcastSkin(player, url, model, heightCm);
                return 1;
            } catch (IOException e) {
                player.sendMessage(
                        SkinManager.errorText(e, "command.multi-skin.skin.save_error")
                                .formatted(Formatting.RED),
                        false
                );
                return 0;
            }
        }

        long downloadId = beginDownload(playerUuid);

        Thread.startVirtualThread(() -> {
            try {
                BufferedImage image = SkinManager.validateSkinUrl(url);

                server.execute(() -> {
                    try {
                        if (!isCurrentDownload(playerUuid, downloadId)) {
                            return;
                        }
                        ServerPlayerEntity currentPlayer = server.getPlayerManager().getPlayer(playerUuid);
                        if (currentPlayer == null) {
                            return;
                        }

                        SkinManager.saveDownloadedSkin(playerUuid, image);
                        SkinManager.saveSkinData(playerUuid, url, model, heightCm);
                        setRemovedParts(currentPlayer, removedPartsMask);
                        SizeManager.apply(currentPlayer, heightCm);
                        SkinNetworking.broadcastSkin(currentPlayer, url, model, heightCm);
                    } catch (Exception e) {
                        ServerPlayerEntity currentPlayer = server.getPlayerManager().getPlayer(playerUuid);
                        if (currentPlayer != null && isCurrentDownload(playerUuid, downloadId)) {
                            currentPlayer.sendMessage(
                                    SkinManager.errorText(e, "command.multi-skin.skin.download_error")
                                            .formatted(Formatting.RED),
                                    false
                            );
                        }
                    } finally {
                        finishDownload(playerUuid, downloadId);
                    }
                });
            } catch (Exception e) {
                server.execute(() -> {
                    ServerPlayerEntity currentPlayer = server.getPlayerManager().getPlayer(playerUuid);
                    if (currentPlayer != null && isCurrentDownload(playerUuid, downloadId)) {
                        currentPlayer.sendMessage(
                                SkinManager.errorText(e, "command.multi-skin.skin.download_error")
                                        .formatted(Formatting.RED),
                                false
                        );
                    }
                    finishDownload(playerUuid, downloadId);
                });
            }
        });

        return 1;
    }

    private static int resetSkin(ServerPlayerEntity player) {
        DOWNLOADS_IN_PROGRESS.remove(player.getUuid());
        try {
            SkinManager.deleteSkinData(player);
            setRemovedParts(player, 0);
            SizeManager.reset(player);
            SkinNetworking.broadcastReset(player);
            return 1;
        } catch (Exception e) {
            player.sendMessage(Text.translatable("command.multi-skin.skin.reset_error", e.getMessage())
                    .formatted(Formatting.RED), false);
            return 0;
        }
    }

    public static void applyUploadedSkin(
            ServerPlayerEntity player,
            String model,
            int heightCm,
            int removedPartsMask,
            BufferedImage image,
            long requestId
    ) {
        if (!isCurrentDownload(player.getUuid(), requestId)) {
            return;
        }
        if (!model.equalsIgnoreCase("slim") && !model.equalsIgnoreCase("steve")) {
            player.sendMessage(Text.translatable("command.multi-skin.skin.invalid_model")
                    .formatted(Formatting.RED), false);
            finishDownload(player.getUuid(), requestId);
            return;
        }
        if (heightCm < SizeManager.MIN_HEIGHT_CM || heightCm > SizeManager.MAX_HEIGHT_CM) {
            player.sendMessage(Text.translatable("command.multi-skin.skin.invalid_height",
                    SizeManager.MIN_HEIGHT_CM, SizeManager.MAX_HEIGHT_CM).formatted(Formatting.RED), false);
            finishDownload(player.getUuid(), requestId);
            return;
        }

        try {
            SkinManager.saveDownloadedSkin(player.getUuid(), SkinManager.validateSkinImage(image));
            SkinManager.saveSkinData(
                    player.getUuid(), SkinManager.UPLOADED_SKIN_SOURCE, model, heightCm);
            setRemovedParts(player, removedPartsMask & 0xF);
            SizeManager.apply(player, heightCm);
            SkinNetworking.broadcastSkin(
                    player, SkinManager.UPLOADED_SKIN_SOURCE, model, heightCm);
        } catch (Exception e) {
            player.sendMessage(SkinManager.errorText(e, "command.multi-skin.skin.upload_error")
                    .formatted(Formatting.RED), false);
        } finally {
            finishDownload(player.getUuid(), requestId);
        }
    }

    private static void setRemovedParts(ServerPlayerEntity player, int removedPartsMask) {
        BodyPartManager.setMask(player, removedPartsMask);
        BodyPartNetworking.broadcast(player, removedPartsMask);
    }

    public static void resetSkinFromScreen(ServerPlayerEntity player) {
        resetSkin(player);
    }

    private static int resetTargetSkins(CommandContext<ServerCommandSource> context) {
        int result = 0;
        for (ServerPlayerEntity player : getTargetPlayers(context)) {
            result += resetSkin(player);
        }
        return result;
    }
}
