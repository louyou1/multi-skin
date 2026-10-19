package net.multiskin.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class MultiSkinConfig {
    public static final float DEFAULT_MOVEMENT_SPEED_EFFECT = 0.25f;
    public static final float DEFAULT_REACH_EFFECT = 0.35f;
    public static final float MIN_EFFECT = 0.0f;
    public static final float MAX_EFFECT = 2.0f;

    private static final Path CONFIG_FILE = Path.of("config", "multi-skin", "config.json");
    private static float movementSpeedEffect = DEFAULT_MOVEMENT_SPEED_EFFECT;
    private static float reachEffect = DEFAULT_REACH_EFFECT;

    private MultiSkinConfig() {
    }

    public static synchronized void load() throws IOException {
        if (!Files.exists(CONFIG_FILE)) {
            save();
            return;
        }

        String json = Files.readString(CONFIG_FILE);
        movementSpeedEffect = readEffect(json, "movementSpeedEffect", DEFAULT_MOVEMENT_SPEED_EFFECT);
        reachEffect = readEffect(json, "reachEffect", DEFAULT_REACH_EFFECT);
    }

    public static synchronized void save() throws IOException {
        Files.createDirectories(CONFIG_FILE.getParent());
        String json = """
                {
                  "movementSpeedEffect": %s,
                  "reachEffect": %s
                }
                """.formatted(movementSpeedEffect, reachEffect);
        Files.writeString(CONFIG_FILE, json);
    }

    public static synchronized float getMovementSpeedEffect() {
        return movementSpeedEffect;
    }

    public static synchronized float getReachEffect() {
        return reachEffect;
    }

    public static synchronized void setMovementSpeedEffect(float value) throws IOException {
        movementSpeedEffect = value;
        save();
    }

    public static synchronized void setReachEffect(float value) throws IOException {
        reachEffect = value;
        save();
    }

    private static float readEffect(String json, String key, float fallback) {
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
        while (end < json.length()) {
            char character = json.charAt(end);
            if (!Character.isDigit(character) && character != '.' && character != '-') {
                break;
            }
            end++;
        }

        if (start == end) {
            return fallback;
        }

        try {
            return Math.clamp(Float.parseFloat(json.substring(start, end)), MIN_EFFECT, MAX_EFFECT);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
