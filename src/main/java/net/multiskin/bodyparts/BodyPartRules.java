package net.multiskin.bodyparts;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.TypedActionResult;

public final class BodyPartRules {
    private BodyPartRules() {
    }

    public static void initialize() {
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (player instanceof ServerPlayerEntity serverPlayer
                    && BodyPart.isHandArmRemoved(
                    BodyPartManager.getMask(serverPlayer), player.getMainArm(), hand)) {
                return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        });
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (player instanceof ServerPlayerEntity serverPlayer
                    && BodyPart.isHandArmRemoved(
                    BodyPartManager.getMask(serverPlayer), player.getMainArm(), hand)) {
                return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        });
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (player instanceof ServerPlayerEntity serverPlayer
                    && BodyPart.isHandArmRemoved(
                    BodyPartManager.getMask(serverPlayer), player.getMainArm(), hand)) {
                return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        });
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (player instanceof ServerPlayerEntity serverPlayer
                    && BodyPart.isHandArmRemoved(
                    BodyPartManager.getMask(serverPlayer), player.getMainArm(), hand)) {
                return TypedActionResult.fail(player.getStackInHand(hand));
            }
            return TypedActionResult.pass(player.getStackInHand(hand));
        });
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) ->
                !(player instanceof ServerPlayerEntity serverPlayer)
                        || !BodyPart.isArmRemoved(
                        BodyPartManager.getMask(serverPlayer), player.getMainArm())
        );
    }
}
