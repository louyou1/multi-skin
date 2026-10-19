package net.multiskin.mixin.bodyparts;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.entity.LivingEntity;
import net.multiskin.bodyparts.BodyPart;
import net.multiskin.bodyparts.BodyPartClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BipedEntityModel.class)
public abstract class BipedEntityModelMixin {
    @Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void multiskin$hideMissingParts(
            LivingEntity entity,
            float limbAngle,
            float limbDistance,
            float animationProgress,
            float headYaw,
            float headPitch,
            CallbackInfo ci
    ) {
        if (!(entity instanceof AbstractClientPlayerEntity player)) {
            return;
        }

        int mask = BodyPartClient.getMask(player.getUuid());
        BipedEntityModel<?> model = (BipedEntityModel<?>)(Object)this;
        model.rightArm.visible = (mask & BodyPart.RIGHT_ARM.mask()) == 0;
        model.leftArm.visible = (mask & BodyPart.LEFT_ARM.mask()) == 0;
        model.rightLeg.visible = (mask & BodyPart.RIGHT_LEG.mask()) == 0;
        model.leftLeg.visible = (mask & BodyPart.LEFT_LEG.mask()) == 0;
    }
}
