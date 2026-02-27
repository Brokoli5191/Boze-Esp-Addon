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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders a PNG from .minecraft/esp-images/ as a billboard above each entity.
 *
 * Options are auto-registered by the Option constructor — do NOT call options.add() manually.
 *
 * Image selection:
 *   PlayerImage / MobImage sliders = 1-based index into the sorted file list.
 *   Index 0 (default) = first file found.
 */
public class ImageEspModule extends AddonModule {

    // Options — auto-registered, no manual options.add needed
    private final ToggleOption targetOnly  = new ToggleOption(this, "TargetOnly",
            "Only show when crosshair targets entity", false);
    private final ToggleOption playersOnly = new ToggleOption(this, "PlayersOnly",
            "Restrict to players", true);
    private final ToggleOption showOnMobs  = new ToggleOption(this, "ShowOnMobs",
            "Also show on mobs (PlayersOnly must be off)", false);
    private final SliderOption scale       = new SliderOption(this, "Scale",
            "Billboard scale", 1.0, 0.1, 5.0, 0.1);
    private final SliderOption playerImage = new SliderOption(this, "PlayerImage",
            "Image index for players (1 = first file)", 1.0, 1.0, 64.0, 1.0);
    private final SliderOption mobImage    = new SliderOption(this, "MobImage",
            "Image index for mobs (1 = first file)", 1.0, 1.0, 64.0, 1.0);

    // Runtime state
    private final List<String>           imageFiles    = new ArrayList<>();
    private final Map<String, Identifier> textureCache = new HashMap<>();
    private final Map<String, int[]>      sizeCache    = new HashMap<>();
    private Path imageDir;

    public ImageEspModule() {
        super("ImageESP",
                "Renders PNG images from .minecraft/esp-images/ as billboards above entities.");
        // No options.add — Option() constructor handles registration automatically
    }

    @Override
    public void onEnable() {
        imageDir = MinecraftClient.getInstance().runDirectory.toPath().resolve("esp-images");
        try {
            Files.createDirectories(imageDir);
        } catch (IOException e) {
            System.err.println("[ImageESP] Could not create esp-images/: " + e.getMessage());
        }
        scanImages();
    }

    @Override
    public void onDisable() {
        textureCache.clear();
        sizeCache.clear();
        imageFiles.clear();
    }

    /** Scans esp-images/ for PNG files and populates imageFiles list. */
    private void scanImages() {
        imageFiles.clear();
        if (imageDir == null || !Files.exists(imageDir)) return;
        try (var stream = Files.list(imageDir)) {
            stream.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".png"))
                  .sorted()
                  .forEach(p -> imageFiles.add(p.getFileName().toString()));
        } catch (IOException e) {
            System.err.println("[ImageESP] Error scanning esp-images/: " + e.getMessage());
        }
        if (!imageFiles.isEmpty()) {
            System.out.println("[ImageESP] Found " + imageFiles.size() + " images: " + imageFiles);
        } else {
            System.out.println("[ImageESP] No PNG files found in esp-images/");
        }
    }

    @EventHandler
    public void onHudRender(EventHudRender event) {
        if (imageFiles.isEmpty()) return;

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

            // Pick file by 1-based index, clamp to available files
            int rawIndex = isPlayer
                    ? playerImage.getValue().intValue() - 1
                    : mobImage.getValue().intValue() - 1;
            int index = Math.max(0, Math.min(rawIndex, imageFiles.size() - 1));
            String filename = imageFiles.get(index);

            Identifier texture = loadTexture(filename);
            if (texture == null) continue;

            Vec3d renderPos = entity.getLerpedPos(event.tickDelta)
                    .add(0, entity.getHeight() + 0.2, 0);

            double s = scale.getValue().doubleValue();
            if (!Billboard.start(renderPos, event.context, s)) continue;

            int[] size = sizeCache.getOrDefault(filename, new int[]{64, 64});
            int w = size[0];
            int h = size[1];

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

    /** Loads and caches a texture from esp-images/. Returns null on failure. */
    private Identifier loadTexture(String filename) {
        if (textureCache.containsKey(filename)) return textureCache.get(filename);

        Path file = imageDir.resolve(filename);
        if (!Files.exists(file)) {
            textureCache.put(filename, null);
            return null;
        }

        try {
            BufferedImage img = ImageIO.read(file.toFile());
            if (img == null) { textureCache.put(filename, null); return null; }

            sizeCache.put(filename, new int[]{img.getWidth(), img.getHeight()});

            NativeImage native_ = toNativeImage(img);
            NativeImageBackedTexture tex = new NativeImageBackedTexture(
                    () -> "esp-addon:" + filename, native_);

            String idPath = "esp/" + filename.toLowerCase().replaceAll("[^a-z0-9_./]", "_");
            Identifier id = Identifier.of("esp-addon", idPath);
            MinecraftClient.getInstance().getTextureManager().registerTexture(id, tex);
            textureCache.put(filename, id);
            return id;
        } catch (IOException e) {
            System.err.println("[ImageESP] Failed to load " + filename + ": " + e.getMessage());
            textureCache.put(filename, null);
            return null;
        }
    }

    /** Converts BufferedImage (ARGB) to NativeImage (ABGR). */
    private NativeImage toNativeImage(BufferedImage img) {
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
}
