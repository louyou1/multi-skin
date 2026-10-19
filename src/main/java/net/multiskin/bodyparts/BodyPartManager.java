package net.multiskin.bodyparts;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class BodyPartManager {
    private BodyPartManager() {
    }

    public static Map<UUID, Integer> getAllMasks(MinecraftServer server) {
        return getState(server).getAllMasks();
    }

    public static int getMask(ServerPlayerEntity player) {
        return getState(Objects.requireNonNull(player.getServer())).getMask(player.getUuid());
    }

    public static int setPartRemoved(ServerPlayerEntity player, BodyPart part, boolean removed) {
        BodyPartState state = getState(Objects.requireNonNull(player.getServer()));
        int oldMask = state.getMask(player.getUuid());
        int newMask = removed ? oldMask | part.mask() : oldMask & ~part.mask();
        state.setMask(player.getUuid(), newMask);
        return newMask;
    }

    public static void setMask(ServerPlayerEntity player, int mask) {
        getState(Objects.requireNonNull(player.getServer())).setMask(player.getUuid(), mask & 0xF);
    }

    public static void reset(ServerPlayerEntity player) {
        getState(Objects.requireNonNull(player.getServer())).setMask(player.getUuid(), 0);
    }

    private static BodyPartState getState(MinecraftServer server) {
        return server.getOverworld()
                .getPersistentStateManager()
                .getOrCreate(BodyPartState.TYPE, "multiskin_body_parts");
    }
}
