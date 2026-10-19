package net.multiskin.mixin.skin;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.SkinTextures;
import net.multiskin.skin.SkinClient;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerListEntry.class)
public class PlayerListEntryMixin {
    @Inject(method = "getSkinTextures", at = @At("RETURN"), cancellable = true)
    private void multiskin$customSkinTextures(CallbackInfoReturnable<SkinTextures> cir) {
        PlayerListEntry entry = (PlayerListEntry) (Object) this;
        GameProfile profile = entry.getProfile();
        cir.setReturnValue(SkinClient.getSkinTextures(profile.getId(), cir.getReturnValue()));
    }
}