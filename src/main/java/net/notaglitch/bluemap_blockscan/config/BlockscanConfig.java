package net.notaglitch.bluemap_blockscan.config;

import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

public class BlockscanConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.ConfigValue<String> OUTPUT_PACK_PATH = BUILDER
            .comment("The file path where the generated BlueMap resource pack zip will be saved.",
                     "Defaults directly to BlueMap's packs directory.")
            .define("outputPackPath", "config/bluemap/packs/bluemap-blockscan.zip");

    public static final ModConfigSpec.BooleanValue AUTO_INCREMENTAL = BUILDER
            .comment("When true, scanning without arguments will automatically detect and scan only newly added mods",
                     "instead of rescanning every mod from scratch.")
            .define("autoIncremental", true);

    public static final ModConfigSpec.BooleanValue EXPORT_UNPACKED = BUILDER
            .comment("Whether to also write out an uncompressed folder for manual inspection.")
            .define("exportUnpacked", false);

    public static final ModConfigSpec.ConfigValue<String> UNPACKED_PATH = BUILDER
            .comment("Directory for unpacked export if exportUnpacked is enabled.")
            .define("unpackedPath", "bluemap_blockscan_export");

    public static final ModConfigSpec.BooleanValue INCLUDE_MINECRAFT = BUILDER
            .comment("Whether to scan vanilla minecraft blocks. (Default false, since BlueMap includes vanilla assets).")
            .define("includeMinecraft", false);

    public static final ModConfigSpec.BooleanValue DUMP_TEXTURES = BUILDER
            .comment("Whether to extract and save PNG textures from the game texture atlas.")
            .define("dumpTextures", true);

    public static final ModConfigSpec.BooleanValue EXPORT_MODELS = BUILDER
            .comment("Whether to reconstruct and export 3D JSON/OBJ block models.")
            .define("exportModels", true);

    public static final ModConfigSpec.BooleanValue EXPORT_PROPERTIES = BUILDER
            .comment("Whether to generate blockProperties.json (occlusion, culling, waterlogging).")
            .define("exportProperties", true);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> EXCLUDED_NAMESPACES = BUILDER
            .comment("List of mod namespaces to exclude from scanning.")
            .defineListAllowEmpty("excludedNamespaces", List.of("c", "neoforge", "forge"), obj -> obj instanceof String);

    public static final ModConfigSpec SPEC = BUILDER.build();

    public static String outputPackPath = "config/bluemap/packs/bluemap-blockscan.zip";
    public static boolean autoIncremental = true;
    public static boolean exportUnpacked = false;
    public static String unpackedPath = "bluemap_blockscan_export";
    public static boolean includeMinecraft = false;
    public static boolean dumpTextures = true;
    public static boolean exportModels = true;
    public static boolean exportProperties = true;
    public static List<? extends String> excludedNamespaces = List.of("c", "neoforge", "forge");

    public static void onLoad(final ModConfigEvent event) {
        outputPackPath = OUTPUT_PACK_PATH.get();
        autoIncremental = AUTO_INCREMENTAL.get();
        exportUnpacked = EXPORT_UNPACKED.get();
        unpackedPath = UNPACKED_PATH.get();
        includeMinecraft = INCLUDE_MINECRAFT.get();
        dumpTextures = DUMP_TEXTURES.get();
        exportModels = EXPORT_MODELS.get();
        exportProperties = EXPORT_PROPERTIES.get();
        excludedNamespaces = EXCLUDED_NAMESPACES.get();
    }
}
