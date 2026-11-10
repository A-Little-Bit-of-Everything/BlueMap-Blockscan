package net.notaglitch.bluemap_blockscan.scanner;

import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.notaglitch.bluemap_blockscan.model.*;
import net.notaglitch.bluemap_blockscan.pack.ResourcePackWriter;
import net.notaglitch.bluemap_blockscan.properties.BlockColorExtractor;
import net.notaglitch.bluemap_blockscan.properties.BlockPropertiesExtractor;
import net.notaglitch.bluemap_blockscan.texture.TextureExtractor;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class BlockScanner {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final AtomicBoolean SCANNING = new AtomicBoolean(false);

    public static boolean isScanning() {
        return SCANNING.get();
    }

    public static void startScan(ScanOptions options) {
        if (!SCANNING.compareAndSet(false, true)) {
            sendFeedback(Component.literal("A scan is already in progress!").withStyle(ChatFormatting.RED));
            return;
        }

        Thread scannerThread = new Thread(() -> runScan(options), "BlueMap-Blockscan-Worker");
        scannerThread.setDaemon(true);
        scannerThread.start();
    }

    private static void runScan(ScanOptions options) {
        long startTime = System.currentTimeMillis();
        Path zipPath = options.getZipOutputPath();
        Path unpackedDir = options.getUnpackedOutputPath();

        try {
            boolean packExists = Files.exists(zipPath) && Files.size(zipPath) > 0;
            Set<String> existingInPack = packExists ? ResourcePackWriter.getExistingNamespaces(zipPath) : Collections.emptySet();

            // Find all loaded candidate namespaces
            Set<String> allLoadedNamespaces = BuiltInRegistries.BLOCK.keySet().stream()
                    .map(ResourceLocation::getNamespace)
                    .map(String::toLowerCase)
                    .filter(options::shouldScanBlock)
                    .collect(Collectors.toCollection(TreeSet::new));

            Set<String> namespacesToScan;
            Set<String> namespacesToOverwrite;
            boolean isIncremental = options.isIncremental() && packExists && !options.isClean();

            if (options.getTargetNamespaces() != null && !options.getTargetNamespaces().isEmpty()) {
                // User targeted specific mods (e.g. /bluemap_blockscan scan create)
                namespacesToScan = new HashSet<>(options.getTargetNamespaces());
                namespacesToOverwrite = namespacesToScan;
                sendFeedback(Component.literal("Scanning specific mod(s): [" + String.join(", ", namespacesToScan) + "] and merging into pack...")
                        .withStyle(ChatFormatting.AQUA));
            } else if (isIncremental) {
                // Auto-detect newly added mods not present in existing pack
                Set<String> newMods = new TreeSet<>();
                for (String ns : allLoadedNamespaces) {
                    if (!existingInPack.contains(ns)) {
                        newMods.add(ns);
                    }
                }

                if (newMods.isEmpty()) {
                    sendFeedback(Component.literal("All " + allLoadedNamespaces.size() + " loaded mods are already present in the BlueMap pack!")
                            .withStyle(ChatFormatting.GREEN));
                    sendFeedback(Component.literal("No new mods detected. (Use '/bluemap_blockscan scan all --clean' to force a full re-scan).")
                            .withStyle(ChatFormatting.GRAY));
                    SCANNING.set(false);
                    return;
                }

                namespacesToScan = newMods;
                namespacesToOverwrite = newMods;
                sendFeedback(Component.literal("Detected " + newMods.size() + " newly added mod(s): [" + String.join(", ", newMods) + "]")
                        .withStyle(ChatFormatting.GOLD));
                sendFeedback(Component.literal("Performing incremental scan (preserving " + existingInPack.size() + " previously scanned mods)...")
                        .withStyle(ChatFormatting.AQUA));
            } else {
                // Full scan of all candidate mods
                namespacesToScan = allLoadedNamespaces;
                namespacesToOverwrite = namespacesToScan;
                sendFeedback(Component.literal("Starting full scan for all " + allLoadedNamespaces.size() + " loaded mods...")
                        .withStyle(ChatFormatting.AQUA));
            }

            try (ResourcePackWriter packWriter = new ResourcePackWriter(zipPath, unpackedDir, !options.isClean(), namespacesToOverwrite)) {
                BlockPropertiesExtractor propertiesExtractor = new BlockPropertiesExtractor();
                BlockColorExtractor colorExtractor = new BlockColorExtractor();
                TextureExtractor textureExtractor = new TextureExtractor();

                List<Block> blocksToScan = new ArrayList<>();
                for (Block block : BuiltInRegistries.BLOCK) {
                    ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
                    if (namespacesToScan.contains(id.getNamespace().toLowerCase(Locale.ROOT))) {
                        blocksToScan.add(block);
                    }
                }

                int totalBlocks = blocksToScan.size();
                sendFeedback(Component.literal("Found " + totalBlocks + " blocks to process across " + namespacesToScan.size() + " mod(s).")
                        .withStyle(ChatFormatting.GRAY));

                int scannedBlocks = 0;
                int skippedBlocks = 0;
                int totalStatesScanned = 0;
                int totalModelsExported = 0;

                for (Block block : blocksToScan) {
                    ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
                    String namespace = id.getNamespace();
                    String blockPath = id.getPath();

                    if (options.isSkipExistingModels() && ModelExtractor.hasStaticModelAvailable(block)) {
                        skippedBlocks++;
                        scannedBlocks++;
                        continue;
                    }

                    // 1. Properties
                    if (options.isExportProperties()) {
                        propertiesExtractor.processBlock(block);
                    }

                    // 2. States and Models with content deduplication
                    List<BlockState> states = block.getStateDefinition().getPossibleStates();
                    BlockStateBuilder blockStateBuilder = new BlockStateBuilder();
                    Map<String, String> contentToModelLoc = new HashMap<>();
                    Map<String, String> variantToModelLoc = new LinkedHashMap<>();
                    int uniqueBlockModels = 0;
                    boolean blockHasTint = false;

                    for (int stateIndex = 0; stateIndex < states.size(); stateIndex++) {
                        BlockState state = states.get(stateIndex);
                        totalStatesScanned++;

                        List<ExtractedQuad> quads = ModelExtractor.extractQuadsForState(state);
                        if (quads.isEmpty()) {
                            continue;
                        }

                        for (ExtractedQuad quad : quads) {
                            if (quad.tintIndex >= 0) {
                                blockHasTint = true;
                                break;
                            }
                        }

                        // Dump textures if explicitly requested
                        if (options.isDumpTextures()) {
                            for (ExtractedQuad quad : quads) {
                                if (quad.sprite != null) {
                                    textureExtractor.exportSprite(quad.sprite, packWriter);
                                }
                            }
                        }

                        // Export model with deduplication
                        if (options.isExportModels()) {
                            String stateVariant = BlockStateBuilder.serializeStateProperties(state);
                            JsonObject modelJson = JsonModelBuilder.buildModelJson(quads);
                            String modelKey = modelJson.toString();

                            String modelResourceLocation = contentToModelLoc.get(modelKey);
                            if (modelResourceLocation == null) {
                                String modelName = (uniqueBlockModels == 0 && states.size() == 1)
                                        ? blockPath
                                        : (blockPath + (uniqueBlockModels == 0 ? "" : "_m" + uniqueBlockModels));
                                modelResourceLocation = namespace + ":block/" + modelName;
                                contentToModelLoc.put(modelKey, modelResourceLocation);

                                packWriter.putJson("assets/" + namespace + "/models/block/" + modelName + ".json", modelJson);

                                boolean hasNonAxisAligned = quads.stream().anyMatch(q -> !q.isPlanarAxisAligned());
                                if (hasNonAxisAligned) {
                                    String objContent = ObjModelBuilder.buildObj(modelName, quads);
                                    packWriter.putString("assets/" + namespace + "/models/block/" + modelName + ".obj", objContent);
                                }

                                uniqueBlockModels++;
                                totalModelsExported++;
                            }

                            variantToModelLoc.put(stateVariant, modelResourceLocation);
                        }
                    }

                    if (!variantToModelLoc.isEmpty() && options.isExportModels()) {
                        // If all states share the exact same model, compress into a single wildcard variant ""
                        if (uniqueBlockModels == 1) {
                            String singleModel = contentToModelLoc.values().iterator().next();
                            blockStateBuilder.addVariant("", singleModel);
                        } else {
                            for (Map.Entry<String, String> entry : variantToModelLoc.entrySet()) {
                                blockStateBuilder.addVariant(entry.getKey(), entry.getValue());
                            }
                        }
                        JsonObject stateJson = blockStateBuilder.buildBlockStateJson();
                        packWriter.putJson("assets/" + namespace + "/blockstates/" + blockPath + ".json", stateJson);
                    }

                    // 3. Biome / Color mapping
                    colorExtractor.processBlock(block, blockHasTint);

                    scannedBlocks++;
                    if (scannedBlocks % 500 == 0 || scannedBlocks == totalBlocks) {
                        int percent = (int) ((scannedBlocks / (float) Math.max(1, totalBlocks)) * 100);
                        sendFeedback(Component.literal(String.format("Progress: %d/%d blocks (%d%%)...", scannedBlocks, totalBlocks, percent))
                                .withStyle(ChatFormatting.YELLOW));
                    }
                }

                // Write blockProperties.json for each scanned namespace
                if (options.isExportProperties()) {
                    for (Map.Entry<String, JsonObject> entry : propertiesExtractor.getNamespacePropertyMaps().entrySet()) {
                        String ns = entry.getKey();
                        JsonObject props = entry.getValue();
                        packWriter.putJson("assets/" + ns + "/blockProperties.json", props);
                    }
                }

                // Write blockColors.json for each scanned namespace
                for (Map.Entry<String, JsonObject> entry : colorExtractor.getNamespaceColorMaps().entrySet()) {
                    String ns = entry.getKey();
                    JsonObject colors = entry.getValue();
                    if (!colors.entrySet().isEmpty()) {
                        packWriter.putJson("assets/" + ns + "/blockColors.json", colors);
                    }
                }

                long elapsed = (System.currentTimeMillis() - startTime) / 1000;
                Set<String> finalPackNamespaces = ResourcePackWriter.getExistingNamespaces(zipPath);

                sendFeedback(Component.literal("BlueMap Blockscan complete in " + elapsed + "s!").withStyle(ChatFormatting.GREEN));
                sendFeedback(Component.literal(String.format("Exported: %d blocks (%d skipped with existing models), %d states, %d unique models into %d files.",
                        scannedBlocks - skippedBlocks, skippedBlocks, totalStatesScanned, totalModelsExported, packWriter.getFileCount())).withStyle(ChatFormatting.GRAY));

                String absPath = zipPath.toAbsolutePath().toString();
                Path parentDir = zipPath.toAbsolutePath().getParent();
                String parentPath = parentDir != null ? parentDir.toString() : absPath;

                MutableComponent fileLink = Component.literal(absPath)
                        .withStyle(style -> style
                                .withColor(ChatFormatting.AQUA)
                                .withUnderlined(true)
                                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, absPath))
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to open file"))));

                MutableComponent openFolderBtn = Component.literal("[Open Folder]")
                        .withStyle(style -> style
                                .withColor(ChatFormatting.YELLOW)
                                .withBold(true)
                                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, parentPath))
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to open folder in File Explorer"))));

                MutableComponent copyPathBtn = Component.literal("[Copy Path]")
                        .withStyle(style -> style
                                .withColor(ChatFormatting.GREEN)
                                .withBold(true)
                                .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, absPath))
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to copy full path to clipboard"))));

                MutableComponent uploadBtn = Component.literal("[Upload to Server]")
                        .withStyle(style -> style
                                .withColor(ChatFormatting.AQUA)
                                .withBold(true)
                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/bluemap_blockscan upload"))
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to upload generated pack to server and reload BlueMap (OP required)"))));

                MutableComponent actionLine = Component.literal("  ")
                        .append(openFolderBtn)
                        .append(Component.literal("   "))
                        .append(copyPathBtn)
                        .append(Component.literal("   "))
                        .append(uploadBtn);

                sendFeedback(fileLink);
                sendFeedback(actionLine);

                if (options.isAutoUpload()) {
                    net.notaglitch.bluemap_blockscan.network.ClientUploadManager.uploadPack(zipPath);
                }
            }

        } catch (Exception e) {
            LOGGER.error("Error during BlueMap Blockscan", e);
            sendFeedback(Component.literal("Error during scan: " + e.getMessage()).withStyle(ChatFormatting.RED));
        } finally {
            SCANNING.set(false);
        }
    }

    public static void listScannedMods(Path zipPath) {
        Set<String> inPack = ResourcePackWriter.getExistingNamespaces(zipPath);
        Set<String> allLoaded = BuiltInRegistries.BLOCK.keySet().stream()
                .map(ResourceLocation::getNamespace)
                .map(String::toLowerCase)
                .filter(ns -> !"minecraft".equals(ns) && !ns.equals("c") && !ns.equals("neoforge") && !ns.equals("forge"))
                .collect(Collectors.toCollection(TreeSet::new));

        Set<String> unscanned = new TreeSet<>(allLoaded);
        unscanned.removeAll(inPack);

        sendFeedback(Component.literal("=== BlueMap Blockscan Status ===").withStyle(ChatFormatting.GOLD));
        sendFeedback(Component.literal("Mods in current pack (" + inPack.size() + "): ").withStyle(ChatFormatting.GREEN)
                .append(Component.literal(inPack.isEmpty() ? "None" : String.join(", ", inPack)).withStyle(ChatFormatting.GRAY)));
        if (unscanned.isEmpty()) {
            sendFeedback(Component.literal("All installed mods are scanned!").withStyle(ChatFormatting.GREEN));
        } else {
            sendFeedback(Component.literal("Unscanned new mods (" + unscanned.size() + "): ").withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal(String.join(", ", unscanned)).withStyle(ChatFormatting.RED)));
            sendFeedback(Component.literal("Tip: Run /bluemap_blockscan update to scan only the missing mods.").withStyle(ChatFormatting.GRAY));
        }
    }

    public static void sendFeedback(Component component) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendSystemMessage(component);
        }
        LOGGER.info(component.getString());
    }
}
