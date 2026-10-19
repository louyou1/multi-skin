package net.multiskin.mixin.bodyparts;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.VertexConsumerProvider;
import net.multiskin.bodyparts.BodyPart;
import net.multiskin.bodyparts.BodyPartClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerEntityRendererMixin {
    @Inject(method = "renderArm", at = @At("HEAD"), cancellable = true)
    private void multiskin$hideMissingFirstPersonArm(
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            AbstractClientPlayerEntity player,
            ModelPart arm,
            ModelPart sleeve,
            CallbackInfo ci
    ) {
        PlayerEntityRenderer renderer = (PlayerEntityRenderer)(Object)this;
        boolean isRightArm = arm == renderer.getModel().rightArm;
        if (BodyPart.isArmRemoved(
                BodyPartClient.getMask(player.getUuid()),
                isRightArm ? net.minecraft.util.Arm.RIGHT : net.minecraft.util.Arm.LEFT)) {
            ci.cancel();
        }
    }
}
