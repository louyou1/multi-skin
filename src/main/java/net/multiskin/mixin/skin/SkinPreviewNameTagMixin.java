package net.multiskin.mixin.skin;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.multiskin.skin.SkinClient;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public abstract class SkinPreviewNameTagMixin<T extends Entity> {
    @Inject(method = "hasLabel", at = @At("HEAD"), cancellable = true)
    private void multiskin$hidePreviewNameTag(T entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof AbstractClientPlayerEntity player && SkinClient.isRenderingSkinPreview(player.getUuid())) {
            cir.setReturnValue(false);
        }
    }

    @Inject(
            method = "renderLabelIfPresent(Lnet/minecraft/entity/Entity;Lnet/minecraft/text/Text;" + "Lnet/minecraft/client/util/math/MatrixStack;" + "Lnet/minecraft/client/render/VertexConsumerProvider;IF)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void multiskin$skipPreviewNameTag(T entity, Text text, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, float tickDelta, CallbackInfo ci) {
        if (entity instanceof AbstractClientPlayerEntity player && SkinClient.isRenderingSkinPreview(player.getUuid())) {
            ci.cancel();
        }
    }
}