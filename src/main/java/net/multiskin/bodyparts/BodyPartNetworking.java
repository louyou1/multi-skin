package net.multiskin.bodyparts;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.multiskin.MultiSkinMod;

import java.util.Objects;
import java.util.UUID;

public final class BodyPartNetworking {
    public static final CustomPayload.Id<BodyPartPayload> PAYLOAD_ID = new CustomPayload.Id<>(
            Identifier.of(MultiSkinMod.MOD_ID, "body_parts")
    );
    private static final PacketCodec<PacketByteBuf, BodyPartPayload> CODEC = PacketCodec.of(
            (payload, buf) -> {
                buf.writeUuid(payload.playerUuid());
                buf.writeVarInt(payload.removedMask());
            },
            buf -> new BodyPartPayload(buf.readUuid(), buf.readVarInt())
    );

    private BodyPartNetworking() {
    }

    public static void initialize() {
        PayloadTypeRegistry.playS2C().register(PAYLOAD_ID, CODEC);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                BodyPartManager.getAllMasks(server).forEach((uuid, mask) -> {
                    if (mask != 0 && ServerPlayNetworking.canSend(handler.getPlayer(), PAYLOAD_ID)) {
                        ServerPlayNetworking.send(handler.getPlayer(), new BodyPartPayload(uuid, mask));
                    }
                })
        );
    }

    public static void broadcast(ServerPlayerEntity player, int removedMask) {
        var server = Objects.requireNonNull(player.getServer(), "Player must be connected to a server.");
        BodyPartPayload payload = new BodyPartPayload(player.getUuid(), removedMask);
        for (ServerPlayerEntity recipient : server.getPlayerManager().getPlayerList()) {
            if (ServerPlayNetworking.canSend(recipient, PAYLOAD_ID)) {
                ServerPlayNetworking.send(recipient, payload);
            }
        }
    }

    public record BodyPartPayload(UUID playerUuid, int removedMask) implements CustomPayload {
        @Override
        public Id<? extends CustomPayload> getId() {
            return PAYLOAD_ID;
        }
    }
}
