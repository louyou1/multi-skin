package net.multiskin.skin;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

public final class SkinImageSource {
    private SkinImageSource() {}

    public static boolean isRemote(String source) {
        return source.regionMatches(true, 0, "http://", 0, 7)
                || source.regionMatches(true, 0, "https://", 0, 8);
    }

    public static BufferedImage load(String source) throws IOException, InterruptedException {
        if (isRemote(source)) {
            return SkinManager.validateSkinUrl(source);
        }

        Path imagePath = toPath(source);
        long size = Files.size(imagePath);
        if (size <= 0 || size > SkinManager.MAX_SKIN_TRANSFER_BYTES) {
            throw new IOException("Le fichier image doit faire moins de 8 Mio.");
        }
        byte[] data = Files.readAllBytes(imagePath);

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(data));
        if (image == null) {
            throw new SkinManager.SkinException("error.multi-skin.skin.invalid_image");
        }
        return SkinManager.validateSkinImage(image);
    }

    public static byte[] encodePng(BufferedImage image) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", output)) {
            throw new IOException("Impossible d'encoder l'image du skin.");
        }
        byte[] data = output.toByteArray();
        if (data.length == 0 || data.length > SkinManager.MAX_SKIN_TRANSFER_BYTES) {
            throw new IOException("L'image du skin dépasse la limite de transfert de 8 Mio.");
        }
        return data;
    }

    public static String normalizeForStorage(String source) throws IOException {
        if (isRemote(source)) {
            return source;
        }
        return toPath(source).toAbsolutePath().normalize().toString();
    }

    private static Path toPath(String source) throws IOException {
        try {
            if (source.regionMatches(true, 0, "file:", 0, 5)) {
                return Path.of(URI.create(source));
            }
            return Path.of(source);
        } catch (IllegalArgumentException e) {
            throw new IOException("Le chemin de l'image locale n'est pas valide.", e);
        }
    }
}
