package net.multiskin.mixin.skin;

import net.minecraft.client.gui.widget.OptionListWidget;
import net.minecraft.client.gui.screen.option.GameOptionsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GameOptionsScreen.class)
public interface GameOptionsScreenAccessor {
    @Accessor("body")
    OptionListWidget multiskin$getBody();
}