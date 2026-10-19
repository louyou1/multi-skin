package net.multiskin.skin.size;

import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.multiskin.MultiSkinMod;
import net.multiskin.config.MultiSkinConfig;
import virtuoel.pehkui.api.ScaleTypes;

public final class SizeManager {
    public static final int DEFAULT_HEIGHT_CM = 185;
    public static final int MIN_HEIGHT_CM = 50;
    public static final int MAX_HEIGHT_CM = 300;
    private static final Identifier MOVEMENT_SPEED_MODIFIER_ID =
            Identifier.of(MultiSkinMod.MOD_ID, "skin_size_movement_speed");
    private SizeManager() {
    }

    public static float toScale(int heightCm) {
        return heightCm / (float) DEFAULT_HEIGHT_CM;
    }

    public static void apply(ServerPlayerEntity player, int heightCm) {
        float scale = toScale(heightCm);

        ScaleTypes.HEIGHT.getScaleData(player).setScale(scale);
        ScaleTypes.HEIGHT.getScaleData(player).setPersistence(true);
        ScaleTypes.WIDTH.getScaleData(player).setScale(scale);
        ScaleTypes.WIDTH.getScaleData(player).setPersistence(true);

        float reachScale = 1.0f + (scale - 1.0f) * MultiSkinConfig.getReachEffect();
        ScaleTypes.REACH.getScaleData(player).setScale(reachScale);
        ScaleTypes.REACH.getScaleData(player).setPersistence(true);

        EntityAttributeInstance movementSpeed = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (movementSpeed == null) {
            return;
        }

        movementSpeed.removeModifier(MOVEMENT_SPEED_MODIFIER_ID);

        double speedEffect = (scale - 1.0f) * MultiSkinConfig.getMovementSpeedEffect();
        if (speedEffect != 0.0d) {
            movementSpeed.addTemporaryModifier(new EntityAttributeModifier(
                    MOVEMENT_SPEED_MODIFIER_ID,
                    speedEffect,
                    EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE
            ));
        }
    }

    public static void reset(ServerPlayerEntity player) {
        ScaleTypes.HEIGHT.getScaleData(player).resetScale();
        ScaleTypes.WIDTH.getScaleData(player).resetScale();
        ScaleTypes.REACH.getScaleData(player).resetScale();

        EntityAttributeInstance movementSpeed = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (movementSpeed != null) {
            movementSpeed.removeModifier(MOVEMENT_SPEED_MODIFIER_ID);
        }
    }
}
