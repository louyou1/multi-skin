package net.multiskin.mixin.skin;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.SkinTextures;

import net.multiskin.skin.SkinClient;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(PlayerEntityRenderer.class)
public class FirstPersonArmMixin {
    @Redirect(method = "renderArm", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/network/AbstractClientPlayerEntity;getSkinTextures()Lnet/minecraft/client/util/SkinTextures;"
    ))
    private SkinTextures multiskin$customSkinTexture(AbstractClientPlayerEntity player) {
        return SkinClient.getSkinTextures(player.getUuid(), player.getSkinTextures());
    }
}