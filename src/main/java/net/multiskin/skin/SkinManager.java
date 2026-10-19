package net.multiskin.skin;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.multiskin.config.MultiSkinConfig;
import net.multiskin.skin.size.SizeManager;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.InetAddress;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.imageio.ImageIO;

public class SkinManager {
    static final long MAX_IMAGE_BYTES = 128L * 1024L * 1024L;
    public static final int MAX_SKIN_TRANSFER_BYTES = 8 * 1024 * 1024;
    public static final String UPLOADED_SKIN_SOURCE = "multiskin-client-upload";

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private static final Path SKIN_FOLDER = Path.of("config", "multi-skin", "skins");
    private static final Map<UUID, SkinData> SKIN_DATA_CACHE = new ConcurrentHashMap<>();

    public record SkinData(String url, String model, int heightCm) {}

    public static final class SkinException extends IOException {
        private final String translationKey;
        private final Object[] arguments;

        public SkinException(String translationKey, Object... arguments) {
            this.translationKey = translationKey;
            this.arguments = arguments;
        }

        public String translationKey() {
            return translationKey;
        }

        public Object[] arguments() {
            return arguments;
        }
    }

    public static MutableText errorText(Exception exception, String fallbackKey) {
        if (exception instanceof SkinException skinException) {
            return Text.translatable(skinException.translationKey(), skinException.arguments());
        }
        return Text.translatable(fallbackKey, exception.getMessage());
    }

    public static void saveDownloadedSkin(UUID playerUuid, BufferedImage image) throws IOException {
        Files.createDirectories(SKIN_FOLDER);
        Path skinFile = SKIN_FOLDER.resolve(playerUuid + ".png");
        if (!ImageIO.write(image, "png", skinFile.toFile())) {
            throw new IOException("Impossible d'enregistrer l'image du skin.");
        }
    }

    public static boolean hasDownloadedSkin(UUID playerUuid) {
        return Files.exists(SKIN_FOLDER.resolve(playerUuid + ".png"));
    }

    public static byte[] readDownloadedSkin(UUID playerUuid) throws IOException {
        Path skinFile = SKIN_FOLDER.resolve(playerUuid + ".png");
        long size = Files.size(skinFile);
        if (size <= 0 || size > MAX_SKIN_TRANSFER_BYTES) {
            throw new IOException("Le fichier du skin dépasse la taille de transfert autorisée.");
        }
        return Files.readAllBytes(skinFile);
    }

    public static BufferedImage validateSkinUrl(String url) throws IOException, InterruptedException {
        URI uri;

        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new SkinException("error.multi-skin.skin.invalid_url");
        }

        if (!isAllowedRemoteUri(uri)) {
            throw new SkinException("error.multi-skin.skin.public_url");
        }

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(fetchImageBytes(uri)));

        if (image == null) {
            throw new SkinException("error.multi-skin.skin.invalid_image");
        }

        return validateSkinImage(image);
    }

    public static BufferedImage validateSkinImage(BufferedImage image) throws IOException {
        int width = image.getWidth();
        int height = image.getHeight();

        if (width != height
                || !MultiSkinConfig.isSupportedSkinSize(width)
                || width > MultiSkinConfig.getMaxSkinSize()) {
            throw new SkinException(
                    "error.multi-skin.skin.invalid_image_size",
                    MultiSkinConfig.getMaxSkinSize()
            );
        }

        return image;
    }

    public static byte[] fetchImageBytes(String url) throws IOException, InterruptedException {
        URI uri;

        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new SkinException("error.multi-skin.skin.invalid_url");
        }

        if (!isAllowedRemoteUri(uri)) {
            throw new SkinException("error.multi-skin.skin.public_url");
        }

        return fetchImageBytes(uri);
    }

    static boolean isAllowedRemoteUri(URI uri) throws IOException {
        String scheme = uri.getScheme();
        String host = uri.getHost();

        if (scheme == null
                || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))
                || host == null
                || host.isBlank()) {
            return false;
        }

        for (InetAddress address : InetAddress.getAllByName(host)) {
            if (address.isAnyLocalAddress()
                    || address.isLoopbackAddress()
                    || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress()
                    || address.isMulticastAddress()) {
                return false;
            }
        }

        return true;
    }

    private static byte[] fetchImageBytes(URI uri) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
        HttpResponse<InputStream> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() != 200) {
            response.body().close();
            throw new SkinException("error.multi-skin.skin.http_status", response.statusCode());
        }

        try (InputStream input = response.body();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            long total = 0;
            int read;

            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > MAX_IMAGE_BYTES) {
                    throw new SkinException("error.multi-skin.skin.image_too_large");
                }
                output.write(buffer, 0, read);
            }

            return output.toByteArray();
        }
    }

    public static void saveSkinData(UUID playerUuid, String url, String model, int heightCm) throws IOException {
        Files.createDirectories(SKIN_FOLDER);
        Path file = SKIN_FOLDER.resolve(playerUuid + ".json");

        String json = """
                {
                  "url": "%s",
                  "model": "%s",
                  "heightCm": %d
                }
                """.formatted(escapeJson(url), escapeJson(model), heightCm);

        Files.writeString(file, json);
        SKIN_DATA_CACHE.put(playerUuid, new SkinData(url, model, heightCm));
    }

    public static SkinData loadSkinData(ServerPlayerEntity player) throws IOException {
        return loadSkinData(player.getUuid());
    }

    public static SkinData loadSkinData(UUID playerUuid) throws IOException {
        SkinData cached = SKIN_DATA_CACHE.get(playerUuid);
        if (cached != null) {
            return cached;
        }

        Path file = SKIN_FOLDER.resolve(playerUuid + ".json");

        if (!Files.exists(file)) {
            return null;
        }

        String json = Files.readString(file);
        String url = extract(json, "url");
        String model = extract(json, "model");
        int heightCm = extractInteger(json, "heightCm", SizeManager.DEFAULT_HEIGHT_CM);

        if (url == null || model == null) {
            return null;
        }

        SkinData skinData = new SkinData(url, model, heightCm);
        SKIN_DATA_CACHE.put(playerUuid, skinData);
        return skinData;
    }

    private static String extract(String json, String key) {
        String search = "\"" + key + "\"";

        int keyStart = json.indexOf(search);

        if (keyStart == -1) {
            return null;
        }

        int colon = json.indexOf(':', keyStart + search.length());
        int firstQuote = json.indexOf('"', colon + 1);
        int secondQuote = json.indexOf('"', firstQuote + 1);

        if (firstQuote == -1 || secondQuote == -1) {
            return null;
        }

        return json.substring(firstQuote + 1, secondQuote);
    }

    private static int extractInteger(String json, String key, int fallback) {
        String search = "\"" + key + "\"";
        int keyStart = json.indexOf(search);

        if (keyStart == -1) {
            return fallback;
        }

        int colon = json.indexOf(':', keyStart + search.length());
        if (colon == -1) {
            return fallback;
        }

        int start = colon + 1;
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) {
            start++;
        }

        int end = start;
        while (end < json.length() && Character.isDigit(json.charAt(end))) {
            end++;
        }

        if (start == end) {
            return fallback;
        }

        try {
            return Math.clamp(Integer.parseInt(json.substring(start, end)),
                    SizeManager.MIN_HEIGHT_CM,
                    SizeManager.MAX_HEIGHT_CM);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String escapeJson(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }

    public static void deleteSkinData(ServerPlayerEntity player) throws IOException {
        deleteSkinData(player.getUuid());
    }

    public static void deleteSkinData(UUID playerUuid) throws IOException {
        Path pngFile = SKIN_FOLDER.resolve(playerUuid + ".png");
        Path jsonFile = SKIN_FOLDER.resolve(playerUuid + ".json");

        Files.deleteIfExists(pngFile);
        Files.deleteIfExists(jsonFile);
        SKIN_DATA_CACHE.remove(playerUuid);
    }
}