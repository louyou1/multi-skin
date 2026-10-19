package net.multiskin.mixin.bodyparts;

import net.minecraft.server.network.ServerPlayerEntity;
import net.multiskin.bodyparts.BodyPart;
import net.multiskin.bodyparts.BodyPartManager;
import net.multiskin.bodyparts.BodyPartMovement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerMovementMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void multiskin$updateMissingLegPose(CallbackInfo ci) {
        ServerPlayerEntity player = (ServerPlayerEntity)(Object)this;
        int mask = BodyPartManager.getMask(player);
        BodyPartMovement.updatePose(player, mask);
        if (BodyPart.hasOneLeg(mask)) {
            player.setSprinting(false);
        }
    }
}
