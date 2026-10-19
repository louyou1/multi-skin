package net.multiskin.mixin.bodyparts;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.LivingEntity;
import net.multiskin.bodyparts.BodyPart;
import net.multiskin.bodyparts.BodyPartClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityModel.class)
public abstract class PlayerEntityModelMixin {
    @Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void multiskin$hideMissingOverlays(
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
        PlayerEntityModel<?> model = (PlayerEntityModel<?>)(Object)this;
        model.rightSleeve.visible = (mask & BodyPart.RIGHT_ARM.mask()) == 0;
        model.leftSleeve.visible = (mask & BodyPart.LEFT_ARM.mask()) == 0;
        model.rightPants.visible = (mask & BodyPart.RIGHT_LEG.mask()) == 0;
        model.leftPants.visible = (mask & BodyPart.LEFT_LEG.mask()) == 0;
    }
}
