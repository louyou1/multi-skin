package net.multiskin.mixin.fastchoose;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Keyboard;
import net.multiskin.skin.fastchoose.FastChooseInput;
import net.multiskin.skin.fastchoose.FastChooseScreen;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
public abstract class FastChooseKeyboardMixin {
    @Shadow
    @Final
    private MinecraftClient client;

    @Inject(method = "onKey(JIIII)V", at = @At("HEAD"))
    private void multiskin$trackMovementKeys(long window, int key, int scanCode, int action, int modifiers, CallbackInfo ci) {
        if (action != GLFW.GLFW_PRESS && action != GLFW.GLFW_RELEASE) {
            return;
        }

        FastChooseInput.onKeyEvent(key, scanCode, action);
        if (client.currentScreen instanceof FastChooseScreen) {
            FastChooseInput.syncMovementKeys(client);
        }
    }
}