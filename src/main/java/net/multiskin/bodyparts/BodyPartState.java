package net.multiskin.bodyparts;

import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.world.PersistentState;
import net.multiskin.MultiSkinMod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class BodyPartState extends PersistentState {
    private static final String DATA_KEY = "players";
    public static final Type<BodyPartState> TYPE = new Type<>(
            BodyPartState::new,
            BodyPartState::fromNbt,
            DataFixTypes.LEVEL
    );

    private final Map<UUID, Integer> removedParts = new HashMap<>();

    private static BodyPartState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        BodyPartState state = new BodyPartState();
        NbtCompound players = nbt.getCompound(DATA_KEY);
        for (String uuidString : players.getKeys()) {
            try {
                int mask = players.getInt(uuidString);
                if (mask != 0) {
                    state.removedParts.put(UUID.fromString(uuidString), mask);
                }
            } catch (IllegalArgumentException ignored) {
                MultiSkinMod.LOGGER.warn("UUID invalide dans les donnees des membres: {}", uuidString);
            }
        }
        return state;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        NbtCompound players = new NbtCompound();
        removedParts.forEach((uuid, mask) -> players.putInt(uuid.toString(), mask));
        nbt.put(DATA_KEY, players);
        return nbt;
    }

    public int getMask(UUID playerUuid) {
        return removedParts.getOrDefault(playerUuid, 0);
    }

    public Map<UUID, Integer> getAllMasks() {
        return Map.copyOf(removedParts);
    }

    public void setMask(UUID playerUuid, int mask) {
        if (getMask(playerUuid) == mask) {
            return;
        }
        if (mask == 0) {
            removedParts.remove(playerUuid);
        } else {
            removedParts.put(playerUuid, mask);
        }
        markDirty();
    }
}
