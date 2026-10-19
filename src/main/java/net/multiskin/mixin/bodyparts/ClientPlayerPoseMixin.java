package net.multiskin.mixin.bodyparts;

import net.minecraft.entity.player.PlayerEntity;
import net.multiskin.bodyparts.BodyPart;
import net.multiskin.bodyparts.BodyPartClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public abstract class ClientPlayerPoseMixin {
    @Unique
    private boolean multiskin$restoreSwimmingAfterTravel;

    @Unique
    private boolean multiskin$normalJumpActive;

    @Inject(method = "travel", at = @At("HEAD"))
    private void multiskin$useNormalJumpPhysics(CallbackInfo ci) {
        PlayerEntity player = (PlayerEntity)(Object)this;
        int mask = BodyPartClient.getMask(player.getUuid());
        if (!BodyPart.hasNoLegs(mask) || player.hasVehicle() || player.isTouchingWater()) {
            multiskin$normalJumpActive = false;
            return;
        }
        if (player.getVelocity().y > 0.0) {
            multiskin$normalJumpActive = true;
        } else if (player.isOnGround()) {
            multiskin$normalJumpActive = false;
        }
        if (multiskin$normalJumpActive && player.isSwimming()) {
            player.setSwimming(false);
            multiskin$restoreSwimmingAfterTravel = true;
        }
    }

    @Inject(method = "travel", at = @At("RETURN"))
    private void multiskin$restoreProneSwimming(CallbackInfo ci) {
        if (multiskin$restoreSwimmingAfterTravel) {
            ((PlayerEntity)(Object)this).setSwimming(true);
            multiskin$restoreSwimmingAfterTravel = false;
        }
    }
}
