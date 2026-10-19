package net.multiskin.bodyparts;

import net.minecraft.entity.EntityPose;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.multiskin.item.ModItems;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

public final class BodyPartMovement {
    private static final Map<PlayerEntity, Boolean> FORCED_CRAWLING =
            Collections.synchronizedMap(new WeakHashMap<>());

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
            if (player.getStackInHand(hand).isOf(ModItems.CRUTCH)
                    && !BodyPart.isHandArmRemoved(mask, player.getMainArm(), hand)) {
                return true;
            }
        }
        return false;
    }
}
