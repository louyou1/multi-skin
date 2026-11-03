package net.multiskin.mixin.skin;

import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.multiskin.skin.screen.SkinScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(net.minecraft.client.gui.screen.option.SkinOptionsScreen.class)
public abstract class SkinOptionsScreenMixin {
    @Inject(method = "addOptions", at = @At("TAIL"))
    private void multiskin$addSkinMenuButton(CallbackInfo ci) {
        Screen parent = (Screen)(Object)this;
        ButtonWidget button = ButtonWidget.builder(Text.translatable("screen.multi-skin.open_skin_menu"), clicked -> MinecraftClient.getInstance().setScreen(new SkinScreen(parent)))
                .width(150).build();
        ((GameOptionsScreenAccessor) parent).multiskin$getBody().addAll(List.of(button));
    }
}