package net.multiskin.skin.screen;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;

import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.MinecraftClient;
import net.multiskin.skin.SkinClient;
import net.multiskin.skin.fastchoose.FastChooseInput;
import net.multiskin.skin.fastchoose.FastChooseScreen;

import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.Set;

public class SkinKeybinds {
    public static KeyBinding OPEN_MENU;
    public static KeyBinding FAST_CHOOSE;
    private static final Set<Integer> PRESSED_KEYS = new HashSet<>();
    private static int refreshTicks;
    private static java.util.List<SkinLibrary.SkinEntry> skins = java.util.List.of();

    public static void initialize() {
        OPEN_MENU = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.multi-skin.skin_menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F12, "multi-skin"));
        FAST_CHOOSE = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.multi-skin.fast_choose", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT, "multi-skin"));
        skins = SkinLibrary.getAllSkins();
    }

    public static void tick(MinecraftClient client) {
        if (client.player == null || client.player.networkHandler == null) {
            PRESSED_KEYS.clear();
            return;
        }

        if (client.currentScreen instanceof FastChooseScreen) {
            return;
        }

        if (client.currentScreen != null) {
            PRESSED_KEYS.clear();
            return;
        }

        if (FAST_CHOOSE.isPressed()) {
            client.setScreen(new FastChooseScreen());
            FastChooseInput.syncMovementKeys(client);
            return;
        }

        if (++refreshTicks >= 20) {
            refreshTicks = 0;
            skins = SkinLibrary.getAllSkins();
        }

        Set<Integer> currentKeys = new HashSet<>();
        long handle = client.getWindow().getHandle();
        for (SkinLibrary.SkinEntry skin : skins) {
            int keyCode = skin.keyCode();
            if (keyCode < 0 || !InputUtil.isKeyPressed(handle, keyCode)) {
                continue;
            }

            currentKeys.add(keyCode);
            if (!PRESSED_KEYS.contains(keyCode)) {
                SkinClient.applySkinSource(skin.url(), skin.model(), skin.heightCm(), skin.removedPartsMask());
                break;
            }
        }

        PRESSED_KEYS.retainAll(currentKeys);
        PRESSED_KEYS.addAll(currentKeys);
    }

    public static boolean isFastChooseDown(MinecraftClient client) {
        return FAST_CHOOSE != null && FastChooseInput.isKeyBindingDown(FAST_CHOOSE);
    }

    public static String getKeyName(int keyCode) {
        if (keyCode < 0) {
            return "Aucune";
        }
        return InputUtil.fromKeyCode(keyCode, 0).getLocalizedText().getString();
    }
}