package net.multiskin.skin.fastchoose;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.entity.EntityPose;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.multiskin.bodyparts.BodyPartClient;
import net.multiskin.bodyparts.BodyPart;
import net.multiskin.skin.SkinClient;
import net.multiskin.skin.screen.SkinLibrary;
import com.mojang.authlib.GameProfile;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import net.minecraft.client.util.math.MatrixStack;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.SkinTextures;
import net.multiskin.skin.size.SizeManager;

public final class SkinPreviewRenderer {
    private static final float PREVIEW_HEAD_SCALE = 0.68F;
    private static final float PREVIEW_HAT_SCALE = 1.0F;
    private static final Identifier FAST_CHOOSE_SLOT_TEXTURE = Identifier.of("multi-skin", "textures/gui/fastchoose_slot.png");
    private static final Identifier FAST_CHOOSE_SLOT_SELECTED_TEXTURE = Identifier.of("multi-skin", "textures/gui/fastchoose_slot_select.png");

    private SkinPreviewRenderer() {
    }

    public static void drawSlotBackground(DrawContext context, int centerX, int centerY, int slotSize, boolean selected) {
        int left = centerX - slotSize / 2;
        int top = centerY - slotSize / 2;
        Identifier texture = selected ? FAST_CHOOSE_SLOT_SELECTED_TEXTURE : FAST_CHOOSE_SLOT_TEXTURE;
        context.drawTexture(texture, left, top, slotSize, slotSize, 0.0F, 0.0F, 64, 64, 64, 64);
    }

    public static boolean draw(DrawContext context, SkinLibrary.SkinEntry skin, int centerX, int top, int slotSize) {
        return draw(context, skin, centerX, top, slotSize, 205.0F);
    }

    public static boolean draw(DrawContext context, SkinLibrary.SkinEntry skin, int centerX, int top, int slotSize, float yaw) {
        Identifier texture = SkinClient.getSkinPreviewTexture(skin.file(), skin.url());
        if (texture == null) {
            return false;
        }

        int entitySize = Math.max(1, Math.round(slotSize * 0.33F));
        int entityY = top + slotSize - Math.max(1, Math.round(slotSize * 0.30F));
        return draw(context, skin.file().toAbsolutePath().normalize().toString(), texture, skin.model(), skin.removedPartsMask(), centerX, top, slotSize, slotSize, entitySize, entityY, yaw);
    }

    public static boolean draw(DrawContext context, Identifier texture, String model, int centerX, int top, int width, int height, int heightCm, float yaw) {
        int entitySize = (int) (Math.min(width, height) * 0.42F * (heightCm / (float) SizeManager.DEFAULT_HEIGHT_CM));
        entitySize = Math.clamp(entitySize, 1, Math.max(1, (height - 8) / 2));
        return draw(context, "current-profile", texture, model, 0, centerX, top, width, height, entitySize, top + height - 4, yaw);
    }

    public static boolean draw(DrawContext context, ClientPlayerEntity player, int centerX, int top, int width, int height, int heightCm, float yaw) {
        SkinTextures textures = player.getSkinTextures();
        int entitySize = (int) (Math.min(width, height) * 0.42F * (heightCm / (float) SizeManager.DEFAULT_HEIGHT_CM));
        entitySize = Math.clamp(entitySize, 1, Math.max(1, (height - 8) / 2));
        int entityY = top + height - 4;
        return draw(context, "current-player:" + player.getUuid(), textures.texture(), textures.model() == SkinTextures.Model.SLIM ? "slim" : "steve", BodyPartClient.getMask(player.getUuid()), centerX, top, width, height, entitySize, entityY, yaw);
    }

    public static boolean draw(DrawContext context, SkinLibrary.SkinEntry skin, int centerX, int top, int width, int height, float yaw) {
        Identifier texture = SkinClient.getSkinPreviewTexture(skin.file(), skin.url());
        if (texture == null) {
            return false;
        }
        int entitySize = (int) (Math.min(width, height) * 0.42F * (skin.heightCm() / (float) SizeManager.DEFAULT_HEIGHT_CM));
        entitySize = Math.clamp(entitySize, 1, Math.max(1, (height - 8) / 2));
        int entityY = top + height - 4;
        return draw(context, skin.file().toAbsolutePath().normalize().toString(), texture, skin.model(), skin.removedPartsMask(), centerX, top, width, height, entitySize, entityY, yaw);
    }

    private static boolean draw(DrawContext context, String previewKey, Identifier texture, String model, int removedPartsMask, int centerX, int top, int width, int height, int entitySize, int entityY, float yaw) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            drawStandalonePreview(context, texture, model, removedPartsMask, centerX, top, width, height, entitySize, entityY, yaw);
            return true;
        }
        UUID previewUuid = UUID.nameUUIDFromBytes(previewKey.getBytes(StandardCharsets.UTF_8));
        OtherClientPlayerEntity previewPlayer = new OtherClientPlayerEntity(client.world, new GameProfile(previewUuid, "MultiSkinPreview"));
        previewPlayer.setPose(EntityPose.STANDING);
        previewPlayer.setSneaking(false);
        previewPlayer.setVelocity(Vec3d.ZERO);
        previewPlayer.setOnGround(true);
        previewPlayer.setYaw(0.0F);
        previewPlayer.setPitch(0.0F);
        previewPlayer.headYaw = 0.0F;
        previewPlayer.bodyYaw = 0.0F;
        Quaternionf rotation = new Quaternionf().rotateZ((float) Math.PI).rotateY((float) Math.toRadians(yaw));
        Quaternionf cameraRotation = new Quaternionf();

        context.enableScissor(centerX - width / 2 + 1, top + 1, centerX + width / 2 - 1, top + height);
        try {
            SkinClient.withSkinPreview(previewUuid, texture, model, () -> BodyPartClient.withPreviewMask(previewUuid, removedPartsMask, () ->
                    InventoryScreen.drawEntity(context, centerX, entityY, entitySize, new Vector3f(0.0F, 0.0F, 0.0F), rotation, cameraRotation, previewPlayer)));
        } finally {
            context.disableScissor();
        }
        return true;
    }

    private static void drawStandalonePreview(DrawContext context, Identifier texture, String model, int removedPartsMask, int centerX, int top, int width, int height, int entitySize, int entityY, float yaw) {
        MinecraftClient client = MinecraftClient.getInstance();
        boolean slim = model.equalsIgnoreCase("slim");
        PlayerEntityModel<?> playerModel = new PlayerEntityModel<>(client.getEntityModelLoader().getModelPart(slim ? EntityModelLayers.PLAYER_SLIM : EntityModelLayers.PLAYER), slim);
        playerModel.setVisible(true);
        playerModel.rightArm.visible = (removedPartsMask & BodyPart.RIGHT_ARM.mask()) == 0;
        playerModel.rightSleeve.visible = playerModel.rightArm.visible;
        playerModel.leftArm.visible = (removedPartsMask & BodyPart.LEFT_ARM.mask()) == 0;
        playerModel.leftSleeve.visible = playerModel.leftArm.visible;
        playerModel.rightLeg.visible = (removedPartsMask & BodyPart.RIGHT_LEG.mask()) == 0;
        playerModel.rightPants.visible = playerModel.rightLeg.visible;
        playerModel.leftLeg.visible = (removedPartsMask & BodyPart.LEFT_LEG.mask()) == 0;
        playerModel.leftPants.visible = playerModel.leftLeg.visible;
        playerModel.hat.copyTransform(playerModel.head);
        playerModel.jacket.copyTransform(playerModel.body);
        playerModel.rightSleeve.copyTransform(playerModel.rightArm);
        playerModel.leftSleeve.copyTransform(playerModel.leftArm);
        playerModel.rightPants.copyTransform(playerModel.rightLeg);
        playerModel.leftPants.copyTransform(playerModel.leftLeg);
        setHeadScale(playerModel.head, PREVIEW_HEAD_SCALE);
        setHeadScale(playerModel.hat, PREVIEW_HAT_SCALE);

        MatrixStack matrices = context.getMatrices();
        context.enableScissor(centerX - width / 2 + 1, top + 1, centerX + width / 2 - 1, top + height);
        matrices.push();
        try {
            RenderSystem.enableDepthTest();
            DiffuseLighting.method_34742();
            matrices.translate(centerX, entityY, 50.0F);
            matrices.multiply(new Quaternionf().rotateZ((float) Math.PI).rotateY((float) Math.toRadians(yaw + 180.0F)));
            float scale = entitySize * 2.0F;
            matrices.scale(scale, scale, -scale);
            matrices.scale(-1.0F, -1.0F, 1.0F);
            matrices.translate(0.0F, -1.501F, 0.0F);
            VertexConsumerProvider consumers = context.getVertexConsumers();
            RenderSystem.runAsFancy(() -> playerModel.render(matrices, consumers.getBuffer(RenderLayer.getEntityTranslucent(texture)), 15728880, OverlayTexture.DEFAULT_UV));
            context.draw();
        } finally {
            matrices.pop();
            context.disableScissor();
            DiffuseLighting.enableGuiDepthLighting();
        }
    }

    private static void setHeadScale(net.minecraft.client.model.ModelPart part, float scale) {
        part.xScale = scale;
        part.yScale = scale;
        part.zScale = scale;
    }
}