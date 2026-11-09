package net.notaglitch.bluemap_blockscan.scanner;

import net.notaglitch.bluemap_blockscan.config.BlockscanConfig;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class ScanOptions {
    private final Set<String> targetNamespaces;
    private final boolean clean;
    private final boolean incremental;
    private final boolean autoUpload;
    private final boolean dumpTextures;
    private final boolean exportModels;
    private final boolean exportProperties;
    private final boolean includeMinecraft;
    private final Set<String> excludedNamespaces;
    private final Path zipOutputPath;
    private final Path unpackedOutputPath;

    public ScanOptions(Set<String> targetNamespaces,
                       boolean clean,
                       boolean incremental,
                       boolean autoUpload,
                       Boolean dumpTextures,
                       Boolean exportModels,
                       Boolean exportProperties,
                       String customZipPath) {
        if (targetNamespaces != null && !targetNamespaces.isEmpty()) {
            Set<String> lower = new HashSet<>();
            for (String s : targetNamespaces) {
                if (!s.equalsIgnoreCase("all")) {
                    lower.add(s.toLowerCase(Locale.ROOT));
                }
            }
            this.targetNamespaces = lower.isEmpty() ? null : lower;
        } else {
            this.targetNamespaces = null;
        }

        this.clean = clean;
        this.incremental = incremental;
        this.autoUpload = autoUpload;
        this.dumpTextures = dumpTextures != null ? dumpTextures : BlockscanConfig.dumpTextures;
        this.exportModels = exportModels != null ? exportModels : BlockscanConfig.exportModels;
        this.exportProperties = exportProperties != null ? exportProperties : BlockscanConfig.exportProperties;
        this.includeMinecraft = BlockscanConfig.includeMinecraft;
        this.excludedNamespaces = new HashSet<>(BlockscanConfig.excludedNamespaces);

        String zipPathStr = customZipPath != null ? customZipPath : BlockscanConfig.outputPackPath;
        this.zipOutputPath = Paths.get(zipPathStr);
        this.unpackedOutputPath = BlockscanConfig.exportUnpacked ? Paths.get(BlockscanConfig.unpackedPath) : null;
    }

    public static ScanOptions createDefault() {
        return new ScanOptions(null, false, BlockscanConfig.autoIncremental, false, null, null, null, null);
    }

    public static ScanOptions createWithUpload() {
        return new ScanOptions(null, false, BlockscanConfig.autoIncremental, true, null, null, null, null);
    }

    public static ScanOptions createForSingleMod(String modid, boolean autoUpload) {
        return new ScanOptions(Collections.singleton(modid), false, true, autoUpload, null, null, null, null);
    }

    public static ScanOptions createCleanFull(boolean autoUpload) {
        return new ScanOptions(null, true, false, autoUpload, null, null, null, null);
    }

    public boolean shouldScanBlock(String namespace) {
        String nsLower = namespace.toLowerCase(Locale.ROOT);
        if (targetNamespaces != null && !targetNamespaces.isEmpty()) {
            return targetNamespaces.contains(nsLower);
        }
        if (!includeMinecraft && "minecraft".equals(nsLower)) {
            return false;
        }
        return !excludedNamespaces.contains(nsLower);
    }

    public Set<String> getTargetNamespaces() {
        return targetNamespaces;
    }

    public boolean isClean() {
        return clean;
    }

    public boolean isIncremental() {
        return incremental;
    }

    public boolean isAutoUpload() {
        return autoUpload;
    }

    public boolean isDumpTextures() {
        return dumpTextures;
    }

    public boolean isExportModels() {
        return exportModels;
    }

    public boolean isExportProperties() {
        return exportProperties;
    }

    public Path getZipOutputPath() {
        return zipOutputPath;
    }

    public Path getUnpackedOutputPath() {
        return unpackedOutputPath;
    }
}
