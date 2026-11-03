package net.multiskin.bodyparts;

import net.minecraft.entity.EntityPose;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.multiskin.MultiSkinMod;
import net.multiskin.item.ModItems;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

public final class BodyPartMovement {
    private static final Identifier CRUTCH_MOVEMENT_SPEED_MODIFIER_ID = Identifier.of(MultiSkinMod.MOD_ID, "crutch_movement_speed");
    private static final double CRUTCH_MOVEMENT_SPEED_PENALTY = -0.25d;
    private static final Map<PlayerEntity, Boolean> FORCED_CRAWLING = Collections.synchronizedMap(new WeakHashMap<>());

    private BodyPartMovement() {
    }

    public static void updatePose(PlayerEntity player, int mask) {
        if (BodyPart.hasNoLegs(mask)) {
            FORCED_CRAWLING.put(player, Boolean.TRUE);
            player.setSwimming(true);
            if (player.getPose() != EntityPose.SWIMMING) {
                player.setPose(EntityPose.SWIMMING);
            }
        } else if (FORCED_CRAWLING.remove(player) != null && !player.isTouchingWater()) {
            player.setSwimming(false);
            player.setPose(EntityPose.STANDING);
        }
    }

    public static boolean hasCrutchInUsableHand(PlayerEntity player, int mask) {
        for (Hand hand : Hand.values()) {
            if (player.getStackInHand(hand).isOf(ModItems.CRUTCH) && !BodyPart.isHandArmRemoved(mask, player.getMainArm(), hand)) {
                return true;
            }
        }
        return false;
    }

    public static void updateCrutchMovementSpeed(ServerPlayerEntity player, int mask) {
        EntityAttributeInstance movementSpeed = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (movementSpeed == null) {
            return;
        }

        if (hasCrutchInUsableHand(player, mask)) {
            if (movementSpeed.getModifier(CRUTCH_MOVEMENT_SPEED_MODIFIER_ID) == null) {
                movementSpeed.addTemporaryModifier(new EntityAttributeModifier(
                        CRUTCH_MOVEMENT_SPEED_MODIFIER_ID,
                        CRUTCH_MOVEMENT_SPEED_PENALTY,
                        EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE
                ));
            }
        } else {
            movementSpeed.removeModifier(CRUTCH_MOVEMENT_SPEED_MODIFIER_ID);
        }
    }
}
