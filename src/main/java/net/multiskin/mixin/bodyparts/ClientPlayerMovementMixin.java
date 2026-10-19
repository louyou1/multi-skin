package net.multiskin.mixin.bodyparts;

import net.minecraft.client.input.Input;
import net.minecraft.client.network.ClientPlayerEntity;
import net.multiskin.bodyparts.BodyPart;
import net.multiskin.bodyparts.BodyPartClient;
import net.multiskin.bodyparts.BodyPartMovement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerMovementMixin {
    @Shadow
    public Input input;

    @Inject(method = "tickMovement", at = @At("HEAD"))
    private void multiskin$updateMissingLegPose(CallbackInfo ci) {
        ClientPlayerEntity player = (ClientPlayerEntity)(Object)this;
        int mask = BodyPartClient.getMask(player.getUuid());
        BodyPartMovement.updatePose(player, mask);
        if (BodyPart.hasOneLeg(mask)) {
            player.setSprinting(false);
        }
    }

    @Inject(
            method = "tickMovement",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/input/Input;tick(ZF)V",
                    shift = At.Shift.AFTER
            )
    )
    private void multiskin$requireJumpToMove(CallbackInfo ci) {
        ClientPlayerEntity player = (ClientPlayerEntity)(Object)this;
        int mask = BodyPartClient.getMask(player.getUuid());
        if (BodyPart.hasNoLegs(mask)) {
            if (BodyPart.hasNoArms(mask)) {
                input.movementForward = 0.0F;
                input.movementSideways = 0.0F;
                input.jumping = false;
            } else if (BodyPart.hasOneArm(mask)) {
                input.movementForward *= 0.5F;
                input.movementSideways *= 0.5F;
            }
        }
        if (BodyPart.hasOneLeg(mask) && !BodyPartMovement.hasCrutchInUsableHand(player, mask)
                && player.isOnGround() && !input.jumping
                && !player.getAbilities().flying && !player.hasVehicle()) {
            input.movementForward = 0.0F;
            input.movementSideways = 0.0F;
        }
    }

    @Inject(method = "canStartSprinting()Z", at = @At("HEAD"), cancellable = true)
    private void multiskin$preventSprintingWithOneLeg(CallbackInfoReturnable<Boolean> cir) {
        ClientPlayerEntity player = (ClientPlayerEntity)(Object)this;
        if (BodyPart.hasOneLeg(BodyPartClient.getMask(player.getUuid()))) {
            cir.setReturnValue(false);
        }
    }
}
