package dev.brokoli.esp.module;

import dev.boze.api.addon.AddonModule;
import dev.boze.api.event.EventHudRender;
import dev.boze.api.option.SliderOption;
import dev.boze.api.option.ToggleOption;
import dev.boze.api.render.Billboard;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
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

    private final SliderOption scale = new SliderOption(this, "Scale",
        "Billboard scale", 1.0, 0.1, 5.0, 0.1);

    private static final Map<String, Identifier> textureCache = new HashMap<>();
    private static final Map<String, int[]> textureSizeCache = new HashMap<>();
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
        options.add(scale);
    }

    @EventHandler
    public void onHudRender(EventHudRender event) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;

        Entity crosshairTarget = mc.targetedEntity;

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

            Vec3d renderPos = entity.getLerpedPos(event.tickDelta)
                .add(0, entity.getHeight() + 0.2, 0);

            double s = scale.getValue().doubleValue();
            if (!Billboard.start(renderPos, event.context, s)) continue;

            int[] size = textureSizeCache.getOrDefault(filename, new int[]{64, 64});
            int w = size[0];
            int h = size[1];

            // 1.21.6+: drawTexture(RenderPipeline, Identifier, x, y, u, v, width, height, texW, texH)
            event.context.drawTexture(
                RenderPipelines.GUI_TEXTURED,
                texture,
                -w / 2, -h,
                0f, 0f,
                w, h,
                w, h
            );

            Billboard.stop(event.context);
        }
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

            int w = img.getWidth();
            int h = img.getHeight();
            textureSizeCache.put(filename, new int[]{w, h});

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

    public static void clearTextureCache() {
        textureCache.clear();
        textureSizeCache.clear();
    }
}
