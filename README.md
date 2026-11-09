<div align="center">

# BlueMap Blockscan

**Automated modded block model, texture, and property extractor for [BlueMap](https://github.com/BlueMap-Minecraft/BlueMap).**  
*Inspired by the legendary [DynmapBlockScan](https://github.com/webbukkit/DynmapBlockScan), rebuilt from the ground up for modern Minecraft.*

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-brightgreen.svg?logo=minecraft)](https://www.minecraft.net/)
[![NeoForge](https://img.shields.io/badge/NeoForge-21.1.265-orange.svg)](https://neoforged.net/)
[![Java](https://img.shields.io/badge/Java-21-blue.svg?logo=openjdk)](https://adoptium.net/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

</div>

---

## Overview

In vanilla Minecraft, **BlueMap** reads static JSON models and textures to generate high-fidelity, interactive 3D web maps. However, when playing with modpacks on **NeoForge**, many modded blocks fail to render correctly:

- **Dynamic & Code-Baked Models:** Heavy mods (*Create, Mekanism, FramedBlocks, Applied Energistics 2, Storage Drawers, Rechiseled, etc.*) frequently use Java-side model loaders (`IGeometryLoader`, `IDynamicBakedModel`, connected textures, and runtime geometry) rather than static JSON files. BlueMap cannot parse compiled Java code, leaving these blocks invisible, flat, or displaying missing-texture placeholders.
- **Occlusion & Culling Glitches:** Without explicit block properties, BlueMap cannot tell if custom blocks are solid, transparent, or waterlogged. This causes severe X-ray holes in the terrain or causes solid blocks not to cull neighbor faces, bloating the web map with millions of unnecessary internal polygons.
- **Runtime Atlas Sprites:** Mod textures stitched directly into the game's client texture atlas during startup may not exist as standalone PNGs in mod JARs.

**BlueMap Blockscan** solves this entirely. Running on a client instance with your modpack loaded, it queries Minecraft's live rendering pipeline, decompiles the true baked geometry and textures for every modded blockstate, and compiles everything into a ready-to-load **BlueMap Resource Pack** (`.zip`).

---

## Features

- **Seamless Server-Side Pack Uploading:**
  - Playing on a dedicated server? No need to manually SFTP or copy files to the server host.
  - If you are OP on the server, you can click the in-chat `[Upload to Server]` button or run `/bluemap_blockscan upload`.
  - BlueMap Blockscan streams the pack to the server in chunks, validates OP permissions, saves it into the server's `config/bluemap/packs/` folder, and automatically triggers `/bluemap reload`.
- **Smart Incremental & Single-Mod Updating:**
  - Added a new mod to your pack? You do **not** need to re-scan your whole pack.
  - Run `/bluemap_blockscan update` to auto-detect newly installed mods, scan only them in seconds, and merge them into your existing pack.
  - Or target a single mod with `/bluemap_blockscan scan <modid>` without touching the rest of your pack.
- **Atomic & Non-Destructive Packaging:**
  - Merges new models and textures with existing pack entries.
  - Updates are staged in a `.tmp` file and atomically moved into place upon completion—your resource pack is never corrupted if interrupted.
- **Automated `blockProperties.json` Generation:**
  - Extracts face occlusion (`canOcclude()`), solid culling (`isSolid()`), identical neighbor culling for glass and leaves (`cullingIdentical`), waterlogged flags (`alwaysWaterlogged`), offset functions (`randomOffset`), and light emission.
- **Automated Model Deduplication & Blockstate Compression:**
  - Blocks with dozens or hundreds of state permutations (e.g. pipes, cables, facing directions) automatically share identical model geometry files.
  - Blocks with identical shapes across all states compress into a single wildcard variant (`""`), eliminating hundreds of thousands of redundant files and cutting pack size by over 98%.
- **Lean Server-Friendly Mode (No Duplicate Textures):**
  - BlueMap already reads textures directly from mod JARs via `scan-for-mod-resources`. BlueMap Blockscan generates models referencing those textures without redundantly packing tens of thousands of duplicate PNGs into the zip.
  - Keeps the output pack under 25 MB and allows BlueMap to boot and open its web server in 2 to 4 seconds, running smoothly on servers with 4 GB to 12 GB RAM without OutOfMemoryErrors.
- **3D Model Decompilation (JSON & OBJ):**
  - **Planar / Cuboid Geometry:** Converted into standard Minecraft JSON block models (`models/block/...`).
  - **Complex / Slanted Geometry:** Automatically detects non-axis-aligned quads (gears, diagonal pipes, chutes, slopes) and generates Wavefront `.obj` models compatible with BlueMap and *BlueMapModelLoaders*.
  - **Blockstate Synthesis:** Generates matching `blockstates/<block>.json` definitions mapping state properties (e.g., `facing=north,powered=true`) to generated models.
- **Texture & Animation Ripping:**
  - Extracts full-resolution `.png` textures and `.mcmeta` animations from the client texture atlas and resource manager into `assets/<modid>/textures/...`.
- **Asynchronous Background Worker:**
  - Runs on a dedicated daemon thread so your client FPS and game loop never stutter or lock up during scans.

---

## Commands

All commands are client-side (`RegisterClientCommandsEvent`) and run directly in singleplayer or while connected to a server:

| Command | Description |
| :--- | :--- |
| `/bluemap_blockscan update [--upload]` | **Incremental Scan:** Automatically detects any newly added mods, scans only them, and merges them into your pack. |
| `/bluemap_blockscan scan [--upload]` | **Smart Scan:** Runs an incremental scan if a pack exists, or performs a full scan if starting fresh. |
| `/bluemap_blockscan scan <modid> [--upload]` | **Single-Mod Scan:** Scans and updates only ONE specific mod (e.g. `/bluemap_blockscan scan create`). Supports tab completion. |
| `/bluemap_blockscan upload` | **Server Upload:** Uploads your generated pack to the server and reloads BlueMap (OP required). |
| `/bluemap_blockscan scan all --clean [--upload]` | **Clean Rebuild:** Completely wipes the existing pack and re-scans all installed mods from scratch. |
| `/bluemap_blockscan list` | **Status Check:** Lists all mods currently in the pack vs. any loaded mods that haven't been scanned yet. |
| `/bluemap_blockscan help` | Shows command help and syntax. |

---

## Quick Start Guide

### Step 1: Generate the Compatibility Pack
1. Install **BlueMap Blockscan** into your NeoForge 1.21.1 client `mods/` folder alongside your modpack.
2. Launch Minecraft and join a singleplayer world (or any server).
3. Open chat and run:
   ```mcfunction
   /bluemap_blockscan scan
   ```
4. The mod will output progress in chat and generate:
   ```
   config/bluemap/packs/bluemap-blockscan.zip
   ```

### Step 2: Apply to BlueMap

#### If Playing Singleplayer (Local BlueMap):
- The generated zip is saved directly into `config/bluemap/packs/bluemap-blockscan.zip`.
- Simply run `/bluemap reload` and then `/bluemap render <map>`.

#### If Running on a Dedicated Server:
*Minecraft dedicated servers are headless and lack client rendering classes (`BakedModel`, `TextureAtlasSprite`, etc.). BlueMap Blockscan generates the pack on the client where models exist, which can then be used anywhere.*
1. If BlueMap Blockscan is installed on the server and you are OP, simply run `/bluemap_blockscan upload` (or click `[Upload to Server]` in chat) to transfer and reload automatically.
2. Alternatively, copy the generated `config/bluemap/packs/bluemap-blockscan.zip` from your client to your server's `./config/bluemap/packs/` directory and run:
   ```mcfunction
   /bluemap reload
   /bluemap purge <map-name>
   /bluemap render <map-name>
   ```

### Important: BlueMap Web Server Startup Delay

In large modpacks (hundreds of mods), `bluemap-blockscan.zip` can exceed 1 GB and contain tens of thousands of textures.

During server startup or when running `/bluemap reload`, **BlueMap indexes and validates all textures and models before binding its HTTP web server port** (default port `8989`).

- **Delayed Port Binding:** BlueMap will actively index textures in the background for 1 to 2 minutes on startup (`Loading textures from: config\bluemap\packs\bluemap-blockscan.zip ...`).
- **Temporary 502 Bad Gateway:** If you access your map through a reverse proxy or server manager (e.g. AMP, Nginx, Caddy, Cloudflare), you will receive a **502 Bad Gateway** or connection timeout while BlueMap is still indexing.
- **What to do:** This is expected behavior. Check your server console or `debug.log` to monitor texture loading. As soon as BlueMap completes the resource stage, port 8989 will bind and the web interface will immediately come online.

---

## Workflow: Adding New Mods Later

When you add a new mod to your modpack down the road:
1. Launch Minecraft and run:
   ```mcfunction
   /bluemap_blockscan list
   ```
   *Output shows which new mods are missing from your pack:*
   ```
   === BlueMap Blockscan Status ===
   Mods in current pack (35): [farmersdelight, rechiseled, ...]
   Unscanned new mods (1): [create]
   Tip: Run /bluemap_blockscan update to scan only the missing mods.
   ```
2. Run:
   ```mcfunction
   /bluemap_blockscan update
   ```
   *Only the new mod is scanned in seconds and seamlessly merged into `bluemap-blockscan.zip`.*
3. Run `/bluemap_blockscan upload` (or copy the updated zip to your server) and run `/bluemap reload`.

---

## Configuration

Configuration file is located at `config/bluemap_blockscan-client.toml`:

```toml
# Destination zip path (defaults directly to BlueMap's packs directory)
outputPackPath = "config/bluemap/packs/bluemap-blockscan.zip"

# When true, '/bluemap_blockscan scan' automatically detects and scans only new mods
autoIncremental = true

# Also output an uncompressed folder for manual inspection/debugging
exportUnpacked = false
unpackedPath = "bluemap_blockscan_export"

# Whether to scan vanilla Minecraft blocks (false by default since BlueMap includes vanilla assets)
includeMinecraft = false

# Feature toggles
dumpTextures = false # false keeps pack under 25MB and prevents server OOM crashes
exportModels = true
exportProperties = true

# Mod namespaces to exclude from scanning
excludedNamespaces = ["c", "neoforge", "forge"]
```

---

## Architecture & How It Works

```
┌────────────────────────────────────────────────────────┐
│             Minecraft Client (NeoForge 1.21.1)         │
├─────────────────────────┬──────────────────────────────┤
│ BlockRegistry           │ BuiltInRegistries.BLOCK      │
│ State Enumeration       │ BlockState combinations      │
│ Property Extractor      │ Occlusion, Culling, Light    │
│ Model Decompiler        │ BakedModel -> BakedQuads     │
│ Vertex & UV Decoder     │ 32-bit packed float unroller │
│ Texture Atlas Ripper    │ SpriteContents & PNG dump    │
└─────────────────────────┴───────────────┬──────────────┘
                                          │
                        ┌─────────────────▼─────────────────┐
                        │        ResourcePackWriter         │
                        │    (Atomic & Non-Destructive)     │
                        ├───────────────────────────────────┤
                        │ • pack.mcmeta (Format 34)         │
                        │ • assets/<mod>/blockProperties.json│
                        │ • assets/<mod>/models/block/*.json │
                        │ • assets/<mod>/models/block/*.obj  │
                        │ • assets/<mod>/blockstates/*.json  │
                        │ • assets/<mod>/textures/block/*.png│
                        └─────────────────┬─────────────────┘
                                          │
                                          ▼
                         config/bluemap/packs/bluemap-blockscan.zip
                                          │
                                          ▼
                                      BlueMap
```

1. **Vertex Decoding:** In Minecraft 1.21.1, `DefaultVertexFormat.BLOCK` encodes 8 integers per vertex (32 bytes per quad). BlueMap Blockscan unpacks `[x, y, z]` coordinates, reads atlas UV floats, and normalizes them back into sprite-relative `[0..16]` UV space.
2. **Axis-Aligned Optimization:** Planar/axis-aligned quads are reconstructed into standard Minecraft JSON model elements (`from`, `to`, `faces`).
3. **Complex Geometry Fallback:** Complex non-axis-aligned quads (e.g. diagonal gears, pipe elbows, slopes) are exported as standard Wavefront `.obj` models.
4. **Occlusion & Lighting:** Block state occlusion and light values are serialized into `blockProperties.json` so BlueMap's chunk mesher correctly culls neighbor faces and lights blocks.

---

## Building from Source

### Prerequisites
- **Java 21 JDK** or newer
- Git

### Build Instructions
```bash
git clone https://github.com/your-username/bluemap_blockscan.git
cd "bluemap_blockscan"
./gradlew build
```

The compiled mod JAR will be located at:
```
build/libs/bluemap_blockscan-0.1.0.jar
```

---

## Credits & Acknowledgements

- **[DynmapBlockScan](https://github.com/webbukkit/DynmapBlockScan)** by webbukkit: The original pioneer of runtime block scanning for Minecraft web maps.
- **[BlueMap](https://github.com/BlueMap-Minecraft/BlueMap)** by TBlueF: The modern 3D map engine for Minecraft.
- **[NeoForge](https://neoforged.net/)**: The next-generation Minecraft modding platform.
