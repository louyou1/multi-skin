package net.multiskin;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.multiskin.item.ModItems;

public class ModGroups {
    public static void registerGroups() {
        Registry.register(Registries.ITEM_GROUP, Identifier.of(MultiSkinMod.MOD_ID, "multi_skin"),
                FabricItemGroup.builder()
                        .icon(() -> new ItemStack(ModItems.CRUTCH))
                        .displayName(Text.translatable("group.multi-skin.multiskin_mod"))
                        .entries((displayContext, entries) -> entries.add(ModItems.CRUTCH))
                        .build());
        MultiSkinMod.LOGGER.info("Registering Groups For " + MultiSkinMod.MOD_ID);
    }
}
