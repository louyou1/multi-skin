package net.multiskin.skin;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.util.Identifier;
import net.multiskin.MultiSkinMod;
import net.multiskin.skin.size.SizeManager;

import java.io.ByteArrayInputStream;
import java.util.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;

public class SkinClient {
    private static final long SKIN_REFRESH_INTERVAL_MS = 30 * 60 * 1000L;
    private static final Map<UUID, Identifier> SKIN_TEXTURES = new ConcurrentHashMap<>();
    private static final Map<UUID, CachedSkinTextures> SKIN_TEXTURE_CACHE = new ConcurrentHashMap<>();
    private static final Map<UUID, String> SKIN_MODELS = new ConcurrentHashMap<>();
    private static final Map<UUID, String> SKIN_URLS = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> SKIN_HEIGHTS = new ConcurrentHashMap<>();
    private static final Map<UUID, LocalSkinSource> LOCAL_SKIN_SOURCES = new ConcurrentHashMap<>();
    private static final Map<UUID, LocalSkinSource> PENDING_LOCAL_SKIN_SOURCES = new ConcurrentHashMap<>();
    private static final Map<UUID, PendingSkinTransfer> PENDING_SKIN_TRANSFERS = new HashMap<>();

    private static final Map<Path, Identifier> SKIN_THUMBNAILS = new HashMap<>();

    private static final Set<Path> THUMBNAILS_LOADING = new HashSet<>();
    private static final Set<Path> THUMBNAILS_INVALID = new HashSet<>();

    private static Identifier warningTexture;

    private static Identifier previewTexture;
    private static String previewModel;
    private static int previewRequestId = 0;
    private static int applyRequestId = 0;
    private static long nextAutoRefreshAt;

    private static final long SKIN_TRANSFER_TIMEOUT_MS = 60_000L;

    private static final class PendingSkinTransfer {
        private final UUID transferId;
        private final byte[] data;
        private final BitSet receivedChunks;
        private final String url;
        private final String model;
        private final int heightCm;
        private final int chunkCount;
        private final long createdAt = System.currentTimeMillis();
        private int receivedCount;

        private PendingSkinTransfer(SkinNetworking.SkinTransferStartPayload payload) {
            transferId = payload.transferId();
            data = new byte[payload.totalBytes()];
            receivedChunks = new BitSet(payload.chunkCount());
            url = payload.skinUrl();
            model = payload.skinModel();
            heightCm = payload.heightCm();
            chunkCount = payload.chunkCount();
        }
    }

    private record CachedSkinTextures(SkinTexturesKey key, SkinTextures value) {}

    private record LocalSkinSource(String source, String model, int heightCm) {}

    private record SkinTexturesKey(
            Identifier texture,
            String textureUrl,
            Identifier capeTexture,
            Identifier elytraTexture,
            SkinTextures.Model model,
            boolean secure
    ) {}

    public static void initialize() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            clearConnectedPlayerSkins();
            nextAutoRefreshAt = System.currentTimeMillis() + SKIN_REFRESH_INTERVAL_MS;
            ClientPlayNetworking.send(new SkinNetworking.SkinSyncRequest());
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            nextAutoRefreshAt = 0;
            clearConnectedPlayerSkins();
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            long now = System.currentTimeMillis();
            PENDING_SKIN_TRANSFERS.entrySet().removeIf(
                    entry -> now - entry.getValue().createdAt > SKIN_TRANSFER_TIMEOUT_MS
            );
            if (client.player != null
                    && client.getNetworkHandler() != null
                    && nextAutoRefreshAt > 0
                    && now >= nextAutoRefreshAt) {
                refreshSkins();
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(
                SkinNetworking.SkinTransferStartPayload.ID,
                (payload, context) -> context.client().execute(() -> startSkinTransfer(payload))
        );
        ClientPlayNetworking.registerGlobalReceiver(
                SkinNetworking.SkinTransferChunkPayload.ID,
                (payload, context) -> context.client().execute(() -> receiveSkinTransferChunk(payload))
        );

        ClientPlayNetworking.registerGlobalReceiver(SkinNetworking.SkinResetPayload.ID, (payload, context) -> {
                    context.client().execute(() -> {
                        removeSkin(payload.playerUuid());
                        MultiSkinMod.LOGGER.debug("Skin Minecraft restauré pour {}", payload.playerUuid());
                    });
                }
        );
    }

    public static void refreshSkins() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.getNetworkHandler() == null) {
            return;
        }

        nextAutoRefreshAt = System.currentTimeMillis() + SKIN_REFRESH_INTERVAL_MS;
        ClientPlayNetworking.send(new SkinNetworking.SkinSyncRequest());
        ClientPlayNetworking.send(new SkinNetworking.SkinRefreshSelfRequest());
    }

    private static void startSkinTransfer(SkinNetworking.SkinTransferStartPayload payload) {
        int expectedChunkCount = (payload.totalBytes() + SkinNetworking.SKIN_CHUNK_SIZE - 1)
                / SkinNetworking.SKIN_CHUNK_SIZE;
        if (payload.totalBytes() <= 0
                || payload.totalBytes() > SkinManager.MAX_SKIN_TRANSFER_BYTES
                || payload.chunkCount() != expectedChunkCount
                || payload.chunkCount() <= 0
                || payload.skinUrl().length() > 2048
                || !(payload.skinModel().equalsIgnoreCase("slim")
                || payload.skinModel().equalsIgnoreCase("steve"))) {
            MultiSkinMod.LOGGER.warn("Le serveur a envoyé des métadonnées de skin invalides pour {}", payload.playerUuid());
            return;
        }

        UUID playerUuid = payload.playerUuid();
        String appliedUrl = payload.skinUrl();
        if (SkinManager.UPLOADED_SKIN_SOURCE.equals(appliedUrl)) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null && client.player.getUuid().equals(playerUuid)) {
                LocalSkinSource pendingSource = PENDING_LOCAL_SKIN_SOURCES.remove(playerUuid);
                if (pendingSource != null) {
                    if (pendingSource.model().equalsIgnoreCase(payload.skinModel())
                            && pendingSource.heightCm() == payload.heightCm()) {
                        LOCAL_SKIN_SOURCES.put(playerUuid, pendingSource);
                    } else {
                        LOCAL_SKIN_SOURCES.remove(playerUuid);
                    }
                }
                LocalSkinSource localSource = LOCAL_SKIN_SOURCES.get(playerUuid);
                if (localSource != null
                        && localSource.model().equalsIgnoreCase(payload.skinModel())
                        && localSource.heightCm() == payload.heightCm()) {
                    appliedUrl = localSource.source();
                }
            }
        } else {
            LOCAL_SKIN_SOURCES.remove(playerUuid);
            PENDING_LOCAL_SKIN_SOURCES.remove(playerUuid);
        }
        SKIN_URLS.put(playerUuid, appliedUrl);
        SKIN_MODELS.put(playerUuid, payload.skinModel());
        SKIN_HEIGHTS.put(playerUuid, payload.heightCm());
        PENDING_SKIN_TRANSFERS.put(playerUuid, new PendingSkinTransfer(payload));
    }

    private static void receiveSkinTransferChunk(SkinNetworking.SkinTransferChunkPayload payload) {
        PendingSkinTransfer transfer = PENDING_SKIN_TRANSFERS.get(payload.playerUuid());
        if (transfer == null
                || !transfer.transferId.equals(payload.transferId())
                || payload.chunkIndex() < 0
                || payload.chunkIndex() >= transfer.chunkCount) {
            return;
        }

        int offset = payload.chunkIndex() * SkinNetworking.SKIN_CHUNK_SIZE;
        int expectedLength = Math.min(SkinNetworking.SKIN_CHUNK_SIZE, transfer.data.length - offset);
        if (payload.data().length != expectedLength) {
            PENDING_SKIN_TRANSFERS.remove(payload.playerUuid(), transfer);
            MultiSkinMod.LOGGER.warn("Bloc de skin invalide reçu pour {}", payload.playerUuid());
            return;
        }
        if (transfer.receivedChunks.get(payload.chunkIndex())) {
            return;
        }

        System.arraycopy(payload.data(), 0, transfer.data, offset, expectedLength);
        transfer.receivedChunks.set(payload.chunkIndex());
        transfer.receivedCount++;

        if (transfer.receivedCount == transfer.chunkCount) {
            PENDING_SKIN_TRANSFERS.remove(payload.playerUuid(), transfer);
            loadTransferredSkin(payload.playerUuid(), transfer);
        }
    }

    private static void loadTransferredSkin(UUID playerUuid, PendingSkinTransfer transfer) {
        try {
            NativeImage nativeImage = NativeImage.read(new ByteArrayInputStream(transfer.data));
            if (nativeImage.getWidth() != nativeImage.getHeight()
                    || nativeImage.getWidth() < 64
                    || nativeImage.getWidth() > 1024
                    || (nativeImage.getWidth() & (nativeImage.getWidth() - 1)) != 0) {
                nativeImage.close();
                throw new IllegalArgumentException("Dimensions de skin invalides.");
            }

            NativeImageBackedTexture texture = new NativeImageBackedTexture(nativeImage);
            MinecraftClient client = MinecraftClient.getInstance();
            Identifier textureId = client.getTextureManager().registerDynamicTexture(
                    "multiskin_skin_" + playerUuid,
                    texture
            );
            Identifier oldTexture = SKIN_TEXTURES.put(playerUuid, textureId);
            SKIN_TEXTURE_CACHE.remove(playerUuid);
            if (oldTexture != null) {
                client.getTextureManager().destroyTexture(oldTexture);
            }
            MultiSkinMod.LOGGER.debug("Skin reçu du serveur : {} ({})", playerUuid, transfer.url);
        } catch (Exception e) {
            MultiSkinMod.LOGGER.error("Impossible de charger le skin reçu du serveur pour {}", playerUuid, e);
        }
    }

    private static void clearConnectedPlayerSkins() {
        MinecraftClient client = MinecraftClient.getInstance();
        applyRequestId++;
        for (Identifier texture : SKIN_TEXTURES.values()) {
            client.getTextureManager().destroyTexture(texture);
        }

        SKIN_TEXTURES.clear();
        SKIN_TEXTURE_CACHE.clear();
        SKIN_MODELS.clear();
        SKIN_URLS.clear();
        SKIN_HEIGHTS.clear();
        PENDING_SKIN_TRANSFERS.clear();
        PENDING_LOCAL_SKIN_SOURCES.clear();
    }

    public static void loadPreviewSkin(String url, String model) {
        int requestId = ++previewRequestId;
        previewModel = model;

        Thread.startVirtualThread(() -> {
            try {
                BufferedImage image = SkinImageSource.load(url);
                byte[] imageData = SkinImageSource.encodePng(image);

                MinecraftClient.getInstance().execute(() -> {
                    if (requestId != previewRequestId) {
                        return;
                    }

                    try {
                        NativeImage nativeImage = NativeImage.read(new ByteArrayInputStream(imageData));
                        NativeImageBackedTexture texture = new NativeImageBackedTexture(nativeImage);
                        Identifier oldTexture = previewTexture;
                        previewTexture = MinecraftClient.getInstance().getTextureManager().registerDynamicTexture("multiskin_preview_skin", texture);

                        if (oldTexture != null) {
                            MinecraftClient.getInstance().getTextureManager().destroyTexture(oldTexture);
                        }

                    } catch (Exception e) {
                        MultiSkinMod.LOGGER.error("Impossible de créer la texture de preview.", e);
                    }
                });

            } catch (Exception e) {
                if (requestId == previewRequestId) {
                    MultiSkinMod.LOGGER.error("Erreur lors du téléchargement de la preview.", e);
                }
            }
        });
    }

    public static void applySkinSource(String source, String model, int heightCm, int removedPartsMask) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.getNetworkHandler() == null) {
            return;
        }
        int requestId = ++applyRequestId;
        UUID playerUuid = client.player.getUuid();
        PENDING_LOCAL_SKIN_SOURCES.remove(playerUuid);
        if (SkinImageSource.isRemote(source)) {
            ClientPlayNetworking.send(new SkinNetworking.SkinApplyRequest(
                    source, model, heightCm, removedPartsMask & 0xF));
            return;
        }

        Thread.startVirtualThread(() -> {
            try {
                BufferedImage image = SkinImageSource.load(source);
                byte[] imageData = SkinImageSource.encodePng(image);
                client.execute(() -> {
                    if (requestId != applyRequestId
                            || client.player == null
                            || client.getNetworkHandler() == null) {
                        return;
                    }
                    PENDING_LOCAL_SKIN_SOURCES.put(
                            playerUuid, new LocalSkinSource(source, model, heightCm));
                    UUID transferId = UUID.randomUUID();
                    int chunkCount = (imageData.length + SkinNetworking.SKIN_CHUNK_SIZE - 1)
                            / SkinNetworking.SKIN_CHUNK_SIZE;
                    ClientPlayNetworking.send(new SkinNetworking.SkinUploadStartPayload(
                            transferId,
                            model,
                            heightCm,
                            removedPartsMask & 0xF,
                            imageData.length,
                            chunkCount
                    ));
                    for (int chunkIndex = 0; chunkIndex < chunkCount; chunkIndex++) {
                        int start = chunkIndex * SkinNetworking.SKIN_CHUNK_SIZE;
                        int end = Math.min(start + SkinNetworking.SKIN_CHUNK_SIZE, imageData.length);
                        ClientPlayNetworking.send(new SkinNetworking.SkinUploadChunkPayload(
                                transferId,
                                chunkIndex,
                                Arrays.copyOfRange(imageData, start, end)
                        ));
                    }
                });
            } catch (Exception e) {
                client.execute(() -> {
                    if (requestId == applyRequestId && client.player != null) {
                        PENDING_LOCAL_SKIN_SOURCES.remove(playerUuid);
                        client.player.sendMessage(
                                net.minecraft.text.Text.translatable(
                                        "command.multi-skin.skin.upload_error", e.getMessage()),
                                false
                        );
                    }
                });
                MultiSkinMod.LOGGER.warn("Impossible de charger l'image locale du skin {}", source, e);
            }
        });
    }

    public static void clearPreview() {
        previewRequestId++;
        previewModel = null;

        if (previewTexture != null) {
            MinecraftClient.getInstance().getTextureManager().destroyTexture(previewTexture);
            previewTexture = null;
        }
    }

    public static Identifier getSkinTexture(UUID playerUuid) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (previewTexture != null && client.player != null && client.player.getUuid().equals(playerUuid)) {
            return previewTexture;
        }
        return SKIN_TEXTURES.get(playerUuid);
    }

    public static String getSkinModel(UUID playerUuid) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (previewModel != null && client.player != null && client.player.getUuid().equals(playerUuid)) {
            return previewModel;
        }
        return SKIN_MODELS.get(playerUuid);
    }

    public static String getAppliedSkinModel(UUID playerUuid) {
        return SKIN_MODELS.get(playerUuid);
    }

    public static String getSkinUrl(UUID playerUuid) {
        return SKIN_URLS.get(playerUuid);
    }

    public static int getSkinHeightCm(UUID playerUuid) {
        return SKIN_HEIGHTS.getOrDefault(playerUuid, SizeManager.DEFAULT_HEIGHT_CM);
    }

    public static SkinTextures getSkinTextures(UUID playerUuid, SkinTextures original) {
        Identifier customTexture = getSkinTexture(playerUuid);
        String customModel = getSkinModel(playerUuid);

        if (customTexture == null && customModel == null) {
            return original;
        }

        SkinTextures.Model model = customModel == null
                ? original.model()
                : customModel.equalsIgnoreCase("slim")
                ? SkinTextures.Model.SLIM
                : SkinTextures.Model.WIDE;
        SkinTexturesKey key = new SkinTexturesKey(
                customTexture != null ? customTexture : original.texture(),
                original.textureUrl(),
                original.capeTexture(),
                original.elytraTexture(),
                model,
                original.secure()
        );
        CachedSkinTextures cached = SKIN_TEXTURE_CACHE.get(playerUuid);

        if (cached != null && cached.key().equals(key)) {
            return cached.value();
        }

        SkinTextures result = new SkinTextures(
                key.texture(),
                key.textureUrl(),
                key.capeTexture(),
                key.elytraTexture(),
                key.model(),
                key.secure()
        );
        SKIN_TEXTURE_CACHE.put(playerUuid, new CachedSkinTextures(key, result));
        return result;
    }

    public static void removeSkin(UUID playerUuid) {
        Identifier texture = SKIN_TEXTURES.remove(playerUuid);
        PENDING_SKIN_TRANSFERS.remove(playerUuid);
        SKIN_TEXTURE_CACHE.remove(playerUuid);
        SKIN_MODELS.remove(playerUuid);
        SKIN_URLS.remove(playerUuid);
        SKIN_HEIGHTS.remove(playerUuid);
        LOCAL_SKIN_SOURCES.remove(playerUuid);
        PENDING_LOCAL_SKIN_SOURCES.remove(playerUuid);

        if (texture != null) {
            MinecraftClient.getInstance().getTextureManager().destroyTexture(texture);
        }
    }

    public static void removeThumbnail(Path skinFile) {
        THUMBNAILS_LOADING.remove(skinFile);
        THUMBNAILS_INVALID.remove(skinFile);

        Identifier texture = SKIN_THUMBNAILS.remove(skinFile);

        if (texture != null) {
            MinecraftClient.getInstance().getTextureManager().destroyTexture(texture);
        }
    }

    public static Identifier getSkinThumbnail(Path skinFile, String url) {
        Identifier cached =
                SKIN_THUMBNAILS.get(skinFile);

        if (cached != null) {
            return cached;
        }

        if (THUMBNAILS_INVALID.contains(skinFile)) {
            return getWarningTexture();
        }

        if (!THUMBNAILS_LOADING.add(skinFile)) {
            return null;
        }

        Thread.startVirtualThread(() -> {
            try {
                BufferedImage image = SkinImageSource.load(url);
                BufferedImage thumbnail = createHeadThumbnail(image);

                MinecraftClient.getInstance().execute(() -> {
                    try {
                        NativeImage nativeImage = NativeImage.read(toPngBytes(thumbnail));
                        NativeImageBackedTexture texture = new NativeImageBackedTexture(nativeImage);
                        Identifier textureId = MinecraftClient.getInstance().getTextureManager().registerDynamicTexture("multiskin_skin_thumbnail", texture);

                        SKIN_THUMBNAILS.put(skinFile, textureId);
                        THUMBNAILS_LOADING.remove(skinFile);

                    } catch (Exception e) {
                        thumbnailFailed(skinFile);
                    }
                });

            } catch (Exception e) {
                thumbnailFailed(skinFile);
            }
        });
        return null;
    }

    private static void thumbnailFailed(Path skinFile) {
        MinecraftClient.getInstance().execute(() -> {
            THUMBNAILS_LOADING.remove(skinFile);
            THUMBNAILS_INVALID.add(skinFile);
        });
    }

    private static BufferedImage createHeadThumbnail(BufferedImage skin) {
        int size = skin.getWidth();
        int headSize = size / 8;

        BufferedImage thumbnail = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);

        java.awt.Graphics2D graphics = thumbnail.createGraphics();
        graphics.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        int x = size / 8;
        int y = size / 8;
        graphics.drawImage(skin, 0, 0, 64, 64, x, y, x + headSize, y + headSize, null);

        int overlayX = size / 8 * 5;
        graphics.drawImage(skin, 0, 0, 64, 64, overlayX, y, overlayX + headSize, y + headSize, null);
        graphics.dispose();

        return thumbnail;
    }

    private static byte[] toPngBytes(BufferedImage image) throws Exception {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        ImageIO.write(image, "png", output);

        return output.toByteArray();
    }

    private static Identifier getWarningTexture() {
        if (warningTexture != null) {
            return warningTexture;
        }
        try {
            BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);

            java.awt.Graphics2D graphics = image.createGraphics();

            graphics.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);

            graphics.setColor(new java.awt.Color(255, 200, 40));
            graphics.fillRect(0, 0, 32, 32);

            graphics.setColor(new java.awt.Color(40, 40, 40));

            int[] xPoints = {16, 4, 28};
            int[] yPoints = {3, 28, 28};

            graphics.fillPolygon(xPoints, yPoints, 3);
            graphics.setColor(java.awt.Color.WHITE);
            graphics.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD, 18));
            graphics.drawString("!", 14, 23);
            graphics.dispose();

            NativeImage nativeImage = NativeImage.read(toPngBytes(image));
            NativeImageBackedTexture texture = new NativeImageBackedTexture(nativeImage);

            warningTexture = MinecraftClient.getInstance().getTextureManager().registerDynamicTexture("multiskin_skin_warning", texture);

            return warningTexture;

        } catch (Exception e) {
            MultiSkinMod.LOGGER.error("Impossible de créer la texture d'avertissement.", e);
            return null;
        }
    }

    public static boolean isThumbnailValid(Path skinFile) {
        return !THUMBNAILS_INVALID.contains(skinFile);
    }
}