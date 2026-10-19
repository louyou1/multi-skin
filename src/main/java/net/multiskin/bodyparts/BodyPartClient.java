package net.multiskin.bodyparts;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.util.ActionResult;
import net.minecraft.util.TypedActionResult;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BodyPartClient {
    private static final Map<UUID, Integer> REMOVED_PARTS = new ConcurrentHashMap<>();
    private static final ThreadLocal<Integer> PREVIEW_MASK = new ThreadLocal<>();

    private BodyPartClient() {
    }

    public static void initialize() {
        ClientPlayNetworking.registerGlobalReceiver(BodyPartNetworking.PAYLOAD_ID, (payload, context) -> {
            if (payload.removedMask() == 0) {
                REMOVED_PARTS.remove(payload.playerUuid());
            } else {
                REMOVED_PARTS.put(payload.playerUuid(), payload.removedMask());
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> REMOVED_PARTS.clear());
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) ->
                BodyPart.isHandArmRemoved(getMask(player.getUuid()), player.getMainArm(), hand)
                        ? ActionResult.FAIL
                        : ActionResult.PASS
        );
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) ->
                BodyPart.isHandArmRemoved(getMask(player.getUuid()), player.getMainArm(), hand)
                        ? ActionResult.FAIL
                        : ActionResult.PASS
        );
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) ->
                BodyPart.isHandArmRemoved(getMask(player.getUuid()), player.getMainArm(), hand)
                        ? ActionResult.FAIL
                        : ActionResult.PASS
        );
        UseItemCallback.EVENT.register((player, world, hand) ->
                BodyPart.isHandArmRemoved(getMask(player.getUuid()), player.getMainArm(), hand)
                        ? TypedActionResult.fail(player.getStackInHand(hand))
                        : TypedActionResult.pass(player.getStackInHand(hand))
        );
    }

    public static int getMask(UUID playerUuid) {
        Integer previewMask = PREVIEW_MASK.get();
        if (previewMask != null) {
            var player = net.minecraft.client.MinecraftClient.getInstance().player;
            if (player != null && player.getUuid().equals(playerUuid)) {
                return previewMask;
            }
        }
        return REMOVED_PARTS.getOrDefault(playerUuid, 0);
    }

    public static void withPreviewMask(int mask, Runnable render) {
        Integer previousMask = PREVIEW_MASK.get();
        PREVIEW_MASK.set(mask & 0xF);
        try {
            render.run();
        } finally {
            if (previousMask == null) {
                PREVIEW_MASK.remove();
            } else {
                PREVIEW_MASK.set(previousMask);
            }
        }
    }
}
