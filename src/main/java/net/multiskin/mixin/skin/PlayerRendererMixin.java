package net.multiskin.mixin.skin;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.util.Identifier;

import net.multiskin.skin.SkinClient;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntityRenderer.class)
public class PlayerRendererMixin {
    @Inject(method = "getTexture", at = @At("HEAD"), cancellable = true)
    private void multiskin$getTexture(AbstractClientPlayerEntity player, CallbackInfoReturnable<Identifier> cir) {
        Identifier customTexture = SkinClient.getSkinTexture(player.getUuid());

        if (customTexture != null) {
            cir.setReturnValue(customTexture);
        }
    }
}