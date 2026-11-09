package net.notaglitch.bluemap_blockscan.pack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class ResourcePackWriter implements Closeable {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final Path finalZipPath;
    private final Path tempZipPath;
    private final Path unpackedDir;
    private final ZipOutputStream zipStream;
    private final ZipFile existingZipFile;
    private final Set<String> writtenPaths = new HashSet<>();

    public ResourcePackWriter(Path zipPath, Path unpackedDir, boolean mergeExisting, Set<String> namespacesToOverwrite) throws IOException {
        this.finalZipPath = zipPath;
        this.unpackedDir = unpackedDir;

        if (zipPath.getParent() != null) {
            Files.createDirectories(zipPath.getParent());
        }
        if (unpackedDir != null) {
            Files.createDirectories(unpackedDir);
        }

        // Use a temporary zip file to safely construct/update without corrupting the original
        this.tempZipPath = zipPath.resolveSibling(zipPath.getFileName().toString() + ".tmp");
        this.zipStream = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(tempZipPath.toFile())));

        if (mergeExisting && Files.exists(zipPath) && Files.size(zipPath) > 0) {
            ZipFile zf = null;
            try {
                zf = new ZipFile(zipPath.toFile());
            } catch (Exception e) {
                LOGGER.warn("Failed to open existing zip for merging: {}. Starting fresh.", e.getMessage());
            }
            this.existingZipFile = zf;

            if (this.existingZipFile != null) {
                copyExistingEntriesExcept(namespacesToOverwrite != null ? namespacesToOverwrite : Collections.emptySet());
            }
        } else {
            this.existingZipFile = null;
        }

        if (!writtenPaths.contains("pack.mcmeta")) {
            writePackMeta();
        }
    }

    public static Set<String> getExistingNamespaces(Path zipPath) {
        Set<String> namespaces = new TreeSet<>();
        if (!Files.exists(zipPath)) {
            return namespaces;
        }
        try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                // Match assets/<namespace>/...
                if (name.startsWith("assets/")) {
                    int slashIndex = name.indexOf('/', 7);
                    if (slashIndex > 7) {
                        namespaces.add(name.substring(7, slashIndex));
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Could not inspect existing pack namespaces: {}", e.getMessage());
        }
        return namespaces;
    }

    private void copyExistingEntriesExcept(Set<String> namespacesToOverwrite) {
        if (existingZipFile == null) return;

        Enumeration<? extends ZipEntry> entries = existingZipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            String name = entry.getName();

            // Check if this entry belongs to a namespace we intend to overwrite
            if (name.startsWith("assets/")) {
                int slashIndex = name.indexOf('/', 7);
                if (slashIndex > 7) {
                    String ns = name.substring(7, slashIndex);
                    if (namespacesToOverwrite.contains(ns.toLowerCase(Locale.ROOT))) {
                        // Skip this entry so the new scan replaces it
                        continue;
                    }
                }
            }

            try {
                ZipEntry newEntry = new ZipEntry(name);
                zipStream.putNextEntry(newEntry);
                try (InputStream is = existingZipFile.getInputStream(entry)) {
                    is.transferTo(zipStream);
                }
                zipStream.closeEntry();
                writtenPaths.add(name);
            } catch (Exception e) {
                LOGGER.warn("Failed to copy existing zip entry '{}': {}", name, e.getMessage());
            }
        }
    }

    private void writePackMeta() throws IOException {
        String mcmeta = """
                {
                  "pack": {
                    "pack_format": 34,
                    "description": "BlueMap Blockscan Generated Mod Support Pack"
                  }
                }
                """;
        putString("pack.mcmeta", mcmeta);
    }

    public synchronized void putFile(String relativePath, byte[] data) throws IOException {
        String normalizedPath = relativePath.replace('\\', '/');
        if (normalizedPath.startsWith("/")) {
            normalizedPath = normalizedPath.substring(1);
        }

        if (!writtenPaths.add(normalizedPath)) {
            return;
        }

        ZipEntry entry = new ZipEntry(normalizedPath);
        zipStream.putNextEntry(entry);
        zipStream.write(data);
        zipStream.closeEntry();

        if (unpackedDir != null) {
            Path target = unpackedDir.resolve(normalizedPath);
            if (target.getParent() != null) {
                Files.createDirectories(target.getParent());
            }
            Files.write(target, data);
        }
    }

    public void putString(String relativePath, String content) throws IOException {
        putFile(relativePath, content.getBytes(StandardCharsets.UTF_8));
    }

    public void putJson(String relativePath, JsonElement json) throws IOException {
        putString(relativePath, GSON.toJson(json));
    }

    @Override
    public void close() throws IOException {
        try {
            zipStream.finish();
        } finally {
            zipStream.close();
            if (existingZipFile != null) {
                try {
                    existingZipFile.close();
                } catch (Exception ignored) {}
            }
        }

        // Replace the final zip with the temp zip
        try {
            Files.move(tempZipPath, finalZipPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tempZipPath, finalZipPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public int getFileCount() {
        return writtenPaths.size();
    }
}
