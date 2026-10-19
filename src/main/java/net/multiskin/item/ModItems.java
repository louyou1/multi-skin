package net.multiskin.item;

import net.multiskin.MultiSkinMod;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModItems {
    public static final Item CRUTCH = registerItem("crutch", new Item(new Item.Settings().maxCount(1)));

    private static Item registerItem(String name, Item item) {
        return Registry.register(Registries.ITEM, Identifier.of(MultiSkinMod.MOD_ID, name), item);
    }

    public static void registerModItems() {
        MultiSkinMod.LOGGER.info("Registering Mod Items For " + MultiSkinMod.MOD_ID);
    }
}