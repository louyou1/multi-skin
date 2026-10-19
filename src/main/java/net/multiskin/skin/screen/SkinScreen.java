package net.multiskin.skin.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.multiskin.MultiSkinMod;
import net.multiskin.bodyparts.BodyPartClient;
import net.multiskin.skin.SkinClient;
import net.multiskin.skin.SkinNetworking;
import net.multiskin.skin.size.SizeManager;
import org.lwjgl.glfw.GLFW;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SkinScreen extends Screen {
    private final MinecraftClient client = MinecraftClient.getInstance();
    private SkinLibrary.SkinEntry selectedSkin;
    private SkinLibrary.SkinEntry lastRequestedSkin;
    private Path selectedFolder;
    private static final Set<Path> openedFolders = new HashSet<>();
    private int scroll = 0;
    private static final int ROW_HEIGHT = 22;
    private static final int TREE_BOTTOM_OFFSET = 15;

    private float previewYaw = 150.0F;
    private boolean rotatingPreview = false;

    private Path draggingPath;
    private Path dragTargetFolder;
    private Path dragInsertTarget;
    private boolean dragInsertBefore;
    private boolean dragging;
    private double dragStartX;
    private double dragStartY;
    private TextFieldWidget searchField;
    private static String searchText = "";
    private static String searchTextLower = "";
    private final Map<Path, Boolean> searchMatchCache = new HashMap<>();
    private final List<ButtonWidget> buttons = new ArrayList<>();
    private int cachedTreeHeight = -1;
    private int cachedMaxScroll = -1;

    private boolean contextMenuRoot;
    private SkinLibrary.SkinEntry contextMenuSkin;
    private Path contextMenuFolder;
    private int contextMenuX;
    private int contextMenuY;

    public SkinScreen() {
        super(Text.translatable("screen.multi-skin.skin.title"));
        openedFolders.add(SkinLibrary.getRoot());
    }

    public static void initialize() {}

    protected void init() {
        super.init();
        clearChildren();
        buttons.clear();
        searchMatchCache.clear();
        invalidateTreeLayout();

        searchField = new TextFieldWidget(this.textRenderer, 25, 30, this.width / 2 - 40, 20,
                Text.translatable("screen.multi-skin.skin.search"));

        searchField.setPlaceholder(Text.translatable("screen.multi-skin.skin.search_placeholder"));
        searchField.setChangedListener(text -> {
            searchText = text.trim();
            searchTextLower = searchText.toLowerCase();
            searchMatchCache.clear();
            scroll = 0;
            invalidateTreeLayout();
        });

        searchField.setText(searchText);
        searchTextLower = searchText.toLowerCase();
        addDrawableChild(searchField);

        ButtonWidget refreshButton = ButtonWidget.builder(
                        Text.translatable("screen.multi-skin.skin.refresh"),
                        button -> SkinClient.refreshSkins()
                )
                .dimensions(this.width - 200, this.height - 35, 90, 20).build();
        buttons.add(refreshButton);
        addDrawableChild(refreshButton);

        ButtonWidget resetButton = ButtonWidget.builder(Text.translatable("screen.multi-skin.skin.reset"), button -> resetSkin())
                .dimensions(this.width - 100, this.height - 35, 85, 20).build();

        buttons.add(resetButton);
        addDrawableChild(resetButton);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);

        int left = 20;
        int top = 59;
        int width = this.width / 2 - 25;
        int bottom = this.height - TREE_BOTTOM_OFFSET;

        context.fill(left - 5, top - 5, left + width, bottom, 0xFF202020);
        drawPreview(context);

        if (dragging && draggingPath != null) {
            String name = getDisplayName(draggingPath);

            context.drawText(this.textRenderer,
                    Text.translatable("screen.multi-skin.skin.moving", name),
                    mouseX + 10, mouseY + 10, 0xFFFFFFFF, true);
        }

        for (ButtonWidget button : buttons) {
            button.render(context, mouseX, mouseY, delta);
        }

        context.enableScissor(left - 5, top, left + width, bottom);
        drawTree(context, SkinLibrary.getRoot(), left, top + 5 - scroll, width, 0, mouseX, mouseY);
        context.disableScissor();

        context.fill(left - 5, 20, left + width, top, 0xFF303030);

        if (SkinLibrary.getContents(SkinLibrary.getRoot()).isEmpty()) {
            Text emptyText = Text.translatable("screen.multi-skin.skin.empty");
            Text hintText = Text.translatable("screen.multi-skin.skin.empty_hint");
            int centerX = left + width / 2;
            context.drawText(this.textRenderer, emptyText,
                    centerX - this.textRenderer.getWidth(emptyText) / 2, top + 45, 0xFFFFFFFF, true);
            context.drawText(this.textRenderer, hintText,
                    centerX - this.textRenderer.getWidth(hintText) / 2, top + 62, 0xFFAAAAAA, false);
        }

        searchField.render(context, mouseX, mouseY, delta);
        drawScrollBar(context, left, width, top, bottom);

        renderContextMenu(context, mouseX, mouseY);
    }

    private int drawTree(DrawContext context, Path folder, int x, int y, int width, int depth, int mouseX, int mouseY) {
        int currentY = y;
        int indent = depth * 14;
        int visibleTop = 70;
        int visibleBottom = this.height - TREE_BOTTOM_OFFSET;
        int rowRight = x + width - 4;
        for (Path path : SkinLibrary.getContents(folder)) {
            if (!matchesSearch(path)) {
                continue;
            }

            boolean visible = currentY + ROW_HEIGHT >= visibleTop && currentY <= visibleBottom;
            boolean directory = Files.isDirectory(path);
            boolean selected = path.equals(selectedFolder) || (selectedSkin != null && path.equals(selectedSkin.file()));
            SkinLibrary.SkinEntry skin = directory ? null : SkinLibrary.loadSkin(path);
            boolean applied = !directory && isAppliedSkin(skin);
            boolean hovered = mouseX >= x + indent && mouseX < rowRight && mouseY >= currentY - 2 && mouseY < currentY + 20;
            boolean dropFolder = dragging && directory && path.equals(dragTargetFolder);
            if (visible) {
                if (dropFolder) {
                    context.fill(x + indent, currentY - 2, rowRight, currentY + 20, 0xFF406040);

                } else if (applied && selected) {
                    context.fill(x + indent, currentY - 2, rowRight, currentY + 20, 0xFF3F7A4A);

                } else if (applied && hovered) {
                    context.fill(x + indent, currentY - 2, rowRight, currentY + 20, 0xFF326B3F);

                } else if (applied) {
                    context.fill(x + indent, currentY - 2, rowRight, currentY + 20, 0xFF23452C);

                } else if (selected) {
                    context.fill(x + indent, currentY - 2, rowRight, currentY + 20, 0xFF505050);

                } else if (hovered) {
                    context.fill(x + indent, currentY - 2, rowRight, currentY + 20, 0xFF303030);
                }

                if (dragging && path.equals(dragInsertTarget)) {
                    int lineY = dragInsertBefore ? currentY - 2 : currentY + ROW_HEIGHT - 2;
                    context.fill(x + indent, lineY, rowRight, lineY + 2, 0xFFFFFFFF);
                }

                if (directory) {
                    boolean opened = openedFolders.contains(path);
                    boolean hasContents = !SkinLibrary.getContents(path).isEmpty();
                    String arrow = hasContents ? (opened ? "▼ " : "▶ ") : "";
                    String name = path.getFileName().toString();
                    context.drawText(this.textRenderer, arrow + "📁 " + name, x + indent + 4, currentY + 7, 0xFFFFFFFF, false);

                } else {
                    String name = skin != null ? skin.name() : path.getFileName().toString();

                    if (skin != null) {
                        Identifier thumbnail = SkinClient.getSkinThumbnail(path, skin.url());
                        if (thumbnail != null) {
                            context.drawTexture(thumbnail, x + indent + 2, currentY, 0, 0, 18, 18, 18, 18);
                        }
                    }

                    int nameX = x + indent + 24;
                    context.drawText(this.textRenderer, name, nameX, currentY + 6, 0xFFFFFFFF, false);

                    if (applied) {
                        int badgeX = nameX + this.textRenderer.getWidth(name) + 5;
                        Text appliedLabel = Text.translatable("screen.multi-skin.skin.equipped");
                        int badgeWidth = this.textRenderer.getWidth(appliedLabel) + 8;
                        context.fill(badgeX, currentY + 2, badgeX + badgeWidth, currentY + 16, 0xFF3C8D50);
                        context.drawText(this.textRenderer, appliedLabel, badgeX + 4, currentY + 6, 0xFFFFFFFF, false);
                    }
                }
            }

            currentY += ROW_HEIGHT;
            if (directory && (openedFolders.contains(path) || !searchText.isEmpty())) {
                currentY = drawTree(context, path, x, currentY, width, depth + 1, mouseX, mouseY);
            }
        }
        return currentY;
    }

    private int getTreeHeight(Path folder) {
        if (!Files.isDirectory(folder)) {
            return 0;
        }

        int height = 0;
        for (Path path : SkinLibrary.getContents(folder)) {
            height += ROW_HEIGHT;
            if (Files.isDirectory(path) && openedFolders.contains(path)) {
                height += getTreeHeight(path);
            }
        }

        return height;
    }

    private int getTotalTreeHeight() {
        if (cachedTreeHeight < 0) {
            cachedTreeHeight = getTreeHeight(SkinLibrary.getRoot());
        }
        return cachedTreeHeight;
    }

    private int getMaxScroll() {
        if (cachedMaxScroll < 0) {
            int totalHeight = getTotalTreeHeight();
            int visibleHeight = this.height - TREE_BOTTOM_OFFSET - 59;
            cachedMaxScroll = Math.max(0, totalHeight - visibleHeight);
        }
        return cachedMaxScroll;
    }

    private void invalidateTreeLayout() {
        cachedTreeHeight = -1;
        cachedMaxScroll = -1;
    }

    private void drawScrollBar(DrawContext context, int left, int width, int top, int bottom) {
        int totalHeight = getTotalTreeHeight();
        int visibleHeight = bottom - top;
        if (totalHeight <= visibleHeight) {
            return;
        }

        int barX = left + width - 5;
        context.fill(barX, top, barX + 3, bottom, 0xFF101010);

        int thumbHeight = Math.max(20, visibleHeight * visibleHeight / totalHeight);
        int maxScroll = getMaxScroll();
        int thumbY = top + ((bottom - top - thumbHeight) * scroll / Math.max(1, maxScroll));
        context.fill(barX, thumbY, barX + 3, thumbY + thumbHeight, 0xFFAAAAAA);
    }

    private void drawPreview(DrawContext context) {
        if (client == null || client.player == null) {
            return;
        }

        int left = this.width / 2 + 10;
        int right = this.width - 15;
        int top = 25;
        int bottom = this.height - 45;
        int panelWidth = right - left;

        context.fill(left - 5, top - 5, right, bottom, 0xFF202020);
        context.fill(left - 5, top - 5, right, top + 34, 0xFF303030);

        String previewName;
        String previewModel;
        int previewHeightCm;

        if (selectedSkin != null) {
            previewName = selectedSkin.name();
            previewModel = selectedSkin.model().equalsIgnoreCase("slim") ? "Slim" : "Steve";
            previewHeightCm = selectedSkin.heightCm();

        } else {
            previewName = client.player.getName().getString();
            previewModel = client.player.getSkinTextures().model() == net.minecraft.client.util.SkinTextures.Model.SLIM ? "Slim" : "Steve";
            previewHeightCm = SkinClient.getSkinHeightCm(client.player.getUuid());
        }

        int nameWidth = this.textRenderer.getWidth(previewName);
        context.drawText(this.textRenderer, previewName, left + (panelWidth - nameWidth) / 2, top + 4, 0xFFFFFFFF, true);

        Text previewModelLabel = Text.translatable("screen.multi-skin.skin.preview_model",
                Text.translatable(previewModel.equals("Slim")
                        ? "screen.multi-skin.skin_editor.model.slim"
                        : "screen.multi-skin.skin_editor.model.steve"),
                previewHeightCm);
        int previewModelLabelWidth = this.textRenderer.getWidth(previewModelLabel);
        context.drawText(this.textRenderer, previewModelLabel,
                left + (panelWidth - previewModelLabelWidth) / 2, top + 18, 0xFFAAAAAA, false);

        int previewTop = top + 34;
        int previewHeight = bottom - previewTop;
        int centerX = (left + right) / 2;

        int size = (int) (Math.min(panelWidth, previewHeight) * 0.42F
                * (previewHeightCm / (float) SizeManager.DEFAULT_HEIGHT_CM));
        size = Math.max(45, size);

        int entityY = bottom - 10;
        int estimatedTop = entityY - (int) (size * 2.0F);
        int minimumTop = previewTop + 4;
        if (estimatedTop < minimumTop) {
            entityY += minimumTop - estimatedTop;
        }
        float yaw = (float) Math.toRadians(previewYaw);

        Quaternionf rotation = new Quaternionf().rotateZ((float) Math.PI).rotateY(yaw);
        Quaternionf cameraRotation = new Quaternionf().rotateX(0);

        float oldYaw = client.player.getYaw();
        float oldPitch = client.player.getPitch();
        float oldHeadYaw = client.player.headYaw;
        float oldBodyYaw = client.player.bodyYaw;

        client.player.setYaw(0.0F);
        client.player.setPitch(0.0F);
        client.player.headYaw = 0.0F;
        client.player.bodyYaw = 0.0F;

        context.enableScissor(left, previewTop, right, bottom);
        int previewEntityY = entityY;
        int previewEntitySize = size;
        if (selectedSkin == null) {
            InventoryScreen.drawEntity(context, centerX, previewEntityY, previewEntitySize,
                    new Vector3f(0.0F, 0.0F, 0.0F), rotation, cameraRotation, client.player);
        } else {
            BodyPartClient.withPreviewMask(selectedSkin.removedPartsMask(), () ->
                    InventoryScreen.drawEntity(context, centerX, previewEntityY, previewEntitySize,
                            new Vector3f(0.0F, 0.0F, 0.0F), rotation, cameraRotation, client.player));
        }
        context.disableScissor();

        client.player.setYaw(oldYaw);
        client.player.setPitch(oldPitch);
        client.player.headYaw = oldHeadYaw;
        client.player.bodyYaw = oldBodyYaw;
    }

    private boolean isInsidePreview(double mouseX, double mouseY) {
        int left = this.width / 2 + 10;
        int right = this.width - 15;
        int top = 25;
        int bottom = this.height - 55;

        return mouseX >= left && mouseX <= right && mouseY >= top && mouseY <= bottom;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int left = 15;
        int width = this.width / 2 - 25;
        int top = 59;
        int bottom = this.height - TREE_BOTTOM_OFFSET;

        if (mouseX >= left && mouseX <= left + width && mouseY >= top && mouseY <= bottom) {
            int amount = verticalAmount > 0 ? -ROW_HEIGHT * 2 : ROW_HEIGHT * 2;
            scroll += amount;
            scroll = Math.clamp(scroll, 0, getMaxScroll());

            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (contextMenuSkin != null || contextMenuFolder != null || contextMenuRoot) {
            int x = contextMenuX;
            int y = contextMenuY;

            int width = 120;
            int height;

            if (contextMenuRoot) {
                height = 46;
            } else if (contextMenuFolder != null) {
                height = 90;
            } else {
                height = 112;
            }

            if (x + width > this.width) {
                x = this.width - width;
            }

            if (y + height > this.height) {
                y = this.height - height;
            }

            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                if (mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height) {
                    int option = (int) ((mouseY - y - 1) / 22);

                    if (contextMenuFolder != null) {
                        Path folder = contextMenuFolder;
                        switch (option) {
                            case 0 -> {
                                SkinSounds.click();
                                selectedFolder = folder;
                                selectedSkin = null;
                                closeContextMenu();
                                client.setScreen(new SkinEditorScreen(this, null, folder));
                            }

                            case 1 -> {
                                SkinSounds.click();
                                selectedFolder = folder;
                                selectedSkin = null;
                                closeContextMenu();
                                client.setScreen(new SkinFolderEditorScreen(this, folder));
                            }

                            case 2 -> {
                                SkinSounds.click();
                                closeContextMenu();
                                client.setScreen(new SkinFolderEditorScreen(this, folder.getParent(), folder));
                            }

                            case 3 -> {
                                SkinSounds.click();
                                selectedFolder = folder;
                                selectedSkin = null;
                                closeContextMenu();
                                deleteSelected();
                            }
                        }
                    } else if (contextMenuRoot) {
                        switch (option) {
                            case 0 -> {
                                SkinSounds.click();
                                closeContextMenu();
                                client.setScreen(new SkinEditorScreen(this, null, SkinLibrary.getRoot()));
                            }

                            case 1 -> {
                                SkinSounds.click();
                                closeContextMenu();
                                client.setScreen(new SkinFolderEditorScreen(this, SkinLibrary.getRoot()));
                            }
                        }

                    } else {
                        switch (option) {
                            case 0 -> {
                                SkinLibrary.SkinEntry skin = contextMenuSkin;

                                if (SkinClient.isThumbnailValid(skin.file())) {
                                    selectedSkin = skin;
                                    SkinSounds.click();
                                    applySelectedSkin();
                                }

                                closeContextMenu();
                            }

                            case 1 -> {
                                SkinSounds.click();
                                SkinLibrary.SkinEntry skin = contextMenuSkin;
                                closeContextMenu();
                                client.setScreen(new SkinEditorScreen(this, skin, skin.file().getParent()));
                            }

                            case 2 -> {
                                SkinSounds.click();
                                duplicateSkin(contextMenuSkin);
                                closeContextMenu();
                            }

                            case 3 -> {
                                SkinSounds.click();
                                copySkinUrl(contextMenuSkin);
                                closeContextMenu();
                            }

                            case 4 -> {
                                SkinSounds.click();
                                selectedSkin = contextMenuSkin;
                                closeContextMenu();
                                deleteSelected();
                            }
                        }
                    }
                    return true;
                }
                closeContextMenu();
                return true;
            }

            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                closeContextMenu();
                return true;
            }

            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && isInsidePreview(mouseX, mouseY)) {
            rotatingPreview = true;
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            int left = 15;
            int top = 59;
            int width = this.width / 2 - 25;
            int bottom = this.height - TREE_BOTTOM_OFFSET;
            if (mouseX >= left && mouseX <= left + width && mouseY >= top && mouseY <= bottom) {
                TreeRow row = findRowAt(SkinLibrary.getRoot(), top + 5 - scroll, mouseY);
                if (row != null) {
                    if (Files.isDirectory(row.path())) {
                        selectedFolder = row.path();
                        openFolderContextMenu(row.path(), (int) mouseX, (int) mouseY);

                        return true;
                    }

                    SkinLibrary.SkinEntry skin = SkinLibrary.loadSkin(row.path());

                    if (skin != null) {
                        selectedSkin = skin;
                        selectedFolder = null;

                        if (SkinClient.isThumbnailValid(row.path())) {
                            SkinClient.loadPreviewSkin(skin.url(), skin.model());
                        }

                        openContextMenu(skin, (int) mouseX, (int) mouseY);

                        return true;
                    }
                }

                if (isInsideTree(mouseX, mouseY)) {
                    openRootContextMenu((int) mouseX, (int) mouseY);
                    return true;
                }
                return true;
            }
            return true;
        }

        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        int left = 15;
        int top = 59;
        int width = this.width / 2 - 25;
        int bottom = this.height - TREE_BOTTOM_OFFSET;

        if (mouseX < left || mouseX > left + width || mouseY < top || mouseY > bottom) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        TreeRow row = findRowAt(SkinLibrary.getRoot(), top + 5 - scroll, mouseY);

        if (row == null) {
            if (!dragging && mouseY >= top) {
                draggingPath = null;
            }
            return true;
        }
        draggingPath = row.path();
        dragStartX = mouseX;
        dragStartY = mouseY;
        dragging = false;
        dragTargetFolder = null;
        dragInsertTarget = null;

        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (rotatingPreview && button == 0) {
            previewYaw += (float) deltaX * 0.5F;
            return true;
        }

        if (draggingPath != null
                        && button == 0) {
            if (!dragging) {
                double distanceX = mouseX - dragStartX;
                double distanceY = mouseY - dragStartY;
                double distanceSquared = distanceX * distanceX + distanceY * distanceY;

                if (distanceSquared >= 16.0) {
                    dragging = true;
                }
            }

            if (dragging) {
                updateDropTarget(mouseX, mouseY);
                return true;
            }
        }

        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && rotatingPreview) {
            rotatingPreview = false;
            return true;
        }

        if (button != 0) {
            return super.mouseReleased(mouseX, mouseY, button);
        }

        if (dragging && draggingPath != null) {
            updateDropTarget(mouseX, mouseY);
            performDrop();

        } else if (draggingPath != null) {
            Path clicked = draggingPath;
            if (Files.isDirectory(clicked)) {
                if (!SkinLibrary.getContents(clicked).isEmpty()) {
                    if (openedFolders.contains(clicked)) {
                        openedFolders.remove(clicked);

                    } else {
                        openedFolders.add(clicked);
                    }
                    invalidateTreeLayout();
                }

                SkinSounds.click();
                selectedFolder = clicked;

            } else {
                SkinLibrary.SkinEntry skin = SkinLibrary.loadSkin(clicked);
                if (skin != null) {
                    selectedSkin = skin;
                    selectedFolder = null;

                    SkinSounds.click();

                    SkinClient.loadPreviewSkin(skin.url(), skin.model());
                }
            }

            scroll = Math.min(scroll, getMaxScroll());
        }

        draggingPath = null;
        dragTargetFolder = null;
        dragInsertTarget = null;
        dragging = false;
        return true;
    }

    private void updateDropTarget(double mouseX, double mouseY) {
        dragTargetFolder = null;
        dragInsertTarget = null;

        TreeRow row = findRowAt(SkinLibrary.getRoot(), 59 + 5 - scroll, mouseY);
        if (row == null) {
            if (isInsideTree(mouseX, mouseY)) {
                dragTargetFolder = SkinLibrary.getRoot();
            }
            return;
        }

        Path target = row.path();
        int relativeY = (int) mouseY - row.y();
        if (Files.isDirectory(target)) {
            if (relativeY >= 7 && relativeY <= 15) {
                if (!isInvalidFolderTarget(target)) {
                    dragTargetFolder = target;
                }
                return;
            }
        }

        dragInsertTarget = target;
        dragInsertBefore = relativeY < ROW_HEIGHT / 2;

        if (target.equals(draggingPath)) {
            dragInsertTarget = null;
        }
    }

    private boolean isInsideTree(double mouseX, double mouseY) {
        int left = 15;
        int width = this.width / 2 - 25;
        int top = 59;
        int bottom = this.height - TREE_BOTTOM_OFFSET;

        return mouseX >= left && mouseX <= left + width && mouseY >= top && mouseY <= bottom;
    }

    private boolean isInvalidFolderTarget(Path target) {
        if (draggingPath == null || !Files.isDirectory(draggingPath)) {
            return false;
        }

        Path source = draggingPath.toAbsolutePath().normalize();
        Path normalizedTarget = target.toAbsolutePath().normalize();

        return normalizedTarget.startsWith(source);
    }

    private void performDrop() {
        try {
            if (draggingPath == null) {
                return;
            }

            if (dragTargetFolder != null) {
                if (draggingPath.getParent() != null && draggingPath.getParent().equals(dragTargetFolder)) {
                    return;
                }

                boolean wasOpen = Files.isDirectory(draggingPath) && openedFolders.contains(draggingPath);
                Path oldPath = draggingPath;
                SkinLibrary.move(draggingPath, dragTargetFolder);
                invalidateTreeLayout();
                Path newPath = dragTargetFolder.resolve(oldPath.getFileName());
                if (wasOpen) {
                    openedFolders.remove(oldPath);
                    openedFolders.add(newPath);
                }

                openedFolders.add(dragTargetFolder);
                return;
            }

            if (dragInsertTarget != null) {
                if (dragInsertTarget.equals(draggingPath)) {
                    return;
                }

                if (dragInsertBefore) {
                    SkinLibrary.moveBefore(draggingPath, dragInsertTarget);
                } else {
                    SkinLibrary.moveAfter(draggingPath, dragInsertTarget);
                }
                invalidateTreeLayout();
            }
        } catch (Exception e) {
            MultiSkinMod.LOGGER.error("Impossible d'effectuer le déplacement.", e);
        }
    }

    private TreeRow findRowAt(Path folder, int startY, double mouseY) {
        int currentY = startY;
        for (Path path : SkinLibrary.getContents(folder)) {
            if (!matchesSearch(path)) {
                continue;
            }

            if (mouseY >= currentY && mouseY < currentY + ROW_HEIGHT) {
                return new TreeRow(path, currentY);
            }

            currentY += ROW_HEIGHT;
            if (Files.isDirectory(path) && (openedFolders.contains(path) || !searchText.isEmpty())) {
                TreeRow result = findRowAt(path, currentY, mouseY);

                if (result != null) {
                    return result;
                }
                currentY += getTreeHeight(path);
            }
        }
        return null;
    }

    private record TreeRow(Path path, int y) {}

    private boolean isAppliedSkin(SkinLibrary.SkinEntry skin) {
        if (skin == null || client.player == null) {
            return false;
        }

        String appliedUrl = SkinClient.getSkinUrl(client.player.getUuid());
        String appliedModel = SkinClient.getAppliedSkinModel(client.player.getUuid());
        return appliedUrl != null
                && appliedUrl.equals(skin.url())
                && (appliedModel == null || appliedModel.equalsIgnoreCase(skin.model()))
                && SkinClient.getSkinHeightCm(client.player.getUuid()) == skin.heightCm();
    }

    private String getDisplayName(Path path) {
        if (Files.isDirectory(path)) {
            return path.getFileName().toString();
        }

        SkinLibrary.SkinEntry skin = SkinLibrary.loadSkin(path);
        if (skin != null) {
            return skin.name();
        }

        return path.getFileName().toString();
    }

    private void deleteSelected() {
        try {
            if (selectedSkin != null) {
                SkinClient.removeThumbnail(selectedSkin.file());
                SkinLibrary.delete(selectedSkin.file());
                invalidateTreeLayout();
                selectedSkin = null;
                SkinClient.clearPreview();
                scroll = Math.min(scroll, getMaxScroll());
                clearAndReinit();
                return;
            }

            if (selectedFolder != null) {
                if (selectedFolder.equals(SkinLibrary.getRoot())) {
                    return;
                }

                int elementCount = countFolderElements(selectedFolder);
                if (elementCount == 0) {
                    SkinLibrary.delete(selectedFolder);
                    invalidateTreeLayout();
                    selectedFolder = null;
                    scroll = Math.min(scroll, getMaxScroll());
                    clearAndReinit();
                    return;
                }
                Path folderToDelete = selectedFolder;
                client.setScreen(new DeleteConfirmationScreen(this, folderToDelete, elementCount));
            }

        } catch (Exception e) {
            MultiSkinMod.LOGGER.error("Impossible de supprimer l'élément.", e);
        }
    }

    private int countFolderElements(Path folder) {
        if (folder == null || !Files.isDirectory(folder)) {
            return 0;
        }

        int count = 0;
        try {
            try (var stream = Files.list(folder)) {
                for (Path path : stream.toList()) {
                    if (path.getFileName().toString().equals(".order.json")) {
                        continue;
                    }

                    count++;
                    if (Files.isDirectory(path)) {
                        count += countFolderElements(path);
                    }
                }
            }

        } catch (Exception e) {
            MultiSkinMod.LOGGER.error("Impossible de compter le contenu de {}", folder, e);
        }
        return count;
    }

    private void confirmDeleteFolder(Path folder) {
        try {
            SkinLibrary.delete(folder);
            invalidateTreeLayout();

            openedFolders.removeIf(path -> !Files.exists(path));
            selectedFolder = null;
            selectedSkin = null;

            SkinClient.clearPreview();

            scroll = Math.min(scroll, getMaxScroll());
            client.setScreen(this);

            clearAndReinit();

        } catch (Exception e) {
            MultiSkinMod.LOGGER.error("Impossible de supprimer le dossier.", e);
            client.setScreen(this);
        }
    }

    private void clearAndReinit() {
        selectedSkin = null;
        clearChildren();
        init();
    }

    void onSkinEdited(SkinLibrary.SkinEntry oldSkin, SkinLibrary.SkinEntry updatedSkin) {
        if (selectedSkin != null
                && selectedSkin.file().toAbsolutePath().normalize()
                .equals(oldSkin.file().toAbsolutePath().normalize())) {
            selectedSkin = updatedSkin;
            SkinClient.clearPreview();
            SkinClient.loadPreviewSkin(updatedSkin.url(), updatedSkin.model());
        }
        searchMatchCache.clear();
        invalidateTreeLayout();
    }

    @Override
    public void close() {
        SkinClient.clearPreview();
        applySelectedSkin();
        super.close();
    }

    private void applySelectedSkin() {
        if (selectedSkin == null || selectedSkin.equals(lastRequestedSkin)) {
            return;
        }

        if (client.player != null && client.player.networkHandler != null) {
            SkinClient.applySkinSource(
                    selectedSkin.url(),
                    selectedSkin.model(),
                    selectedSkin.heightCm(),
                    selectedSkin.removedPartsMask()
            );
            lastRequestedSkin = selectedSkin;
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (SkinKeybinds.OPEN_MENU.matchesKey(keyCode, scanCode)) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void openContextMenu(SkinLibrary.SkinEntry skin, int mouseX, int mouseY) {
        contextMenuSkin = skin;
        contextMenuX = mouseX;
        contextMenuY = mouseY;
    }

    private void closeContextMenu() {
        contextMenuSkin = null;
        contextMenuFolder = null;
        contextMenuRoot = false;
    }

    private void renderContextMenu(DrawContext context, int mouseX, int mouseY) {
        if (contextMenuSkin == null && contextMenuFolder == null && !contextMenuRoot) {
            return;
        }

        int x = contextMenuX;
        int y = contextMenuY;

        int width = 120;
        int height;

        if (contextMenuRoot) {
            height = 46;
        } else if (contextMenuFolder != null) {
            height = 90;
        } else {
            height = 112;
        }

        if (x + width > this.width) {
            x = this.width - width;
        }

        if (y + height > this.height) {
            y = this.height - height;
        }

        context.fill(x, y, x + width, y + height, 0xFF202020);

        context.fill(x, y, x + width, y + 1, 0xFFFFFFFF);
        context.fill(x, y + height - 1, x + width, y + height, 0xFFFFFFFF);
        context.fill(x, y, x + 1, y + height, 0xFFFFFFFF);
        context.fill(x + width - 1, y, x + width, y + height, 0xFFFFFFFF);

        if (contextMenuFolder != null) {
            drawContextMenuButton(context, Text.translatable("screen.multi-skin.skin.menu.new_skin"),
                    x, y + 1, mouseX, mouseY);
            drawContextMenuButton(context, Text.translatable("screen.multi-skin.skin.menu.new_folder"),
                    x, y + 23, mouseX, mouseY);
            drawContextMenuButton(context, Text.translatable("screen.multi-skin.skin.menu.rename"),
                    x, y + 45, mouseX, mouseY);
            drawContextMenuButton(context, Text.translatable("screen.multi-skin.skin.menu.delete"),
                    x, y + 67, mouseX, mouseY);

        } else if (contextMenuRoot) {
            drawContextMenuButton(context, Text.translatable("screen.multi-skin.skin.menu.new_skin"),
                    x, y + 1, mouseX, mouseY);
            drawContextMenuButton(context, Text.translatable("screen.multi-skin.skin.menu.new_folder"),
                    x, y + 23, mouseX, mouseY);

        } else {
            drawContextMenuButton(context, Text.translatable("screen.multi-skin.skin.menu.apply"),
                    x, y + 1, mouseX, mouseY);
            drawContextMenuButton(context, Text.translatable("screen.multi-skin.skin.menu.edit"),
                    x, y + 23, mouseX, mouseY);
            drawContextMenuButton(context, Text.translatable("screen.multi-skin.skin.menu.duplicate"),
                    x, y + 45, mouseX, mouseY);
            drawContextMenuButton(context, Text.translatable("screen.multi-skin.skin.menu.copy_url"),
                    x, y + 67, mouseX, mouseY);
            drawContextMenuButton(context, Text.translatable("screen.multi-skin.skin.menu.delete"),
                    x, y + 89, mouseX, mouseY);
        }
    }

    private void drawContextMenuButton(DrawContext context, Text text, int x, int y, int mouseX, int mouseY) {
        int width = 120;
        int height = 22;
        boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        if (hovered) {
            context.fill(x + 1, y, x + width - 1, y + height, 0xFF505050);
        }

        context.drawText(this.textRenderer, text, x + 8, y + 7, 0xFFFFFFFF, false);
    }

    private static class DeleteConfirmationScreen extends Screen {
        private final SkinScreen parent;
        private final Path folder;
        private final int elementCount;
        private ButtonWidget cancelButton;
        private ButtonWidget deleteButton;

        protected DeleteConfirmationScreen(SkinScreen parent, Path folder, int elementCount) {
            super(Text.translatable("screen.multi-skin.skin.delete.title"));
            this.parent = parent;
            this.folder = folder;
            this.elementCount = elementCount;
        }

        @Override
        protected void init() {
            super.init();
            int centerX = this.width / 2;
            int buttonY = this.height / 2 + 35;

            cancelButton = ButtonWidget.builder(
                    Text.translatable("screen.multi-skin.skin_editor.cancel"),
                    button -> MinecraftClient.getInstance().setScreen(parent)
            ).dimensions(centerX - 105, buttonY, 100, 20).build();

            deleteButton = ButtonWidget.builder(
                    Text.translatable("screen.multi-skin.skin.menu.delete"),
                    button -> parent.confirmDeleteFolder(folder)
            ).dimensions(centerX + 5, buttonY, 100, 20).build();

            addDrawableChild(cancelButton);
            addDrawableChild(deleteButton);
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            parent.render(context, mouseX, mouseY, delta);
            context.fill(0, 0, this.width, this.height, 0x99000000);

            int boxWidth = 360;
            int boxHeight = 150;
            int left = (this.width - boxWidth) / 2;
            int top = (this.height - boxHeight) / 2;

            context.fill(left, top, left + boxWidth, top + boxHeight, 0xFF202020);
            context.fill(left, top, left + boxWidth, top + 2, 0xFFFFFFFF);
            context.fill(left, top + boxHeight - 2, left + boxWidth, top + boxHeight, 0xFFFFFFFF);
            context.fill(left, top, left + 2, top + boxHeight, 0xFFFFFFFF);
            context.fill(left + boxWidth - 2, top, left + boxWidth, top + boxHeight, 0xFFFFFFFF);

            Text title = Text.translatable("screen.multi-skin.skin.delete.question");
            context.drawText(this.textRenderer, title, (this.width - this.textRenderer.getWidth(title)) / 2, top + 18, 0xFFFFFFFF, true);

            String folderName = folder.getFileName().toString();
            Text folderText = Text.translatable("screen.multi-skin.skin.delete.folder", folderName);
            context.drawText(
                    this.textRenderer,
                    folderText,
                    (this.width - this.textRenderer.getWidth(folderText)) / 2,
                    top + 43,
                    0xFFCCCCCC,
                    false
            );

            Text countText = Text.translatable(elementCount == 1
                    ? "screen.multi-skin.skin.delete.count.one"
                    : "screen.multi-skin.skin.delete.count.many", elementCount);

            context.drawText(this.textRenderer, countText, (this.width - this.textRenderer.getWidth(countText)) / 2, top + 65, 0xFFFFAA00, false);

            Text warning = Text.translatable("screen.multi-skin.skin.delete.warning");
            context.drawText(this.textRenderer, warning, (this.width - this.textRenderer.getWidth(warning)) / 2, top + 85, 0xFFFF5555, false);

            cancelButton.render(context, mouseX, mouseY, delta);
            deleteButton.render(context, mouseX, mouseY, delta);
        }

        @Override
        public boolean shouldPause() {
            return false;
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                MinecraftClient.getInstance().setScreen(parent);
                return true;
            }

            return super.keyPressed(keyCode, scanCode, modifiers);
        }
    }

    private void resetSkin() {
        if (client.player != null) {
            ClientPlayNetworking.send(new SkinNetworking.SkinResetRequest());
        }

        selectedSkin = null;
        selectedFolder = null;
        lastRequestedSkin = null;

        SkinClient.clearPreview();
    }

    private boolean matchesSearch(Path path) {
        if (searchText.isEmpty()) {
            return true;
        }

        Boolean cached = searchMatchCache.get(path);
        if (cached != null) {
            return cached;
        }

        boolean matches;
        if (!Files.isDirectory(path)) {
            SkinLibrary.SkinEntry skin = SkinLibrary.loadSkin(path);
            if (skin == null) {
                searchMatchCache.put(path, false);
                return false;
            }
            matches = skin.name().toLowerCase().contains(searchTextLower);
            searchMatchCache.put(path, matches);
            return matches;
        }

        try (var stream = Files.list(path)) {
            matches = stream
                    .filter(child -> !child.getFileName().toString().equals(".order.json"))
                    .anyMatch(this::matchesSearch);

        } catch (Exception e) {
            searchMatchCache.put(path, false);
            return false;
        }

        searchMatchCache.put(path, matches);
        return matches;
    }

    private void openFolderContextMenu(Path folder, int mouseX, int mouseY) {
        contextMenuSkin = null;
        contextMenuFolder = folder;
        contextMenuX = mouseX;
        contextMenuY = mouseY;
    }

    private void openRootContextMenu(int mouseX, int mouseY) {
        contextMenuSkin = null;
        contextMenuFolder = null;
        contextMenuRoot = true;

        contextMenuX = mouseX;
        contextMenuY = mouseY;
    }

    private void duplicateSkin(SkinLibrary.SkinEntry skin) {
        if (skin == null) {
            return;
        }

        try {
            String name = skin.name();
            SkinLibrary.saveSkin(
                    skin.file().getParent(),
                    name,
                    skin.url(),
                    skin.model(),
                    skin.heightCm(),
                    -1,
                    skin.removedPartsMask()
            );
            clearAndReinit();

        } catch (Exception e) {
            MultiSkinMod.LOGGER.error("Impossible de dupliquer le skin.", e);
        }
    }

    private void copySkinUrl(SkinLibrary.SkinEntry skin) {
        if (skin == null || skin.url().isEmpty()) {
            return;
        }

        client.keyboard.setClipboard(skin.url());
    }
}