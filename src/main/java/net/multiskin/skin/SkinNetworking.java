package net.multiskin.skin;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.multiskin.MultiSkinMod;

import java.util.UUID;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.io.ByteArrayInputStream;
import java.awt.image.BufferedImage;

import javax.imageio.ImageIO;

public class SkinNetworking {
    public static final int SKIN_CHUNK_SIZE = 24 * 1024;
    private static final int MAX_PENDING_UPLOADS = 8;
    private static final long UPLOAD_TIMEOUT_MS = 60_000L;
    private static final Map<UUID, PendingSkinUpload> PENDING_UPLOADS = new HashMap<>();

    private static final class PendingSkinUpload {
        private final UUID transferId;
        private final String model;
        private final int heightCm;
        private final int removedPartsMask;
        private final byte[] data;
        private final BitSet receivedChunks;
        private final long createdAt = System.currentTimeMillis();
        private final int chunkCount;
        private final long requestId;
        private int receivedCount;

        private PendingSkinUpload(SkinUploadStartPayload payload, long requestId) {
            transferId = payload.transferId();
            model = payload.skinModel();
            heightCm = payload.heightCm();
            removedPartsMask = payload.removedPartsMask();
            data = new byte[payload.totalBytes()];
            receivedChunks = new BitSet(payload.chunkCount());
            chunkCount = payload.chunkCount();
            this.requestId = requestId;
        }
    }

    public record SkinTransferStartPayload(
            UUID playerUuid,
            UUID transferId,
            String skinUrl,
            String skinModel,
            int heightCm,
            int totalBytes,
            int chunkCount
    ) implements CustomPayload {
        public static final CustomPayload.Id<SkinTransferStartPayload> ID =
                new CustomPayload.Id<>(Identifier.of(MultiSkinMod.MOD_ID, "skin_transfer_start"));

        public static final PacketCodec<PacketByteBuf, SkinTransferStartPayload> CODEC = PacketCodec.of(
                (payload, buf) -> {
                    buf.writeUuid(payload.playerUuid());
                    buf.writeUuid(payload.transferId());
                    buf.writeString(payload.skinUrl());
                    buf.writeString(payload.skinModel());
                    buf.writeVarInt(payload.heightCm());
                    buf.writeVarInt(payload.totalBytes());
                    buf.writeVarInt(payload.chunkCount());
                },
                buf -> new SkinTransferStartPayload(
                        buf.readUuid(),
                        buf.readUuid(),
                        buf.readString(),
                        buf.readString(),
                        buf.readVarInt(),
                        buf.readVarInt(),
                        buf.readVarInt()
                )
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record SkinTransferChunkPayload(
            UUID playerUuid,
            UUID transferId,
            int chunkIndex,
            byte[] data
    ) implements CustomPayload {
        public static final CustomPayload.Id<SkinTransferChunkPayload> ID =
                new CustomPayload.Id<>(Identifier.of(MultiSkinMod.MOD_ID, "skin_transfer_chunk"));

        public static final PacketCodec<PacketByteBuf, SkinTransferChunkPayload> CODEC = PacketCodec.of(
                (payload, buf) -> {
                    buf.writeUuid(payload.playerUuid());
                    buf.writeUuid(payload.transferId());
                    buf.writeVarInt(payload.chunkIndex());
                    buf.writeByteArray(payload.data());
                },
                buf -> new SkinTransferChunkPayload(
                        buf.readUuid(),
                        buf.readUuid(),
                        buf.readVarInt(),
                        buf.readByteArray(SKIN_CHUNK_SIZE)
                )
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public static void initialize() {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> server.execute(() -> {
            PendingSkinUpload upload = PENDING_UPLOADS.remove(handler.getPlayer().getUuid());
            if (upload != null) {
                SkinCommand.cancelUploadedSkinRequest(handler.getPlayer().getUuid(), upload.requestId);
            }
        }));

        PayloadTypeRegistry.playS2C().register(SkinTransferStartPayload.ID, SkinTransferStartPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SkinTransferChunkPayload.ID, SkinTransferChunkPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SkinResetPayload.ID, SkinResetPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SkinSyncRequest.ID, SkinSyncRequest.CODEC);
        PayloadTypeRegistry.playC2S().register(SkinRefreshSelfRequest.ID, SkinRefreshSelfRequest.CODEC);
        PayloadTypeRegistry.playC2S().register(SkinApplyRequest.ID, SkinApplyRequest.CODEC);
        PayloadTypeRegistry.playC2S().register(SkinUploadStartPayload.ID, SkinUploadStartPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SkinUploadChunkPayload.ID, SkinUploadChunkPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SkinResetRequest.ID, SkinResetRequest.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(
                SkinSyncRequest.ID,
                (payload, context) -> context.server().execute(() -> sendAllSkins(context.player()))
        );
        ServerPlayNetworking.registerGlobalReceiver(
                SkinRefreshSelfRequest.ID,
                (payload, context) -> context.server().execute(() -> SkinCommand.refreshOwnSkin(context.player()))
        );
        ServerPlayNetworking.registerGlobalReceiver(
                SkinApplyRequest.ID,
                (payload, context) -> context.server().execute(() ->
                        SkinCommand.applySkinFromScreen(
                                context.player(),
                                payload.skinModel(),
                                payload.skinUrl(),
                                payload.heightCm(),
                                payload.removedPartsMask() & 0xF
                        ))
        );
        ServerPlayNetworking.registerGlobalReceiver(
                SkinUploadStartPayload.ID,
                (payload, context) -> context.server().execute(() -> startSkinUpload(context.player(), payload))
        );
        ServerPlayNetworking.registerGlobalReceiver(
                SkinUploadChunkPayload.ID,
                (payload, context) -> context.server().execute(() -> receiveSkinUploadChunk(context.player(), payload))
        );
        ServerPlayNetworking.registerGlobalReceiver(
                SkinResetRequest.ID,
                (payload, context) -> context.server().execute(() ->
                        SkinCommand.resetSkinFromScreen(context.player()))
        );
    }

    private static void startSkinUpload(ServerPlayerEntity player, SkinUploadStartPayload payload) {
        long now = System.currentTimeMillis();
        PENDING_UPLOADS.entrySet().removeIf(entry -> {
            boolean expired = now - entry.getValue().createdAt > UPLOAD_TIMEOUT_MS;
            if (expired) {
                SkinCommand.cancelUploadedSkinRequest(entry.getKey(), entry.getValue().requestId);
            }
            return expired;
        });
        int expectedChunks = (payload.totalBytes() + SKIN_CHUNK_SIZE - 1) / SKIN_CHUNK_SIZE;
        if (payload.totalBytes() <= 0
                || payload.totalBytes() > SkinManager.MAX_SKIN_TRANSFER_BYTES
                || payload.chunkCount() != expectedChunks
                || payload.chunkCount() <= 0
                || !(payload.skinModel().equalsIgnoreCase("slim")
                || payload.skinModel().equalsIgnoreCase("steve"))
                || payload.heightCm() < net.multiskin.skin.size.SizeManager.MIN_HEIGHT_CM
                || payload.heightCm() > net.multiskin.skin.size.SizeManager.MAX_HEIGHT_CM) {
            player.sendMessage(net.minecraft.text.Text.translatable("command.multi-skin.skin.upload_error",
                    "Les informations de transfert de l'image sont invalides."), false);
            return;
        }

        PENDING_UPLOADS.remove(player.getUuid());
        if (PENDING_UPLOADS.size() >= MAX_PENDING_UPLOADS) {
            player.sendMessage(net.minecraft.text.Text.translatable("command.multi-skin.skin.upload_error",
                    "Le serveur reçoit déjà trop d'images."), false);
            return;
        }
        long requestId = SkinCommand.beginUploadedSkinRequest(player.getUuid());
        PENDING_UPLOADS.put(player.getUuid(), new PendingSkinUpload(payload, requestId));
    }

    private static void receiveSkinUploadChunk(ServerPlayerEntity player, SkinUploadChunkPayload payload) {
        PendingSkinUpload upload = PENDING_UPLOADS.get(player.getUuid());
        if (upload == null
                || !upload.transferId.equals(payload.transferId())
                || payload.chunkIndex() < 0
                || payload.chunkIndex() >= upload.chunkCount) {
            return;
        }
        if (System.currentTimeMillis() - upload.createdAt > UPLOAD_TIMEOUT_MS) {
            PENDING_UPLOADS.remove(player.getUuid(), upload);
            SkinCommand.cancelUploadedSkinRequest(player.getUuid(), upload.requestId);
            return;
        }
        if (!SkinCommand.isCurrentUploadedSkinRequest(player.getUuid(), upload.requestId)) {
            PENDING_UPLOADS.remove(player.getUuid(), upload);
            return;
        }

        int offset = payload.chunkIndex() * SKIN_CHUNK_SIZE;
        int expectedLength = Math.min(SKIN_CHUNK_SIZE, upload.data.length - offset);
        if (payload.data().length != expectedLength || upload.receivedChunks.get(payload.chunkIndex())) {
            PENDING_UPLOADS.remove(player.getUuid(), upload);
            SkinCommand.cancelUploadedSkinRequest(player.getUuid(), upload.requestId);
            player.sendMessage(net.minecraft.text.Text.translatable("command.multi-skin.skin.upload_error",
                    "Un bloc de l'image est invalide."), false);
            return;
        }

        System.arraycopy(payload.data(), 0, upload.data, offset, expectedLength);
        upload.receivedChunks.set(payload.chunkIndex());
        upload.receivedCount++;
        if (upload.receivedCount != upload.chunkCount) {
            return;
        }

        PENDING_UPLOADS.remove(player.getUuid(), upload);
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(upload.data));
            if (image == null) {
                throw new SkinManager.SkinException("error.multi-skin.skin.invalid_image");
            }
            SkinCommand.applyUploadedSkin(
                    player, upload.model, upload.heightCm, upload.removedPartsMask,
                    SkinManager.validateSkinImage(image), upload.requestId
            );
        } catch (Exception e) {
            SkinCommand.cancelUploadedSkinRequest(player.getUuid(), upload.requestId);
            player.sendMessage(SkinManager.errorText(e, "command.multi-skin.skin.upload_error")
                    .formatted(net.minecraft.util.Formatting.RED), false);
        }
    }

    public record SkinUploadStartPayload(
            UUID transferId,
            String skinModel,
            int heightCm,
            int removedPartsMask,
            int totalBytes,
            int chunkCount
    ) implements CustomPayload {
        public static final CustomPayload.Id<SkinUploadStartPayload> ID =
                new CustomPayload.Id<>(Identifier.of(MultiSkinMod.MOD_ID, "skin_upload_start"));

        public static final PacketCodec<PacketByteBuf, SkinUploadStartPayload> CODEC = PacketCodec.of(
                (payload, buf) -> {
                    buf.writeUuid(payload.transferId());
                    buf.writeString(payload.skinModel());
                    buf.writeVarInt(payload.heightCm());
                    buf.writeVarInt(payload.removedPartsMask());
                    buf.writeVarInt(payload.totalBytes());
                    buf.writeVarInt(payload.chunkCount());
                },
                buf -> new SkinUploadStartPayload(
                        buf.readUuid(),
                        buf.readString(16),
                        buf.readVarInt(),
                        buf.readVarInt(),
                        buf.readVarInt(),
                        buf.readVarInt()
                )
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record SkinUploadChunkPayload(UUID transferId, int chunkIndex, byte[] data) implements CustomPayload {
        public static final CustomPayload.Id<SkinUploadChunkPayload> ID =
                new CustomPayload.Id<>(Identifier.of(MultiSkinMod.MOD_ID, "skin_upload_chunk"));

        public static final PacketCodec<PacketByteBuf, SkinUploadChunkPayload> CODEC = PacketCodec.of(
                (payload, buf) -> {
                    buf.writeUuid(payload.transferId());
                    buf.writeVarInt(payload.chunkIndex());
                    buf.writeByteArray(payload.data());
                },
                buf -> new SkinUploadChunkPayload(
                        buf.readUuid(),
                        buf.readVarInt(),
                        buf.readByteArray(SKIN_CHUNK_SIZE)
                )
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record SkinSyncRequest() implements CustomPayload {
        public static final CustomPayload.Id<SkinSyncRequest> ID =
                new CustomPayload.Id<>(Identifier.of(MultiSkinMod.MOD_ID, "skin_sync_request"));

        public static final PacketCodec<PacketByteBuf, SkinSyncRequest> CODEC = PacketCodec.of(
                (payload, buf) -> {},
                buf -> new SkinSyncRequest()
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record SkinRefreshSelfRequest() implements CustomPayload {
        public static final CustomPayload.Id<SkinRefreshSelfRequest> ID =
                new CustomPayload.Id<>(Identifier.of(MultiSkinMod.MOD_ID, "skin_refresh_self"));

        public static final PacketCodec<PacketByteBuf, SkinRefreshSelfRequest> CODEC = PacketCodec.of(
                (payload, buf) -> {},
                buf -> new SkinRefreshSelfRequest()
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record SkinApplyRequest(String skinUrl, String skinModel, int heightCm, int removedPartsMask)
            implements CustomPayload {
        public static final CustomPayload.Id<SkinApplyRequest> ID =
                new CustomPayload.Id<>(Identifier.of(MultiSkinMod.MOD_ID, "skin_apply_request"));

        public static final PacketCodec<PacketByteBuf, SkinApplyRequest> CODEC = PacketCodec.of(
                (payload, buf) -> {
                    buf.writeString(payload.skinUrl());
                    buf.writeString(payload.skinModel());
                    buf.writeVarInt(payload.heightCm());
                    buf.writeVarInt(payload.removedPartsMask());
                },
                buf -> new SkinApplyRequest(
                        buf.readString(),
                        buf.readString(),
                        buf.readVarInt(),
                        buf.readVarInt()
                )
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record SkinResetRequest() implements CustomPayload {
        public static final CustomPayload.Id<SkinResetRequest> ID =
                new CustomPayload.Id<>(Identifier.of(MultiSkinMod.MOD_ID, "skin_reset_request"));

        public static final PacketCodec<PacketByteBuf, SkinResetRequest> CODEC = PacketCodec.of(
                (payload, buf) -> {},
                buf -> new SkinResetRequest()
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public static void sendSkin(ServerPlayerEntity target, ServerPlayerEntity skinOwner) {
        SkinManager.SkinData skin;

        try {
            skin = SkinManager.loadSkinData(skinOwner);
        } catch (Exception e) {
            MultiSkinMod.LOGGER.warn(
                    "Impossible de charger le skin de {}",
                    skinOwner.getName().getString(),
                    e
            );
            return;
        }

        if (skin == null) {
            return;
        }

        sendSkin(target, skinOwner.getUuid(), skin.url(), skin.model(), skin.heightCm());
    }

    public static void sendSkin(
            ServerPlayerEntity target,
            UUID playerUuid,
            String skinUrl,
            String skinModel,
            int heightCm
    ) {
        try {
            sendSkinTransfer(target, playerUuid, skinUrl, skinModel, heightCm,
                    SkinManager.readDownloadedSkin(playerUuid));
        } catch (Exception e) {
            MultiSkinMod.LOGGER.warn("Impossible d'envoyer le skin de {}", playerUuid, e);
        }
    }

    private static void sendSkinTransfer(
            ServerPlayerEntity target,
            UUID playerUuid,
            String skinUrl,
            String skinModel,
            int heightCm,
            byte[] imageData
    ) {
        if (imageData.length == 0 || imageData.length > SkinManager.MAX_SKIN_TRANSFER_BYTES) {
            MultiSkinMod.LOGGER.warn("Fichier de skin trop volumineux pour {}", playerUuid);
            return;
        }

        UUID transferId = UUID.randomUUID();
        int chunkCount = (imageData.length + SKIN_CHUNK_SIZE - 1) / SKIN_CHUNK_SIZE;
        ServerPlayNetworking.send(target, new SkinTransferStartPayload(
                playerUuid, transferId, skinUrl, skinModel, heightCm, imageData.length, chunkCount
        ));
        for (int chunkIndex = 0; chunkIndex < chunkCount; chunkIndex++) {
            int start = chunkIndex * SKIN_CHUNK_SIZE;
            int end = Math.min(start + SKIN_CHUNK_SIZE, imageData.length);
            ServerPlayNetworking.send(target, new SkinTransferChunkPayload(
                    playerUuid,
                    transferId,
                    chunkIndex,
                    Arrays.copyOfRange(imageData, start, end)
            ));
        }
    }

    public static void broadcastSkin(ServerPlayerEntity player, String skinUrl, String skinModel, int heightCm) {
        if (player.getServer() == null) {
            return;
        }

        try {
            byte[] imageData = SkinManager.readDownloadedSkin(player.getUuid());
            for (ServerPlayerEntity target : player.getServer().getPlayerManager().getPlayerList()) {
                sendSkinTransfer(target, player.getUuid(), skinUrl, skinModel, heightCm, imageData);
            }
        } catch (Exception e) {
            MultiSkinMod.LOGGER.warn("Impossible de diffuser le skin de {}", player.getUuid(), e);
        }
    }

    public static void sendAllSkins(ServerPlayerEntity target) {
        if (target.getServer() == null) {
            return;
        }
        for (ServerPlayerEntity player : target.getServer().getPlayerManager().getPlayerList()) {
            sendSkin(target, player);
        }
    }

    public record SkinResetPayload(UUID playerUuid) implements CustomPayload {
        public static final CustomPayload.Id<SkinResetPayload> ID = new CustomPayload.Id<>(Identifier.of(MultiSkinMod.MOD_ID, "skin_reset"));

        public static final PacketCodec<PacketByteBuf, SkinResetPayload> CODEC = PacketCodec.of(
                (payload, buf) -> {
                    buf.writeUuid(payload.playerUuid());
                },

                buf -> new SkinResetPayload(buf.readUuid())
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public static void broadcastReset(ServerPlayerEntity player) {
        if (player.getServer() == null) {
            return;
        }
        for (ServerPlayerEntity target : player.getServer().getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(target, new SkinResetPayload(player.getUuid()));
        }
    }
}