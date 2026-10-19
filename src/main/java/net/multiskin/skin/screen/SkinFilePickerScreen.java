package net.multiskin.skin.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.multiskin.MultiSkinMod;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.stream.Stream;

final class SkinFilePickerScreen extends Screen {
    private static final int ROW_HEIGHT = 22;

    private final Screen parent;
    private final Consumer<Path> onFileSelected;
    private Path directory;
    private TextFieldWidget pathField;
    private List<Path> entries = List.of();
    private String errorMessage;
    private int scroll;
    private boolean draggingScrollbar;
    private int scrollbarDragOffset;

    SkinFilePickerScreen(Screen parent, Path startingPath, Consumer<Path> onFileSelected) {
        super(Text.translatable("screen.multi-skin.skin_editor.browse_title"));
        this.parent = parent;
        this.onFileSelected = onFileSelected;
        this.directory = startingDirectory(startingPath);
        loadEntries();
    }

    private static Path startingDirectory(Path startingPath) {
        Path candidate = startingPath.toAbsolutePath().normalize();
        if (!Files.isDirectory(candidate)) {
            candidate = candidate.getParent();
        }
        if (candidate == null || !Files.isDirectory(candidate)) {
            candidate = Path.of(System.getProperty("user.home", ".")).toAbsolutePath().normalize();
        }
        return candidate;
    }

    @Override
    protected void init() {
        super.init();
        int left = Math.max(10, (width - 420) / 2);
        int top = 28;
        pathField = new TextFieldWidget(textRenderer, left, top, Math.max(120, width - 2 * left - 115), 20,
                Text.translatable("screen.multi-skin.skin_editor.browse_path"));
        pathField.setMaxLength(2048);
        pathField.setText(directory.toString());
        addDrawableChild(pathField);

        addDrawableChild(ButtonWidget.builder(Text.translatable("screen.multi-skin.skin_editor.browse_go"),
                        button -> navigateToPath())
                .dimensions(left + pathField.getWidth() + 5, top, 50, 20)
                .build());
        ButtonWidget upButton = ButtonWidget.builder(Text.literal(".."), button -> goUp())
                .dimensions(left + pathField.getWidth() + 60, top, 50, 20)
                .build();
        upButton.active = directory.getParent() != null;
        addDrawableChild(upButton);

        int listTop = listTop();
        int visibleRows = visibleRows();
        int maxScroll = maxScroll();
        scroll = Math.clamp(scroll, 0, maxScroll);
        for (int row = 0; row < visibleRows && row + scroll < entries.size(); row++) {
            Path entry = entries.get(row + scroll);
            boolean isDirectory = Files.isDirectory(entry);
            String label = isDirectory
                    ? Text.translatable("screen.multi-skin.skin_editor.browse_directory",
                            entry.getFileName().toString()).getString()
                    : entry.getFileName().toString();
            addDrawableChild(ButtonWidget.builder(Text.literal(label), button -> {
                        if (isDirectory) {
                            directory = entry;
                            scroll = 0;
                            loadEntries();
                            clearAndInit();
                        } else {
                            onFileSelected.accept(entry);
                            client.setScreen(parent);
                        }
                    })
                    .dimensions(left, listTop + row * ROW_HEIGHT, width - 2 * left - 12, 20)
                    .build());
        }

        addDrawableChild(ButtonWidget.builder(Text.translatable("screen.multi-skin.skin_editor.cancel"),
                        button -> client.setScreen(parent))
                .dimensions(width / 2 - 45, height - 28, 90, 20)
                .build());
    }

    private void navigateToPath() {
        try {
            Path requestedPath = Path.of(pathField.getText()).toAbsolutePath().normalize();
            if (!Files.isDirectory(requestedPath)) {
                errorMessage = Text.translatable("screen.multi-skin.skin_editor.browse_not_directory").getString();
                return;
            }
            directory = requestedPath;
            scroll = 0;
            loadEntries();
            clearAndInit();
        } catch (RuntimeException e) {
            errorMessage = Text.translatable("screen.multi-skin.skin_editor.browse_invalid_path").getString();
        }
    }

    private void goUp() {
        Path parentPath = directory.getParent();
        if (parentPath != null) {
            directory = parentPath;
            scroll = 0;
            loadEntries();
            clearAndInit();
        }
    }

    private void loadEntries() {
        try (Stream<Path> paths = Files.list(directory)) {
            entries = paths
                    .filter(path -> Files.isDirectory(path) || isImageFile(path))
                    .sorted(Comparator
                            .comparing((Path path) -> !Files.isDirectory(path))
                            .thenComparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .toList();
            errorMessage = null;
        } catch (IOException | SecurityException e) {
            entries = new ArrayList<>();
            errorMessage = Text.translatable("screen.multi-skin.skin_editor.browse_read_error").getString();
            MultiSkinMod.LOGGER.warn("Impossible de lire le dossier {}", directory, e);
        }
    }

    private static boolean isImageFile(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg");
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int updatedScroll = Math.clamp(scroll - (int) Math.signum(verticalAmount), 0, maxScroll());
        if (updatedScroll != scroll) {
            scroll = updatedScroll;
            clearAndInit();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int thumbTop = scrollbarThumbTop();
        int thumbBottom = thumbTop + scrollbarThumbHeight();
        if (button == 0 && maxScroll() > 0
                && mouseX >= scrollbarX() - 3 && mouseX <= scrollbarX() + 10
                && mouseY >= listTop() && mouseY <= listBottom()) {
            draggingScrollbar = true;
            if (mouseY >= thumbTop && mouseY <= thumbBottom) {
                scrollbarDragOffset = (int) mouseY - thumbTop;
            } else {
                scrollbarDragOffset = scrollbarThumbHeight() / 2;
                updateScrollFromMouse(mouseY);
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (draggingScrollbar && button == 0) {
            updateScrollFromMouse(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private int listTop() {
        return 62;
    }

    private int listBottom() {
        return height - 38;
    }

    private int visibleRows() {
        return Math.max(1, (listBottom() - listTop()) / ROW_HEIGHT);
    }

    private int maxScroll() {
        return Math.max(0, entries.size() - visibleRows());
    }

    private int scrollbarX() {
        return width - Math.max(10, (width - 420) / 2) - 8;
    }

    private int scrollbarTrackHeight() {
        return listBottom() - listTop();
    }

    private int scrollbarThumbHeight() {
        if (entries.size() <= visibleRows()) {
            return scrollbarTrackHeight();
        }
        return Math.max(16, scrollbarTrackHeight() * visibleRows() / entries.size());
    }

    private int scrollbarThumbTop() {
        int travel = scrollbarTrackHeight() - scrollbarThumbHeight();
        return listTop() + (maxScroll() == 0 ? 0 : travel * scroll / maxScroll());
    }

    private void updateScrollFromMouse(double mouseY) {
        int travel = scrollbarTrackHeight() - scrollbarThumbHeight();
        if (travel <= 0) {
            return;
        }
        int thumbTop = Math.clamp((int) mouseY - scrollbarDragOffset, listTop(), listTop() + travel);
        int updatedScroll = (thumbTop - listTop()) * maxScroll() / travel;
        if (updatedScroll != scroll) {
            scroll = updatedScroll;
            clearAndInit();
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 10, 0xFFFFFFFF);
        super.render(context, mouseX, mouseY, delta);
        int trackX = scrollbarX();
        int trackTop = listTop();
        int trackBottom = listBottom();
        context.fill(trackX, trackTop, trackX + 6, trackBottom, 0xFF202020);
        context.fill(trackX, trackTop, trackX + 1, trackBottom, 0xFF606060);
        context.fill(trackX + 5, trackTop, trackX + 6, trackBottom, 0xFF606060);
        int thumbColor = draggingScrollbar ? 0xFFFFFFFF : 0xFFB0B0B0;
        int thumbTop = scrollbarThumbTop();
        context.fill(trackX, thumbTop, trackX + 6, thumbTop + scrollbarThumbHeight(), thumbColor);
        if (errorMessage != null) {
            context.drawCenteredTextWithShadow(textRenderer, errorMessage, width / 2, height - 48, 0xFFFF5555);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
