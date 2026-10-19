package net.multiskin.skin.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.multiskin.MultiSkinMod;
import net.multiskin.bodyparts.BodyPart;
import net.multiskin.skin.SkinClient;
import net.multiskin.skin.SkinManager;
import net.multiskin.skin.SkinImageSource;
import net.multiskin.skin.size.SizeManager;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;

public class SkinEditorScreen extends Screen {
    private static final int DIALOG_WIDTH = 300;
    private static final int DIALOG_HEIGHT = 130;
    private static final int BODY_ICON_SCALE = 3;
    private static final Identifier BODY_PART_ICON =
            Identifier.of(MultiSkinMod.MOD_ID, "textures/gui/bodypart_icon.png");

    private final Screen parent;
    private final SkinLibrary.SkinEntry editing;
    private final Path targetFolder;
    private String selectedImagePath;

    private TextFieldWidget nameField;
    private TextFieldWidget urlField;
    private HeightSliderWidget heightSlider;
    private ButtonWidget keyButton;
    private ButtonWidget browseButton;

    private boolean slim;
    private int removedPartsMask;
    private boolean capturingKey;
    private int keyCode;
    private int keyCodeBeforeCapture;

    private String errorMessage;
    private int layoutTop;
    private SkinLibrary.SkinEntry keyConflict;
    private SkinLibrary.SkinEntry pendingSwap;

    public SkinEditorScreen(Screen parent, SkinLibrary.SkinEntry editing, Path targetFolder) {
        super(Text.translatable(editing == null
                ? "screen.multi-skin.skin_editor.new_title"
                : "screen.multi-skin.skin_editor.edit_title"));

        this.parent = parent;
        this.editing = editing;
        this.targetFolder = targetFolder;

        this.slim = editing != null && editing.model().equalsIgnoreCase("slim");
        this.keyCode = editing == null ? -1 : editing.keyCode();
        this.removedPartsMask = editing == null ? 0 : editing.removedPartsMask();
    }

    @Override
    protected void init() {
        super.init();

        int center = this.width / 2;
        layoutTop = Math.max(8, (this.height - 220) / 2);
        int formLeft = center - 155;
        nameField = new TextFieldWidget(this.textRenderer, formLeft, layoutTop + 32, 180, 20,
                Text.translatable("screen.multi-skin.skin_editor.name"));
        nameField.setMaxLength(64);

        if (editing != null) {
            nameField.setText(editing.name());
        }

        addDrawableChild(nameField);
        browseButton = ButtonWidget.builder(Text.literal("📁"), button -> openImageFilePicker())
                .dimensions(formLeft - 25, layoutTop + 68, 20, 20)
                .build();
        browseButton.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(
                Text.translatable("screen.multi-skin.skin_editor.browse")));
        addDrawableChild(browseButton);
        urlField = new TextFieldWidget(this.textRenderer, formLeft, layoutTop + 68, 180, 20,
                Text.translatable("screen.multi-skin.skin_editor.url"));
        urlField.setMaxLength(2048);

        if (selectedImagePath != null) {
            urlField.setText(selectedImagePath);
        } else if (editing != null) {
            urlField.setText(editing.url());
        }

        addDrawableChild(urlField);

        int initialHeight = editing == null ? SizeManager.DEFAULT_HEIGHT_CM : editing.heightCm();
        heightSlider = new HeightSliderWidget(formLeft, layoutTop + 94, 180, 20, initialHeight);
        addDrawableChild(heightSlider);

        addDrawableChild(ButtonWidget.builder(modelText(), button -> {
            slim = !slim;
            button.setMessage(modelText());
        }).dimensions(formLeft, layoutTop + 120, 180, 20).build());

        keyButton = ButtonWidget.builder(keyText(), button -> {
            keyCodeBeforeCapture = keyCode;
            capturingKey = true;
            button.setMessage(Text.translatable("screen.multi-skin.skin_editor.press_key"));
        }).dimensions(formLeft, layoutTop + 160, 180, 20).build();
        addDrawableChild(keyButton);

        addDrawableChild(ButtonWidget.builder(Text.translatable("screen.multi-skin.skin_editor.save"), button -> save())
                .dimensions(formLeft, layoutTop + 186, 85, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.translatable("screen.multi-skin.skin_editor.cancel"), button -> close())
                .dimensions(formLeft + 95, layoutTop + 186, 85, 20).build());

        setInitialFocus(nameField);
    }

    private void openImageFilePicker() {
        String source = urlField.getText().trim();
        Path startingPath = MinecraftClient.getInstance().runDirectory.toPath();
        if (!source.isEmpty() && !SkinImageSource.isRemote(source)) {
            try {
                startingPath = Path.of(source);
            } catch (RuntimeException e) {
                errorMessage = Text.translatable(
                        "screen.multi-skin.skin_editor.browse_invalid_path").getString();
            }
        }
        MinecraftClient.getInstance().setScreen(
                new SkinFilePickerScreen(this, startingPath, path -> {
                    selectedImagePath = path.toAbsolutePath().normalize().toString();
                })
        );
    }

    private void save() {
        String name = nameField.getText().trim();
        String url = urlField.getText().trim();
        int heightCm = heightSlider.getHeightCm();

        if (name.isEmpty() || url.isEmpty()) {
            errorMessage = Text.translatable("screen.multi-skin.skin_editor.required_fields").getString();
            SkinSounds.error();
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return;
        }

        String model = slim ? "slim" : "steve";
        errorMessage = null;

        saveSkin(model, heightCm, pendingSwap);
    }

    private void saveSkin(String model, int heightCm, SkinLibrary.SkinEntry conflictToSwap) {
        String name = nameField.getText().trim();
        String url = urlField.getText().trim();
        MinecraftClient client = MinecraftClient.getInstance();

        Thread.startVirtualThread(() -> {
            try {
                SkinImageSource.load(url);
                String storedSource = SkinImageSource.normalizeForStorage(url);
                client.execute(() -> {
                    try {
                        if (editing == null) {
                            SkinLibrary.saveSkin(
                                    targetFolder != null ? targetFolder : SkinLibrary.getRoot(),
                                    name,
                                    storedSource,
                                    model,
                                    heightCm,
                                    keyCode,
                                    removedPartsMask
                            );
                        } else {
                            SkinClient.removeThumbnail(editing.file());
                            SkinLibrary.SkinEntry updated = SkinLibrary.updateSkin(
                                    editing, name, storedSource, model, heightCm, keyCode, removedPartsMask);
                            if (parent instanceof SkinScreen skinScreen) {
                                skinScreen.onSkinEdited(editing, updated);
                            }
                        }

                        if (conflictToSwap != null) {
                            SkinLibrary.updateSkinKeyCode(
                                    conflictToSwap,
                                    keyCodeBeforeCapture
                            );
                        }

                        keyConflict = null;
                        pendingSwap = null;
                        close();
                    } catch (Exception e) {
                        errorMessage = SkinManager.errorText(
                                e, "screen.multi-skin.skin_editor.save_error").getString();
                        SkinSounds.error();
                    }
                });
            } catch (Exception e) {
                client.execute(() -> errorMessage = SkinManager.errorText(
                        e, "screen.multi-skin.skin_editor.save_error").getString());
                SkinSounds.error();
            }
        });
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);

        if (keyConflict != null) {
            renderConflictDialog(context, mouseX, mouseY);
            return;
        }

        super.render(context, mouseX, mouseY, delta);

        context.drawText(this.textRenderer, this.title,
                this.width / 2 - this.textRenderer.getWidth(this.title) / 2, layoutTop, 0xFFFFFFFF, true);
        context.drawText(this.textRenderer, Text.translatable("screen.multi-skin.skin_editor.name"),
                this.width / 2 - 155, layoutTop + 18, 0xFFFFFFFF, true);
        context.drawText(this.textRenderer, Text.translatable("screen.multi-skin.skin_editor.url"),
                this.width / 2 - 155, layoutTop + 54, 0xFFFFFFFF, true);
        context.drawText(this.textRenderer, Text.translatable("screen.multi-skin.skin_editor.keybind"),
                this.width / 2 - 155, layoutTop + 146, 0xFFFFFFFF, true);

        drawBodyPartSelector(context, mouseX, mouseY);

        if (errorMessage != null) {
            context.drawText(this.textRenderer, errorMessage, this.width / 2 - 155, layoutTop + 212, 0xFFFF5555, false);
        }

    }

    private void drawBodyPartSelector(DrawContext context, int mouseX, int mouseY) {
        int iconX = this.width / 2 + 42;
        int iconY = layoutTop + 60;
        int iconWidth = 22 * BODY_ICON_SCALE;
        int iconHeight = 42 * BODY_ICON_SCALE;

        Text label = Text.translatable("screen.multi-skin.skin_editor.bodyparts");
        context.drawText(this.textRenderer, label,
                iconX + (iconWidth - this.textRenderer.getWidth(label)) / 2, layoutTop + 40, 0xFFFFFFFF, true);
        context.drawTexture(BODY_PART_ICON, iconX, iconY, iconWidth, iconHeight,
                0.0F, 0.0F, 22, 42, 22, 42);

        drawPartHighlight(context, BodyPart.LEFT_ARM, iconX, iconY, mouseX, mouseY);
        drawPartHighlight(context, BodyPart.RIGHT_ARM, iconX, iconY, mouseX, mouseY);
        drawPartHighlight(context, BodyPart.LEFT_LEG, iconX, iconY, mouseX, mouseY);
        drawPartHighlight(context, BodyPart.RIGHT_LEG, iconX, iconY, mouseX, mouseY);

        BodyPart hoveredPart = getHoveredBodyPart(mouseX, mouseY);
        if (hoveredPart != null) {
            Text partName = Text.translatable(hoveredPart.translationKey());
            context.drawText(this.textRenderer, partName,
                    iconX + (iconWidth - this.textRenderer.getWidth(partName)) / 2,
                    iconY + iconHeight + 4, 0xFFFFFFFF, true);
        }
    }

    private void drawPartHighlight(
            DrawContext context,
            BodyPart part,
            int iconX,
            int iconY,
            int mouseX,
            int mouseY
    ) {
        int[] bounds = getPartTextureBounds(part);
        int leftX = iconX + bounds[0] * BODY_ICON_SCALE;
        int topY = iconY + bounds[1] * BODY_ICON_SCALE;
        int rightX = leftX + bounds[2] * BODY_ICON_SCALE;
        int bottomY = topY + bounds[3] * BODY_ICON_SCALE;

        boolean selected = (removedPartsMask & part.mask()) != 0;
        boolean hovered = mouseX >= leftX && mouseX < rightX && mouseY >= topY && mouseY < bottomY;
        if (selected || hovered) {
            context.fill(leftX, topY, rightX, bottomY, selected ? 0x8066FF66 : 0x60FFFFFF);
            context.fill(leftX, topY, rightX, topY + 1, 0xFFFFFFFF);
            context.fill(leftX, bottomY - 1, rightX, bottomY, 0xFFFFFFFF);
            context.fill(leftX, topY, leftX + 1, bottomY, 0xFFFFFFFF);
            context.fill(rightX - 1, topY, rightX, bottomY, 0xFFFFFFFF);
        }
    }

    private BodyPart getHoveredBodyPart(double mouseX, double mouseY) {
        int iconX = this.width / 2 + 42;
        int iconY = layoutTop + 60;
        if (mouseX < iconX || mouseX >= iconX + 22 * BODY_ICON_SCALE
                || mouseY < iconY || mouseY >= iconY + 42 * BODY_ICON_SCALE) {
            return null;
        }

        int textureX = (int)((mouseX - iconX) / BODY_ICON_SCALE);
        int textureY = (int)((mouseY - iconY) / BODY_ICON_SCALE);
        for (BodyPart part : BodyPart.values()) {
            int[] bounds = getPartTextureBounds(part);
            if (textureX >= bounds[0] && textureX < bounds[0] + bounds[2]
                    && textureY >= bounds[1] && textureY < bounds[1] + bounds[3]) {
                return part;
            }
        }
        return null;
    }

    private int[] getPartTextureBounds(BodyPart part) {
        return switch (part) {
            case RIGHT_ARM -> new int[]{0, 12, 4, 14};
            case LEFT_ARM -> new int[]{18, 12, 4, 14};
            case RIGHT_LEG -> new int[]{6, 28, 4, 14};
            case LEFT_LEG -> new int[]{12, 28, 4, 14};
        };
    }

    private Text keyText() {
        SkinLibrary.SkinEntry conflict = SkinLibrary.findSkinWithKeyCode(keyCode, editing == null ? null : editing.file());
        if (pendingSwap != null && conflict != null
                && conflict.file().toAbsolutePath().normalize()
                .equals(pendingSwap.file().toAbsolutePath().normalize())) {
            conflict = null;
        }
        String keyName = keyCode < 0
                ? Text.translatable("screen.multi-skin.skin_editor.none").getString()
                : SkinKeybinds.getKeyName(keyCode);
        MutableText keyText = Text.translatable("screen.multi-skin.skin_editor.key", keyName);
        return conflict == null
                ? keyText
                : keyText.formatted(Formatting.RED);
    }

    private Text modelText() {
        return Text.translatable("screen.multi-skin.skin_editor.model",
                Text.translatable(slim
                        ? "screen.multi-skin.skin_editor.model.slim"
                        : "screen.multi-skin.skin_editor.model.steve"));
    }

    private void selectKeyCode(int keyCode) {
        if (this.keyCode != keyCode) {
            pendingSwap = null;
        }

        this.keyCode = keyCode;
        keyButton.setMessage(keyText());
        keyConflict = SkinLibrary.findSkinWithKeyCode(
                keyCode, editing == null ? null : editing.file());
        if (keyConflict != null) {
            SkinSounds.error();
        }
    }

    private void cancelKeyConflict() {
        keyCode = keyCodeBeforeCapture;
        pendingSwap = null;
        keyConflict = null;
        keyButton.setMessage(keyText());
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.keyConflict != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                cancelKeyConflict();
                return true;
            }
            return true;
        }

        if (capturingKey) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                capturingKey = false;
                keyButton.setMessage(keyText());
            } else {
                selectKeyCode(keyCode == GLFW.GLFW_KEY_BACKSPACE || keyCode == GLFW.GLFW_KEY_DELETE ? -1 : keyCode);
                capturingKey = false;
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (keyConflict != null) {
            if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                return true;
            }

            int left = (this.width - DIALOG_WIDTH) / 2;
            int top = (this.height - DIALOG_HEIGHT) / 2;
            if (mouseY >= top + 85 && mouseY < top + 110) {
                if (mouseX >= left + 10 && mouseX < left + 100) {
                    pendingSwap = keyConflict;
                    keyConflict = null;
                    keyButton.setMessage(keyText());
                } else if (mouseX >= left + 105 && mouseX < left + 195) {
                    pendingSwap = null;
                    keyConflict = null;
                } else if (mouseX >= left + 200 && mouseX < left + 290) {
                    cancelKeyConflict();
                }
            }
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            BodyPart part = getHoveredBodyPart(mouseX, mouseY);
            if (part != null) {
                removedPartsMask ^= part.mask();
                SkinSounds.click();
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void renderConflictDialog(DrawContext context, int mouseX, int mouseY) {
        int left = (this.width - DIALOG_WIDTH) / 2;
        int top = (this.height - DIALOG_HEIGHT) / 2;

        context.fill(0, 0, this.width, this.height, 0xFF000000);
        context.fill(left, top, left + DIALOG_WIDTH, top + DIALOG_HEIGHT, 0xFF202020);
        context.fill(left, top, left + DIALOG_WIDTH, top + 1, 0xFFFFFFFF);
        context.fill(left, top + DIALOG_HEIGHT - 1, left + DIALOG_WIDTH, top + DIALOG_HEIGHT, 0xFFFFFFFF);
        context.fill(left, top, left + 1, top + DIALOG_HEIGHT, 0xFFFFFFFF);
        context.fill(left + DIALOG_WIDTH - 1, top, left + DIALOG_WIDTH, top + DIALOG_HEIGHT, 0xFFFFFFFF);

        Text title = Text.translatable("screen.multi-skin.skin_editor.conflict.title");
        context.drawText(this.textRenderer, title,
                left + (DIALOG_WIDTH - this.textRenderer.getWidth(title)) / 2, top + 10, 0xFFFF5555, true);
        Text message = Text.translatable("screen.multi-skin.skin_editor.conflict.message", keyConflict.name());
        context.drawText(this.textRenderer, message,
                left + 10, top + 35, 0xFFFFFFFF, false);
        context.drawText(this.textRenderer, Text.translatable("screen.multi-skin.skin_editor.conflict.question"),
                left + 10, top + 52, 0xFFCCCCCC, false);

        drawDialogButton(context, Text.translatable("screen.multi-skin.skin_editor.conflict.swap"),
                left + 10, top + 85, mouseX, mouseY);
        drawDialogButton(context, Text.translatable("screen.multi-skin.skin_editor.conflict.keep"),
                left + 105, top + 85, mouseX, mouseY);
        drawDialogButton(context, Text.translatable("screen.multi-skin.skin_editor.cancel"),
                left + 200, top + 85, mouseX, mouseY);
    }

    private void drawDialogButton(DrawContext context, Text text, int x, int y, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX < x + 90 && mouseY >= y && mouseY < y + 25;
        context.fill(x, y, x + 90, y + 25, hovered ? 0xFF606060 : 0xFF404040);
        context.drawText(this.textRenderer, text,
                x + (90 - this.textRenderer.getWidth(text)) / 2, y + 8, 0xFFFFFFFF, false);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private static final class HeightSliderWidget extends SliderWidget {
        private HeightSliderWidget(int x, int y, int width, int height, int heightCm) {
            super(
                    x,
                    y,
                    width,
                    height,
                    Text.empty(),
                    (heightCm - SizeManager.MIN_HEIGHT_CM)
                            / (double) (SizeManager.MAX_HEIGHT_CM - SizeManager.MIN_HEIGHT_CM)
            );
            updateMessage();
        }

        private int getHeightCm() {
            return SizeManager.MIN_HEIGHT_CM + (int) Math.round(
                    value * (SizeManager.MAX_HEIGHT_CM - SizeManager.MIN_HEIGHT_CM)
            );
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.translatable("screen.multi-skin.skin_editor.height", getHeightCm()));
        }

        @Override
        protected void applyValue() {
        }
    }
}