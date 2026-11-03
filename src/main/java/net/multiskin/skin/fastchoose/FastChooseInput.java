package net.multiskin.skin.fastchoose;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;

import java.util.HashSet;
import java.util.Set;

public final class FastChooseInput {
    private static final Set<PressedKey> PRESSED_KEYS = new HashSet<>();

    private record PressedKey(int keyCode, int scanCode) {}

    private FastChooseInput() {}

    public static void onKeyEvent(int key, int scanCode, int action) {
        PressedKey inputKey = new PressedKey(key, scanCode);
        if (action == 1) {
            PRESSED_KEYS.add(inputKey);
        } else if (action == 0) {
            PRESSED_KEYS.remove(inputKey);
        }
    }

    public static boolean isKeyBindingDown(KeyBinding binding) {
        return PRESSED_KEYS.stream().anyMatch(key -> binding.matchesKey(key.keyCode(), key.scanCode()));
    }

    public static void syncMovementKeys(MinecraftClient client) {
        for (KeyBinding binding : getMovementKeys(client)) {
            boolean pressed = PRESSED_KEYS.stream().anyMatch(key -> binding.matchesKey(key.keyCode(), key.scanCode()));
            binding.setPressed(pressed);
        }
    }

    private static KeyBinding[] getMovementKeys(MinecraftClient client) {
        return new KeyBinding[] {
                client.options.forwardKey,
                client.options.backKey,
                client.options.leftKey,
                client.options.rightKey,
                client.options.jumpKey,
                client.options.sneakKey
        };
    }
}
