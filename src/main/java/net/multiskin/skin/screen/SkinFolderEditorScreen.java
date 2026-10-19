package net.multiskin.skin.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.multiskin.MultiSkinMod;

import java.nio.file.Files;
import java.nio.file.Path;

public class SkinFolderEditorScreen extends Screen {
    private final Screen parent;
    private final Path parentFolder;
    private final Path folderToRename;

    private TextFieldWidget nameField;

    public SkinFolderEditorScreen(Screen parent, Path parentFolder) {
        this(parent, parentFolder, null);
    }

    public SkinFolderEditorScreen(Screen parent, Path parentFolder, Path folderToRename) {
        super(Text.translatable(folderToRename == null
                ? "screen.multi-skin.folder_editor.new_title"
                : "screen.multi-skin.folder_editor.rename_title"));

        this.parent = parent;
        this.parentFolder = parentFolder;
        this.folderToRename = folderToRename;
    }

    @Override
    protected void init() {
        super.init();

        int center = this.width / 2;

        nameField = new TextFieldWidget(this.textRenderer, center - 100, 80, 200, 20,
                Text.translatable("screen.multi-skin.folder_editor.name"));
        nameField.setMaxLength(64);

        if (folderToRename != null) {
            nameField.setText(folderToRename.getFileName().toString());
        }

        addDrawableChild(nameField);

        addDrawableChild(ButtonWidget.builder(Text.translatable(folderToRename == null
                        ? "screen.multi-skin.folder_editor.create"
                        : "screen.multi-skin.folder_editor.rename"), button -> save())
                .dimensions(center - 100, 120, 95, 20).build()
        );

        addDrawableChild(ButtonWidget.builder(Text.translatable("screen.multi-skin.folder_editor.cancel"), button -> close())
                .dimensions(center + 5, 120, 95, 20).build()
        );

        setInitialFocus(nameField);
    }

    private void save() {
        String name = nameField.getText().trim();

        if (name.isEmpty()) {
            return;
        }

        try {
            if (folderToRename == null) {
                SkinLibrary.createFolder(parentFolder, name);

            } else {
                String safeName = SkinLibrary.sanitizeName(name);
                Path newPath = folderToRename.getParent().resolve(safeName);

                if (!newPath.equals(folderToRename) && Files.exists(newPath)) {
                    return;
                }

                Files.move(folderToRename, newPath);
                SkinLibrary.renameOrderPath(folderToRename, newPath);
            }
            close();

        } catch (Exception e) {
            MultiSkinMod.LOGGER.error("Impossible de sauvegarder le dossier.", e);
        }
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);

        context.drawText(this.textRenderer, this.title, this.width / 2 - this.textRenderer.getWidth(this.title) / 2, 40, 0xFFFFFFFF, true);
        context.drawText(this.textRenderer, Text.translatable("screen.multi-skin.folder_editor.folder_name"),
                this.width / 2 - 100, 62, 0xFFFFFFFF, true);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}