# Boze Image ESP Addon

A [Boze](https://boze.dev) addon that renders **PNG images** (with full alpha transparency) as billboard ESP overlays above entities in Minecraft 1.21.4.

Images are **not bundled in the JAR** — they are loaded at runtime from a folder you manage yourself.

---

## Features

- Runtime image loading from `.minecraft/esp-images/` — no recompile needed
- Full PNG transparency (alpha channel preserved)
- GIF support (first frame extracted automatically)
- Per-entity-type images: separate files for players vs. mobs
- **Target-only mode** — overlay only appears when crosshair is aimed at an entity
- Images scale to entity height automatically
- Texture cache — files are only read from disk once per session
- The `esp-images/` folder is created automatically on first launch

---

## Setup

1. Build the mod:
   ```bash
   ./gradlew build
   ```
2. Copy the JAR from `build/libs/esp-addon-1.0.0.jar` into your `mods/` folder (alongside Boze).
3. Launch Minecraft. The folder `.minecraft/esp-images/` is created automatically.
4. Drop your PNG files into `.minecraft/esp-images/`.
5. Configure filenames inside Boze's module settings.

---

## In-game Settings

| Setting | Default | Description |
|---|---|---|
| `TargetOnly` | `false` | Only show overlay when crosshair is on the entity |
| `PlayersOnly` | `true` | Restrict to player entities |
| `ShowOnMobs` | `false` | Also show on mobs (needs PlayersOnly = off) |
| `DefaultImage` | `default.png` | Fallback image filename |
| `PlayerImage` | *(blank)* | Custom image for players (blank = use DefaultImage) |
| `MobImage` | *(blank)* | Custom image for mobs (blank = use DefaultImage) |

---

## Image Folder

```
.minecraft/
└── esp-images/
    ├── default.png      ← shown for all entities unless overridden
    ├── player.png       ← shown for players if PlayerImage = "player.png"
    └── zombie.png       ← shown for mobs if MobImage = "zombie.png"
```

Supported formats: **PNG** (recommended, full transparency), **GIF** (first frame used).

To reload an image mid-session, you currently need to restart the game.

---

## Building Requirements

- Java 21+
- Gradle (wrapper included)
- Minecraft 1.21.4 + Fabric Loader 0.16.10
- Boze Client installed

---

## License

MIT
