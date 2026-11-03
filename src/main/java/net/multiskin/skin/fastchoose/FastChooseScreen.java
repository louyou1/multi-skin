package net.multiskin.skin.fastchoose;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.multiskin.skin.SkinClient;
import net.multiskin.skin.screen.SkinKeybinds;
import net.multiskin.skin.screen.SkinLibrary;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class FastChooseScreen extends Screen {
    private static final int SKINS_PER_PAGE = 8;
    private static final int SLOT_SIZE = 58;
    private final List<SkinLibrary.SkinEntry> skins;
    private int page;

    public FastChooseScreen() {
        super(Text.translatable("screen.multi-skin.fast_choose.title"));
        skins = SkinLibrary.getFastChooseSkins();
    }

    @Override
    protected void init() {
    }

    @Override
    public void tick() {
        super.tick();
        MinecraftClient minecraftClient = MinecraftClient.getInstance();
        FastChooseInput.syncMovementKeys(minecraftClient);
        if (minecraftClient.player == null || minecraftClient.getNetworkHandler() == null || !SkinKeybinds.isFastChooseDown(minecraftClient)) {
            close();
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int centerX = width / 2;
        int centerY = height / 2;
        int radius = getWheelRadius();

        drawCenter(context, centerX, centerY);
        if (skins.isEmpty()) {
            context.drawCenteredTextWithShadow(
                    textRenderer,
                    Text.translatable("screen.multi-skin.fast_choose.empty"),
                    centerX,
                    centerY + 27,
                    0xFFDDDDDD
            );
        }

        for (int slot = 0; slot < SKINS_PER_PAGE; slot++) {
            double angle = -Math.PI / 2 + slot * (Math.PI * 2 / SKINS_PER_PAGE);
            int slotCenterX = centerX + (int) (Math.cos(angle) * radius);
            int slotCenterY = centerY + (int) (Math.sin(angle) * radius);
            SkinLibrary.SkinEntry skin = SkinLibrary.getFastChooseSkinAtPosition(page * SKINS_PER_PAGE + slot);
            if (skin == null) {
                drawEmptySlot(context, slotCenterX, slotCenterY, mouseX, mouseY);
            } else {
                drawSkinSlot(context, skin, slotCenterX, slotCenterY, mouseX, mouseY);
            }
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private void drawCenter(DrawContext context, int centerX, int centerY) {
        int color = getPageCount() > 1 ? 0xFFFFFFFF : 0xFF777777;
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("‹"), centerX - 24, centerY - 4, color);
        context.drawCenteredTextWithShadow(
                textRenderer,
                Text.literal(Integer.toString(page + 1)),
                centerX,
                centerY - 4,
                0xFFFFFFFF
        );
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("›"), centerX + 24, centerY - 4, color);
    }

    private void drawSkinSlot(DrawContext context, SkinLibrary.SkinEntry skin, int centerX, int centerY, int mouseX, int mouseY) {
        int left = centerX - SLOT_SIZE / 2;
        int top = centerY - SLOT_SIZE / 2;
        boolean hovered = mouseX >= left && mouseX < left + SLOT_SIZE && mouseY >= top && mouseY < top + SLOT_SIZE;

        SkinPreviewRenderer.drawSlotBackground(context, centerX, centerY, SLOT_SIZE, hovered);
        SkinPreviewRenderer.draw(context, skin, centerX, top, SLOT_SIZE);

        String name = textRenderer.trimToWidth(skin.name(), SLOT_SIZE - 4);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(name), centerX, top + 48, 0xFFFFFFFF);
    }

    private void drawEmptySlot(DrawContext context, int centerX, int centerY, int mouseX, int mouseY) {
        int left = centerX - SLOT_SIZE / 2;
        int top = centerY - SLOT_SIZE / 2;
        boolean hovered = mouseX >= left && mouseX < left + SLOT_SIZE && mouseY >= top && mouseY < top + SLOT_SIZE;
        SkinPreviewRenderer.drawSlotBackground(context, centerX, centerY, SLOT_SIZE, hovered);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            int centerX = width / 2;
            int centerY = height / 2;
            if (mouseY >= centerY - 18 && mouseY < centerY + 18) {
                if (mouseX >= centerX - 36 && mouseX < centerX - 12) {
                    changePage(-1);
                    return true;
                }
                if (mouseX >= centerX + 12 && mouseX < centerX + 36) {
                    changePage(1);
                    return true;
                }
            }

            int radius = getWheelRadius();
            for (int slot = 0; slot < SKINS_PER_PAGE; slot++) {
                double angle = -Math.PI / 2 + slot * (Math.PI * 2 / SKINS_PER_PAGE);
                int slotCenterX = centerX + (int) (Math.cos(angle) * radius);
                int slotCenterY = centerY + (int) (Math.sin(angle) * radius);
                int left = slotCenterX - SLOT_SIZE / 2;
                int top = slotCenterY - SLOT_SIZE / 2;
                if (mouseX >= left && mouseX < left + SLOT_SIZE && mouseY >= top && mouseY < top + SLOT_SIZE) {
                    SkinLibrary.SkinEntry skin = SkinLibrary.getFastChooseSkinAtPosition(page * SKINS_PER_PAGE + slot);
                    if (skin != null) {
                        SkinClient.applySkinSource(skin.url(), skin.model(), skin.heightCm(), skin.removedPartsMask());
                    }
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (verticalAmount != 0 && getPageCount() > 1) {
            changePage(verticalAmount > 0 ? -1 : 1);
        }
        return true;
    }

    private void changePage(int amount) {
        int pageCount = getPageCount();
        if (pageCount <= 1) {
            return;
        }
        page = Math.floorMod(page + amount, pageCount);
    }

    private int getPageCount() {
        return SkinLibrary.getFastChoosePageCount();
    }

    private int getWheelRadius() {
        return Math.clamp(Math.min(width / 3, (height - 90) / 2), 50, 110);
    }
}