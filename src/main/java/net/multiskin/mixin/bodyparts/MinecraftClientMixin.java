package net.multiskin.mixin.bodyparts;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.option.GameOptions;
import net.multiskin.bodyparts.BodyPart;
import net.multiskin.bodyparts.BodyPartClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
    @Shadow
    @Final
    public GameOptions options;

    @Shadow
    public ClientPlayerEntity player;

    @Shadow
    public ClientPlayerInteractionManager interactionManager;

    @Inject(method = "doAttack()Z", at = @At("HEAD"), cancellable = true)
    private void multiskin$preventAttackWithoutMainArm(CallbackInfoReturnable<Boolean> cir) {
        if (player != null && BodyPart.isArmRemoved(
                BodyPartClient.getMask(player.getUuid()), player.getMainArm())) {
            options.attackKey.setPressed(false);
            if (interactionManager != null) {
                interactionManager.cancelBlockBreaking();
            }
            cir.setReturnValue(false);
        }
    }
}
