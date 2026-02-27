package dev.brokoli.esp.module;

import dev.boze.api.addon.AddonModule;
import dev.boze.api.event.EventWorldRender;
import dev.boze.api.option.SliderOption;
import dev.boze.api.option.ToggleOption;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class ImageEspModule extends AddonModule {

    private final ToggleOption targetOnly = new ToggleOption(this, "TargetOnly",
        "Only show when crosshair is aimed at entity", false);

    private final ToggleOption playersOnly = new ToggleOption(this, "PlayersOnly",
        "Restrict to player entities only", true);

    private final ToggleOption showOnMobs = new ToggleOption(this, "ShowOnMobs",
        "Also show on mobs (requires PlayersOnly off)", false);

    private final SliderOption alpha = new SliderOption(this, "Opacity",
        "Image opacity 0-255", 220, 0, 255, 1);

    private static final Map<String, Identifier> textureCache = new HashMap<>();
    private static Path imageDir = null;

    private static final String DEFAULT_IMAGE = "default.png";
    private static final String PLAYER_IMAGE  = "player.png";
    private static final String MOB_IMAGE     = "mob.png";

    public ImageEspModule() {
        super("ImageESP",
            "Renders PNG images from .minecraft/esp-images/ as ESP overlays above entities.");
        options.add(targetOnly);
        options.add(playersOnly);
        options.add(showOnMobs);
        options.add(alpha);
    }

    @EventHandler
    public void onWorldRender(EventWorldRender event) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;

        Entity crosshairTarget = mc.targetedEntity;
        VertexConsumerProvider.Immediate vcp = mc.getBufferBuilders().getEntityVertexConsumers();

        for (Entity entity : mc.world.getEntities()) {
            if (entity == mc.player) continue;
            if (!(entity instanceof LivingEntity)) continue;

            boolean isPlayer = entity instanceof PlayerEntity;

            if (playersOnly.getValue() && !isPlayer) continue;
            if (!showOnMobs.getValue() && !isPlayer) continue;
            if (targetOnly.getValue() && entity != crosshairTarget) continue;

            String filename = isPlayer ? PLAYER_IMAGE : MOB_IMAGE;
            Identifier texture = resolveTexture(filename);
            if (texture == null) texture = resolveTexture(DEFAULT_IMAGE);
            if (texture == null) continue;

            renderBillboard(event, vcp, entity, texture);
        }

        vcp.draw();
    }

    private Identifier resolveTexture(String filename) {
        if (textureCache.containsKey(filename)) return textureCache.get(filename);

        Path filePath = getImageDir().resolve(filename);
        if (!Files.exists(filePath)) {
            textureCache.put(filename, null);
            return null;
        }

        try {
            BufferedImage img = ImageIO.read(filePath.toFile());
            if (img == null) { textureCache.put(filename, null); return null; }

            NativeImage native_ = bufferedToNative(img);
            NativeImageBackedTexture tex = new NativeImageBackedTexture(
                () -> "esp-addon:" + filename, native_);

            String idPath = "entity/" + filename.toLowerCase()
                .replaceAll("[^a-z0-9_./]", "_");
            Identifier id = Identifier.of("esp-addon", idPath);
            MinecraftClient.getInstance().getTextureManager().registerTexture(id, tex);
            textureCache.put(filename, id);
            return id;
        } catch (IOException e) {
            System.err.println("[ImageESP] Failed to load: " + filePath);
            textureCache.put(filename, null);
            return null;
        }
    }

    private NativeImage bufferedToNative(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        NativeImage out = new NativeImage(w, h, false);
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                int argb = img.getRGB(x, y);
                int a = (argb >> 24) & 0xFF;
                int r = (argb >> 16) & 0xFF;
                int g = (argb >>  8) & 0xFF;
                int b =  argb        & 0xFF;
                out.setColorArgb(x, y, (a << 24) | (b << 16) | (g << 8) | r);
            }
        }
        return out;
    }

    private void renderBillboard(EventWorldRender event, VertexConsumerProvider.Immediate vcp,
                                 Entity entity, Identifier texture) {
        Vec3d cam = new Vec3d(
            event.camera.getBlockPos().getX() + 0.5,
            event.camera.getBlockPos().getY() + 0.5,
            event.camera.getBlockPos().getZ() + 0.5
        );
        if (event.camera.getFocusedEntity() != null) {
            cam = event.camera.getFocusedEntity().getEyePos();
        }

        Vec3d epos = entity.getLerpedPos(event.tickDelta);

        float dx = (float)(epos.x - cam.x);
        float dy = (float)(epos.y - cam.y);
        float dz = (float)(epos.z - cam.z);

        float entityHeight = entity.getHeight();
        float scale = Math.max(0.5f, entityHeight);
        int a = (int) alpha.getValue().doubleValue();

        event.matrices.push();
        event.matrices.translate(dx, dy + entityHeight + 0.1f, dz);
        event.matrices.multiply(event.camera.getRotation());
        event.matrices.scale(scale, scale, scale);

        // Yarn 1.21.11: getEntityTranslucent(Identifier texture, boolean affectsOutline)
        var consumer = vcp.getBuffer(RenderLayer.getEntityTranslucent(texture, false));
        var entry    = event.matrices.peek();
        int light = 0xF000F0, overlay = 0;

        consumer.vertex(entry, -0.5f, 0.0f, 0f)
            .color(255, 255, 255, a).texture(0f, 1f)
            .overlay(overlay).light(light).normal(entry, 0, 0, 1);
        consumer.vertex(entry,  0.5f, 0.0f, 0f)
            .color(255, 255, 255, a).texture(1f, 1f)
            .overlay(overlay).light(light).normal(entry, 0, 0, 1);
        consumer.vertex(entry,  0.5f, 1.0f, 0f)
            .color(255, 255, 255, a).texture(1f, 0f)
            .overlay(overlay).light(light).normal(entry, 0, 0, 1);
        consumer.vertex(entry, -0.5f, 1.0f, 0f)
            .color(255, 255, 255, a).texture(0f, 0f)
            .overlay(overlay).light(light).normal(entry, 0, 0, 1);

        event.matrices.pop();
    }

    private static Path getImageDir() {
        if (imageDir == null) {
            imageDir = MinecraftClient.getInstance().runDirectory.toPath()
                .resolve("esp-images");
            try { Files.createDirectories(imageDir); }
            catch (IOException e) {
                System.err.println("[ImageESP] Could not create esp-images/: " + e.getMessage());
            }
        }
        return imageDir;
    }

    public static void clearTextureCache() { textureCache.clear(); }
}
