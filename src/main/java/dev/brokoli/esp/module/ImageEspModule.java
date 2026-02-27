package dev.brokoli.esp.module;

import dev.boze.api.addon.AddonModule;
import dev.boze.api.event.Render3DEvent;
import dev.boze.api.option.BooleanOption;
import dev.boze.api.option.StringOption;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.math.Vec3d;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * ImageEspModule - renders PNG images (with alpha transparency) as billboard
 * overlays above entities. Images are loaded at runtime from:
 *   <gameDir>/esp-images/
 *
 * Drop any PNG/GIF file into that folder and reference it by filename in settings.
 * GIF support: first frame is extracted automatically via ImageIO.
 */
public class ImageEspModule extends AddonModule {

    private final BooleanOption targetOnly = new BooleanOption("TargetOnly",
        "Only show image overlay when the crosshair is aimed at the entity", false);

    private final BooleanOption playersOnly = new BooleanOption("PlayersOnly",
        "Restrict overlays to player entities only", true);

    private final BooleanOption showOnMobs = new BooleanOption("ShowOnMobs",
        "Also show overlays on non-player mobs (only active when PlayersOnly is off)", false);

    private final StringOption defaultImageName = new StringOption("DefaultImage",
        "Filename inside esp-images/ used as fallback (e.g. default.png)",
        "default.png");

    private final StringOption customPlayerImageName = new StringOption("PlayerImage",
        "Custom filename for player entities - leave blank to use DefaultImage", "");

    private final StringOption customMobImageName = new StringOption("MobImage",
        "Custom filename for mob entities - leave blank to use DefaultImage", "");

    // Runtime texture cache: filename -> registered Identifier
    private static final Map<String, Identifier> textureCache = new HashMap<>();

    // Base directory for user-supplied images
    private static Path imageDir = null;

    public ImageEspModule() {
        super("ImageESP",
            "Renders PNG/GIF images from esp-images/ as ESP overlays above entities.");
        addOptions(targetOnly, playersOnly, showOnMobs,
                   defaultImageName, customPlayerImageName, customMobImageName);
    }

    // -------------------------------------------------------------------------
    // Event handler
    // -------------------------------------------------------------------------

    @EventHandler
    public void onRender3D(Render3DEvent event) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;

        Entity crosshairTarget = mc.targetedEntity;

        for (Entity entity : mc.world.getEntities()) {
            if (entity == mc.player) continue;
            if (!(entity instanceof LivingEntity)) continue;

            boolean isPlayer = entity instanceof PlayerEntity;

            if (playersOnly.get() && !isPlayer) continue;
            if (!showOnMobs.get() && !isPlayer) continue;
            if (targetOnly.get() && entity != crosshairTarget) continue;

            String filename = resolveFilename(isPlayer);
            Identifier texture = resolveTexture(filename);
            if (texture == null) continue;

            renderBillboard(event.matrixStack, event.vertexConsumers, entity, texture, mc);
        }
    }

    // -------------------------------------------------------------------------
    // Filename & texture resolution
    // -------------------------------------------------------------------------

    private String resolveFilename(boolean isPlayer) {
        String custom = isPlayer ? customPlayerImageName.get() : customMobImageName.get();
        return (custom != null && !custom.isBlank()) ? custom.trim() : defaultImageName.get().trim();
    }

    /**
     * Returns a cached or freshly loaded Identifier for the given filename.
     * Returns null if the file doesn't exist or cannot be decoded.
     */
    private Identifier resolveTexture(String filename) {
        if (textureCache.containsKey(filename)) return textureCache.get(filename);

        Path dir = getImageDir();
        Path filePath = dir.resolve(filename);

        if (!Files.exists(filePath)) {
            textureCache.put(filename, null);
            return null;
        }

        try {
            BufferedImage img = ImageIO.read(filePath.toFile());
            if (img == null) {
                textureCache.put(filename, null);
                return null;
            }

            NativeImage native_ = bufferedToNative(img);
            NativeImageBackedTexture tex = new NativeImageBackedTexture(native_);

            String idPath = "entity/" + filename.toLowerCase()
                .replaceAll("[^a-z0-9_./]", "_");
            Identifier id = Identifier.of("esp-addon", idPath);

            MinecraftClient.getInstance().getTextureManager().registerTexture(id, tex);
            textureCache.put(filename, id);
            return id;

        } catch (IOException e) {
            System.err.println("[ImageESP] Failed to load image: " + filePath);
            e.printStackTrace();
            textureCache.put(filename, null);
            return null;
        }
    }

    /**
     * Converts a BufferedImage (ARGB) to Minecraft NativeImage (ABGR).
     * Alpha channel is fully preserved for transparent PNGs.
     */
    private NativeImage bufferedToNative(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        NativeImage out = new NativeImage(w, h, false);
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                int argb = img.getRGB(x, y);
                int a = (argb >> 24) & 0xFF;
                int r = (argb >> 16) & 0xFF;
                int g = (argb >>  8) & 0xFF;
                int b =  argb        & 0xFF;
                // NativeImage stores ABGR
                out.setColorArgb(x, y, (a << 24) | (b << 16) | (g << 8) | r);
            }
        }
        return out;
    }

    // -------------------------------------------------------------------------
    // Billboard rendering
    // -------------------------------------------------------------------------

    private void renderBillboard(MatrixStack matrices, VertexConsumerProvider vcp,
                                 Entity entity, Identifier texture, MinecraftClient mc) {
        Vec3d cam  = mc.getEntityRenderDispatcher().camera.getPos();
        Vec3d epos = entity.getLerpedPos(
            mc.getRenderTickCounter().getTickDelta(true));

        float dx = (float)(epos.x - cam.x);
        float dy = (float)(epos.y - cam.y);
        float dz = (float)(epos.z - cam.z);

        float entityHeight = entity.getHeight();
        float scale = Math.max(0.5f, entityHeight);

        matrices.push();
        matrices.translate(dx, dy + entityHeight + 0.1f, dz);
        matrices.multiply(mc.getEntityRenderDispatcher().camera.getRotation());
        matrices.scale(scale, scale, scale);

        var consumer = vcp.getBuffer(RenderLayer.getEntityTranslucent(texture));
        var entry    = matrices.peek();

        int light   = 0xF000F0;
        int overlay = 0;
        int alpha   = 220;

        consumer.vertex(entry, -0.5f, 0.0f, 0f)
            .color(255, 255, 255, alpha)
            .texture(0f, 1f).overlay(overlay).light(light).normal(entry, 0, 0, 1);
        consumer.vertex(entry,  0.5f, 0.0f, 0f)
            .color(255, 255, 255, alpha)
            .texture(1f, 1f).overlay(overlay).light(light).normal(entry, 0, 0, 1);
        consumer.vertex(entry,  0.5f, 1.0f, 0f)
            .color(255, 255, 255, alpha)
            .texture(1f, 0f).overlay(overlay).light(light).normal(entry, 0, 0, 1);
        consumer.vertex(entry, -0.5f, 1.0f, 0f)
            .color(255, 255, 255, alpha)
            .texture(0f, 0f).overlay(overlay).light(light).normal(entry, 0, 0, 1);

        matrices.pop();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Returns (and lazily creates) the image directory:
     *   <gameDir>/esp-images/
     */
    private static Path getImageDir() {
        if (imageDir == null) {
            imageDir = MinecraftClient.getInstance().runDirectory.toPath()
                .resolve("esp-images");
            try {
                Files.createDirectories(imageDir);
            } catch (IOException e) {
                System.err.println("[ImageESP] Could not create esp-images/ directory: " + e.getMessage());
            }
        }
        return imageDir;
    }

    /** Clears the texture cache - call this to force reload of all images. */
    public static void clearTextureCache() {
        textureCache.clear();
    }
}
