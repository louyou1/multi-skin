package net.multiskin.mixin.skin;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.util.SkinTextures;

import net.multiskin.skin.SkinClient;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayerEntity.class)
public class SkinTexturesMixin {
    @Inject(method = "getSkinTextures", at = @At("RETURN"), cancellable = true)
    private void multiskin$customSkinTextures(CallbackInfoReturnable<SkinTextures> cir) {
        AbstractClientPlayerEntity player = (AbstractClientPlayerEntity) (Object) this;
        cir.setReturnValue(SkinClient.getSkinTextures(player.getUuid(), cir.getReturnValue()));
    }
}