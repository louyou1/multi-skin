package net.multiskin.mixin.skin;

import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.SkinTextures;
import net.multiskin.skin.SkinClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(PlayerListHud.class)
public class PlayerListHudMixin {
    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/PlayerListEntry;getSkinTextures()Lnet/minecraft/client/util/SkinTextures;"))
    private SkinTextures multiskin$useCustomSkinInTab(PlayerListEntry entry) {
        return SkinClient.getSkinTextures(entry.getProfile().getId(), entry.getSkinTextures());
    }
}
