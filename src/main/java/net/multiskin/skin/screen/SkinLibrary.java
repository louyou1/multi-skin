package net.multiskin.skin.screen;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.minecraft.client.MinecraftClient;
import net.multiskin.MultiSkinMod;
import net.multiskin.skin.size.SizeManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SkinLibrary {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String ORDER_FILE = ".order.json";
    private static final class RootHolder {
        private static final Path ROOT = MinecraftClient.getInstance().runDirectory.toPath()
                .resolve("multi-skin")
                .resolve("skins");
    }
    private static Map<String, List<String>> cachedOrders;
    private static final Map<Path, SkinEntry> skinCache = new HashMap<>();
    private static final Map<Path, List<Path>> contentsCache = new HashMap<>();

    public record SkinEntry(
            Path file,
            String name,
            String url,
            String model,
            int heightCm,
            int keyCode,
            int removedPartsMask
    ) {}

    public static Path getRoot() {
        return root();
    }

    private static Path root() {
        return RootHolder.ROOT;
    }

    private static void invalidateSkinCache(Path path) {
        Path normalizedPath = path.toAbsolutePath().normalize();
        skinCache.keySet().removeIf(cachedPath ->
                cachedPath.equals(normalizedPath) || cachedPath.startsWith(normalizedPath));
    }

    public static void initialize() {
        cachedOrders = null;
        skinCache.clear();
        contentsCache.clear();

        try {
            Files.createDirectories(root());
            Path orderFile = root().resolve(ORDER_FILE);

            if (!Files.exists(orderFile)) {
                createInitialOrder();
            } else {
                repairOrder();
            }

        } catch (IOException e) {
            MultiSkinMod.LOGGER.error("Impossible d'initialiser la bibliothèque.", e);
        }
    }

    private static void createInitialOrder() throws IOException {
        Map<String, List<String>> orders = new HashMap<>();
        buildInitialOrder(root(), orders);

        saveOrders(orders);
    }

    private static void buildInitialOrder(Path folder, Map<String, List<String>> orders) {
        List<Path> contents = getDirectContents(folder);
        contents.sort(Comparator.comparing(path -> path.getFileName().toString().toLowerCase()));
        String folderRelative = folder.equals(root()) ? "" : toRelative(folder);
        List<String> children = new ArrayList<>();

        for (Path path : contents) {
            children.add(path.getFileName().toString());

            if (Files.isDirectory(path)) {
                buildInitialOrder(path, orders);
            }
        }

        orders.put(folderRelative, children);
    }

    private static void repairOrder() throws IOException {
        Map<String, List<String>> orders = loadOrders();
        boolean changed = repairFolderOrder(root(), "", orders);

        if (changed) {
            saveOrders(orders);
        }
    }

    private static boolean repairFolderOrder(Path folder, String relativeFolder, Map<String, List<String>> orders) {
        List<Path> actualContents = getDirectContents(folder);
        List<String> currentOrder = orders.computeIfAbsent(relativeFolder, key -> new ArrayList<>());
        Set<String> actualNames = new HashSet<>();

        for (Path path : actualContents) {
            actualNames.add(path.getFileName().toString());
        }
        boolean changed = false;

        changed |= currentOrder.removeIf(name -> !actualNames.contains(name));

        for (Path path : actualContents) {
            String name = path.getFileName().toString();

            if (!currentOrder.contains(name)) {
                currentOrder.add(name);
                changed = true;
            }
        }

        for (Path path : actualContents) {
            if (!Files.isDirectory(path)) {
                continue;
            }

            String childRelative = relativeFolder.isEmpty() ? path.getFileName().toString() : relativeFolder + "/" + path.getFileName().toString();
            changed |= repairFolderOrder(path, childRelative, orders);
        }

        return changed;
    }

    public static List<Path> getContents(Path folder) {
        Path cacheKey = folder.toAbsolutePath().normalize();
        List<Path> cached = contentsCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        List<Path> contents = getDirectContents(folder);

        if (contents.isEmpty()) {
            contentsCache.put(cacheKey, contents);
            return contents;
        }

        Map<String, List<String>> orders = loadOrders();
        String relativeFolder = folder.equals(root()) ? "" : toRelative(folder);
        List<String> order = orders.getOrDefault(relativeFolder, new ArrayList<>());
        Map<String, Integer> positions = new HashMap<>();

        for (int i = 0; i < order.size(); i++) {
            positions.put(order.get(i), i);
        }

        contents.sort((a, b) -> {
                    String nameA = a.getFileName().toString();
                    String nameB = b.getFileName().toString();
                    int positionA = positions.getOrDefault(nameA, Integer.MAX_VALUE);
                    int positionB = positions.getOrDefault(nameB, Integer.MAX_VALUE);

                    if (positionA != positionB) {
                        return Integer.compare(positionA, positionB);
                    }

                    return nameA.compareToIgnoreCase(nameB);
                }
        );
        contentsCache.put(cacheKey, contents);
        return contents;
    }

    private static List<Path> getDirectContents(Path folder) {
        List<Path> result = new ArrayList<>();

        if (!Files.isDirectory(folder)) {
            return result;
        }

        try (var stream = Files.list(folder)) {
            stream.filter(path -> !path.getFileName().toString().equals(ORDER_FILE)).forEach(result::add);

        } catch (IOException e) {
            MultiSkinMod.LOGGER.error("Impossible de lire le contenu du dossier : {}", folder, e);
        }

        return result;
    }

    private static Map<String, List<String>> loadOrders() {
        if (cachedOrders == null) {
            cachedOrders = loadOrdersFromDisk();
        }

        return cachedOrders;
    }

    private static Map<String, List<String>> loadOrdersFromDisk() {
        Map<String, List<String>> result = new HashMap<>();
        Path file = root().resolve(ORDER_FILE);

        if (!Files.exists(file)) {
            return result;
        }

        try {
            JsonObject object = GSON.fromJson(Files.readString(file), JsonObject.class);

            if (object == null) {
                return result;
            }

            if (object.has("folders")) {
                JsonObject folders = object.getAsJsonObject("folders");

                for (String folder : folders.keySet()) {
                    JsonArray array = folders.getAsJsonArray(folder);
                    List<String> order = new ArrayList<>();

                    for (int i = 0; i < array.size(); i++) {
                        order.add(array.get(i).getAsString());
                    }

                    result.put(normalizeRelative(folder), order);
                }

                return result;
            }

            if (object.has("order")) {
                JsonArray array = object.getAsJsonArray("order");
                List<String> oldOrder = new ArrayList<>();

                for (int i = 0; i < array.size(); i++) {
                    oldOrder.add(normalizeRelative(array.get(i).getAsString()));
                }

                convertOldOrder(oldOrder, result);
                saveOrders(result);
                return result;
            }

        } catch (Exception e) {
            MultiSkinMod.LOGGER.error("Impossible de lire {}", file, e);
        }

        return result;
    }

    private static void convertOldOrder(List<String> oldOrder, Map<String, List<String>> result) {
        for (String relative : oldOrder) {
            Path path = root().resolve(relative);

            if (!Files.exists(path)) {
                continue;
            }

            Path parent = path.getParent();

            if (parent == null) {
                continue;
            }

            String parentRelative = parent.equals(root()) ? "" : toRelative(parent);
            String name = path.getFileName().toString();

            List<String> order = result.computeIfAbsent(parentRelative, key -> new ArrayList<>());

            if (!order.contains(name)) {
                order.add(name);
            }
        }
    }

    private static void saveOrders(Map<String, List<String>> orders) {
        try {
            Files.createDirectories(root());

            JsonObject object = new JsonObject();
            JsonObject folders = new JsonObject();

            orders.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                JsonArray array = new JsonArray();

                for (String name : entry.getValue()) {
                    array.add(name);
                }

                folders.add(normalizeRelative(entry.getKey()), array);
            });

            object.add("folders", folders);
            Files.writeString(root().resolve(ORDER_FILE), GSON.toJson(object));
            cachedOrders = orders;
            contentsCache.clear();

        } catch (IOException e) {
            MultiSkinMod.LOGGER.error("Impossible de sauvegarder l'ordre.", e);
        }
    }

    public static void move(Path source, Path targetFolder) throws IOException {

        if (source == null || targetFolder == null) {
            return;
        }

        if (!Files.isDirectory(targetFolder)) {
            return;
        }

        Path normalizedSource = source.toAbsolutePath().normalize();
        Path normalizedTarget = targetFolder.toAbsolutePath().normalize();

        if (normalizedTarget.startsWith(normalizedSource)) {
            return;
        }

        Path sourceParent = source.getParent();

        if (sourceParent != null && sourceParent.toAbsolutePath().normalize().equals(normalizedTarget)) {
            moveToEnd(source, targetFolder);

            return;
        }

        String sourceName = source.getFileName().toString();

        Path destination = getUniqueDestination(targetFolder, sourceName);
        Map<String, List<String>> orders = loadOrders();
        invalidateSkinCache(source);

        String sourceParentRelative = sourceParent == null || sourceParent.equals(root()) ? "" : toRelative(sourceParent);
        String targetFolderRelative = targetFolder.equals(root()) ? "" : toRelative(targetFolder);

        Files.move(source, destination);

        List<String> oldOrder = orders.computeIfAbsent(sourceParentRelative, key -> new ArrayList<>());
        oldOrder.remove(sourceName);

        List<String> newOrder = orders.computeIfAbsent(targetFolderRelative, key -> new ArrayList<>());
        String destinationName = destination.getFileName().toString();

        newOrder.remove(destinationName);
        newOrder.add(destinationName);

        if (Files.isDirectory(destination)) {
            String oldRelative = toRelative(source);
            String newRelative = toRelative(destination);

            renameOrderPrefixes(orders, oldRelative, newRelative);
        }

        saveOrders(orders);
    }

    private static void moveToEnd(Path source, Path folder) {
        Map<String, List<String>> orders = loadOrders();

        String folderRelative = folder.equals(root()) ? "" : toRelative(folder);
        String name = source.getFileName().toString();

        List<String> order = orders.computeIfAbsent(folderRelative, key -> new ArrayList<>());

        order.remove(name);
        order.add(name);

        saveOrders(orders);
    }

    public static void moveBefore(Path source, Path target) throws IOException {
        moveRelative(source, target, false);
    }

    public static void moveAfter(Path source, Path target) throws IOException {
        moveRelative(source, target, true);
    }

    private static void moveRelative(Path source, Path target, boolean after) throws IOException {
        if (source == null || target == null || source.equals(target)) {
            return;
        }

        if (!Files.exists(source) || !Files.exists(target)) {
            return;
        }

        if (Files.isDirectory(source)) {
            Path normalizedSource = source.toAbsolutePath().normalize();
            Path normalizedTarget = target.toAbsolutePath().normalize();

            if (normalizedTarget.startsWith(normalizedSource)) {
                return;
            }
        }

        Path sourceParent = source.getParent();
        Path targetParent = target.getParent();

        if (sourceParent == null || targetParent == null) {
            return;
        }

        String sourceName = source.getFileName().toString();
        String targetName = target.getFileName().toString();
        String sourceParentRelative = sourceParent.equals(root()) ? "" : toRelative(sourceParent);
        String targetParentRelative = targetParent.equals(root()) ? "" : toRelative(targetParent);

        Map<String, List<String>> orders = loadOrders();

        if (sourceParent.toAbsolutePath().normalize().equals(targetParent.toAbsolutePath().normalize())) {
            List<String> order = orders.computeIfAbsent(sourceParentRelative, key -> new ArrayList<>());

            int sourceIndex = order.indexOf(sourceName);
            int targetIndex = order.indexOf(targetName);

            if (sourceIndex < 0 || targetIndex < 0) {
                return;
            }

            order.remove(sourceName);
            targetIndex = order.indexOf(targetName);

            int insertionIndex = after ? targetIndex + 1 : targetIndex;

            if (insertionIndex < 0) {
                insertionIndex = 0;
            }

            if (insertionIndex > order.size()) {
                insertionIndex = order.size();
            }

            order.add(insertionIndex, sourceName);

            saveOrders(orders);
            return;
        }

        Path destination = getUniqueDestination(targetParent, sourceName);

        String oldRelative = toRelative(source);
        String newRelative = toRelative(destination);

        invalidateSkinCache(source);
        Files.move(source, destination);

        List<String> sourceOrder = orders.computeIfAbsent(sourceParentRelative, key -> new ArrayList<>());
        sourceOrder.remove(sourceName);

        if (Files.isDirectory(destination)) {
            renameOrderPrefixes(orders, oldRelative, newRelative);
        }

        List<String> targetOrder = orders.computeIfAbsent(targetParentRelative, key -> new ArrayList<>());

        String destinationName = destination.getFileName().toString();
        targetOrder.remove(destinationName);

        int targetIndex = targetOrder.indexOf(targetName);

        if (targetIndex < 0) {
            targetOrder.add(destinationName);
        } else {
            int insertionIndex = after ? targetIndex + 1 : targetIndex;

            if (insertionIndex > targetOrder.size()) {
                insertionIndex = targetOrder.size();
            }

            targetOrder.add(insertionIndex, destinationName);
        }

        saveOrders(orders);
    }

    private static void renameOrderPrefixes(Map<String, List<String>> orders, String oldPrefix, String newPrefix) {
        oldPrefix = normalizeRelative(oldPrefix);
        newPrefix = normalizeRelative(newPrefix);

        Map<String, List<String>> replacement = new HashMap<>();

        for (Map.Entry<String, List<String>> entry : orders.entrySet()) {
            String key = normalizeRelative(entry.getKey());
            String newKey;

            if (key.equals(oldPrefix)) {
                newKey = newPrefix;
            } else if (key.startsWith(oldPrefix + "/")) {
                newKey = newPrefix + key.substring(oldPrefix.length());
            } else {
                newKey = key;
            }
            replacement.put(newKey, entry.getValue());
        }
        orders.clear();
        orders.putAll(replacement);
    }

    public static void delete(Path path) throws IOException {
        if (path == null || path.equals(root())) {
            return;
        }

        invalidateSkinCache(path);
        String relative = toRelative(path);
        Path parent = path.getParent();
        String parentRelative = parent == null || parent.equals(root()) ? "" : toRelative(parent);
        String name = path.getFileName().toString();

        if (Files.isDirectory(path)) {
            try (var stream = Files.walk(path)) {
                stream.sorted(Comparator.reverseOrder()).forEach(
                        current -> {
                            try {
                                Files.delete(current);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        });
            }

        } else {
            Files.deleteIfExists(path);
        }

        Map<String, List<String>> orders = loadOrders();
        List<String> parentOrder = orders.get(parentRelative);

        if (parentOrder != null) {
            parentOrder.remove(name);
        }

        orders.keySet().removeIf(key -> key.equals(relative) || key.startsWith(relative + "/"));

        saveOrders(orders);
    }

    public static void saveSkin(Path folder, String name, String url, String model, int heightCm, int keyCode) throws IOException {
        saveSkin(folder, name, url, model, heightCm, keyCode, 0);
    }

    public static void saveSkin(
            Path folder,
            String name,
            String url,
            String model,
            int heightCm,
            int keyCode,
            int removedPartsMask
    ) throws IOException {
        Files.createDirectories(folder);

        String safeName = sanitizeName(name);
        Path file = getUniqueDestination(folder, safeName + ".json");
        writeSkin(file, name, url, model, heightCm, keyCode, removedPartsMask);

        Map<String, List<String>> orders = loadOrders();

        String folderRelative = folder.equals(root()) ? "" : toRelative(folder);
        List<String> order = orders.computeIfAbsent(folderRelative, key -> new ArrayList<>());

        order.remove(file.getFileName().toString());
        order.add(file.getFileName().toString());

        saveOrders(orders);
    }

    public static SkinEntry updateSkin(SkinEntry oldSkin, String name, String url, String model, int heightCm, int keyCode) throws IOException {
        return updateSkin(oldSkin, name, url, model, heightCm, keyCode, oldSkin.removedPartsMask());
    }

    public static SkinEntry updateSkin(
            SkinEntry oldSkin,
            String name,
            String url,
            String model,
            int heightCm,
            int keyCode,
            int removedPartsMask
    ) throws IOException {
        Path oldFile = oldSkin.file();
        Path folder = oldFile.getParent();

        if (folder == null) {
            throw new IOException("Le skin ne possède pas de dossier parent.");
        }

        String safeName = sanitizeName(name);
        Path newFile = folder.resolve(safeName + ".json");

        if (!oldFile.equals(newFile) && Files.exists(newFile)) {
            newFile = getUniqueDestination(folder, safeName + ".json");
        }

        Map<String, List<String>> orders = loadOrders();
        String folderRelative = folder.equals(root()) ? "" : toRelative(folder);

        String oldName = oldFile.getFileName().toString();
        String newName = newFile.getFileName().toString();

        if (!oldFile.equals(newFile)) {
            Files.move(oldFile, newFile);
            List<String> order = orders.computeIfAbsent(folderRelative, key -> new ArrayList<>());

            int index = order.indexOf(oldName);
            order.remove(oldName);

            if (index < 0) {
                index = order.size();
            }

            order.remove(newName);

            if (index > order.size()) {
                index = order.size();
            }

            order.add(index, newName);
        }

        writeSkin(newFile, name, url, model, heightCm, keyCode, removedPartsMask);

        saveOrders(orders);
        invalidateSkinCache(oldFile);
        invalidateSkinCache(newFile);
        return new SkinEntry(newFile, name, url, model, heightCm, keyCode, removedPartsMask & 0xF);
    }

    public static void createFolder(Path parent, String name) throws IOException {
        Files.createDirectories(parent);

        String safeName = sanitizeName(name);
        Path folder = getUniqueDestination(parent, safeName);

        Files.createDirectories(folder);
        Map<String, List<String>> orders = loadOrders();

        String parentRelative = parent.equals(root()) ? "" : toRelative(parent);
        List<String> order = orders.computeIfAbsent(parentRelative, key -> new ArrayList<>());

        order.add(folder.getFileName().toString());
        orders.putIfAbsent(toRelative(folder), new ArrayList<>());

        saveOrders(orders);
    }

    private static void writeSkin(
            Path file,
            String name,
            String url,
            String model,
            int heightCm,
            int keyCode,
            int removedPartsMask
    ) throws IOException {
        JsonObject object = new JsonObject();

        object.addProperty("name", name);
        object.addProperty("url", url);
        object.addProperty("model", model);
        object.addProperty("heightCm", heightCm);
        object.addProperty("keyCode", keyCode);
        object.addProperty("removedPartsMask", removedPartsMask & 0xF);

        Files.writeString(file, GSON.toJson(object));
        invalidateSkinCache(file);
    }

    private static Path getUniqueDestination(Path folder, String fileName) {
        Path destination = folder.resolve(fileName);

        if (!Files.exists(destination)) {
            return destination;
        }

        String name = fileName;
        String extension = "";

        if (!Files.isDirectory(destination)) {
            int dot = fileName.lastIndexOf('.');

            if (dot > 0) {
                name = fileName.substring(0, dot);
                extension = fileName.substring(dot);
            }
        }

        int number = 2;

        while (Files.exists(destination)) {
            destination = folder.resolve(name + " (" + number + ")" + extension);
            number++;
        }
        return destination;
    }

    public static String sanitizeName(String name) {
        String result = name
                .replace("\\", "_")
                .replace("/", "_")
                .replace(":", "_")
                .replace("*", "_")
                .replace("?", "_")
                .replace("\"", "_")
                .replace("<", "_")
                .replace(">", "_")
                .replace("|", "_")
                .trim();

        if (result.isEmpty()) {
            result = "Nouveau";
        }
        return result;
    }

    public static SkinEntry loadSkin(Path file) {
        Path cacheKey = file.toAbsolutePath().normalize();
        SkinEntry cached = skinCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        try {
            String json = Files.readString(file);
            JsonObject object = GSON.fromJson(json, JsonObject.class);

            if (object == null) {
                return null;
            }

            String name = object.has("name") ? object.get("name").getAsString() : removeExtension(file.getFileName().toString());
            String url = object.has("url") ? object.get("url").getAsString() : "";
            String model = object.has("model") ? object.get("model").getAsString() : "steve";
            int heightCm = object.has("heightCm")
                    ? Math.clamp(
                    object.get("heightCm").getAsInt(),
                    SizeManager.MIN_HEIGHT_CM,
                    SizeManager.MAX_HEIGHT_CM
            )
                    : SizeManager.DEFAULT_HEIGHT_CM;
            int keyCode = object.has("keyCode") ? object.get("keyCode").getAsInt() : -1;
            int removedPartsMask = object.has("removedPartsMask")
                    ? object.get("removedPartsMask").getAsInt() & 0xF
                    : 0;

            SkinEntry skin = new SkinEntry(file, name, url, model, heightCm, keyCode, removedPartsMask);
            skinCache.put(cacheKey, skin);
            return skin;

        } catch (Exception e) {
            MultiSkinMod.LOGGER.error("Impossible de lire {}", file, e);
            return null;
        }
    }

    public static List<SkinEntry> getAllSkins() {
        List<SkinEntry> result = new ArrayList<>();
        collectSkins(root(), result);
        return result;
    }

    public static SkinEntry findSkinWithKeyCode(int keyCode, Path excludedFile) {
        if (keyCode < 0) {
            return null;
        }

        Path excluded = excludedFile == null ? null : excludedFile.toAbsolutePath().normalize();
        for (SkinEntry skin : getAllSkins()) {
            if (skin.keyCode() == keyCode
                    && (excluded == null || !skin.file().toAbsolutePath().normalize().equals(excluded))) {
                return skin;
            }
        }
        return null;
    }

    public static void updateSkinKeyCode(SkinEntry skin, int keyCode) throws IOException {
        String json = Files.readString(skin.file());
        JsonObject object = GSON.fromJson(json, JsonObject.class);
        if (object == null) {
            throw new IOException("Impossible de lire le skin " + skin.name() + ".");
        }

        object.addProperty("keyCode", keyCode);
        Files.writeString(skin.file(), GSON.toJson(object));
        invalidateSkinCache(skin.file());
    }

    private static void collectSkins(Path folder, List<SkinEntry> result) {
        for (Path path : getContents(folder)) {
            if (Files.isDirectory(path)) {
                collectSkins(path, result);
            } else {
                SkinEntry skin = loadSkin(path);
                if (skin != null) {
                    result.add(skin);
                }
            }
        }
    }

    private static String removeExtension(String name) {
        int index =
                name.lastIndexOf('.');

        if (index <= 0) {
            return name;
        }

        return name.substring(0, index);
    }

    private static String toRelative(Path path) {
        return normalizeRelative(root().toAbsolutePath().normalize().relativize(path.toAbsolutePath().normalize()).toString());
    }

    public static void renameOrderPath(Path oldPath, Path newPath) {
        invalidateSkinCache(oldPath);
        Map<String, List<String>> orders = loadOrders();

        String oldRelative = toRelative(oldPath);
        String newRelative = toRelative(newPath);

        Path oldParent = oldPath.getParent();
        Path newParent = newPath.getParent();

        if (oldParent == null || newParent == null) {
            return;
        }

        String oldParentRelative = oldParent.equals(root()) ? "" : toRelative(oldParent);
        String newParentRelative = newParent.equals(root()) ? "" : toRelative(newParent);

        String oldName = oldPath.getFileName().toString();
        String newName = newPath.getFileName().toString();

        if (oldParentRelative.equals(newParentRelative)) {
            List<String> order = orders.computeIfAbsent(oldParentRelative, key -> new ArrayList<>());

            int index = order.indexOf(oldName);

            order.remove(oldName);

            if (index < 0 || index > order.size()) {
                index = order.size();
            }

            order.remove(newName);
            order.add(index, newName);

        } else {

            List<String> oldOrder =
                    orders.get(oldParentRelative);

            if (oldOrder != null) {
                oldOrder.remove(oldName);
            }

            List<String> newOrder = orders.computeIfAbsent(newParentRelative, key -> new ArrayList<>());

            newOrder.remove(newName);
            newOrder.add(newName);
        }

        renameOrderPrefixes(orders, oldRelative, newRelative);
        saveOrders(orders);
    }

    private static String normalizeRelative(String path) {
        return path.replace('\\', '/');
    }
}