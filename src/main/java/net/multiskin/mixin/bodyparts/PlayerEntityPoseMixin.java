package net.multiskin.mixin.bodyparts;

import net.minecraft.entity.EntityPose;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.multiskin.bodyparts.BodyPart;
import net.multiskin.bodyparts.BodyPartManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityPoseMixin {
    @Unique
    private boolean multiskin$restoreSwimmingAfterTravel;

    @Unique
    private boolean multiskin$normalJumpActive;

    @Inject(method = "updatePose", at = @At("HEAD"), cancellable = true)
    private void multiskin$keepPlayersWithoutLegsProne(CallbackInfo ci) {
        PlayerEntity player = (PlayerEntity)(Object)this;
        if (!(player instanceof ServerPlayerEntity serverPlayer)
                || !BodyPart.hasNoLegs(BodyPartManager.getMask(serverPlayer))
                || player.hasVehicle() || player.isSleeping() || player.isSpectator()
                || player.isFallFlying()) {
            return;
        }

        player.setSwimming(true);
        if (player.getPose() != EntityPose.SWIMMING) {
            player.setPose(EntityPose.SWIMMING);
        }
        ci.cancel();
    }

    @Inject(method = "travel", at = @At("HEAD"))
    private void multiskin$useNormalJumpPhysics(CallbackInfo ci) {
        PlayerEntity player = (PlayerEntity)(Object)this;
        if (!(player instanceof ServerPlayerEntity serverPlayer)) {
            return;
        }
        if (!BodyPart.hasNoLegs(BodyPartManager.getMask(serverPlayer))
                || serverPlayer.hasVehicle() || serverPlayer.isTouchingWater()) {
            multiskin$normalJumpActive = false;
            return;
        }
        if (serverPlayer.getVelocity().y > 0.0) {
            multiskin$normalJumpActive = true;
        } else if (serverPlayer.isOnGround()) {
            multiskin$normalJumpActive = false;
        }
        if (multiskin$normalJumpActive && serverPlayer.isSwimming()) {
            serverPlayer.setSwimming(false);
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
