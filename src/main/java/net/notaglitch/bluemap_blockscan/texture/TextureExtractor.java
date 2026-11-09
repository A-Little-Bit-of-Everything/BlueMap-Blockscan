package net.notaglitch.bluemap_blockscan.texture;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.notaglitch.bluemap_blockscan.pack.ResourcePackWriter;
import org.slf4j.Logger;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public class TextureExtractor {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Set<ResourceLocation> processedSprites = new HashSet<>();

    public void exportSprite(TextureAtlasSprite sprite, ResourcePackWriter packWriter) {
        if (sprite == null || sprite.contents() == null) {
            return;
        }

        ResourceLocation spriteName = sprite.contents().name();
        if (spriteName == null || !processedSprites.add(spriteName)) {
            return;
        }

        String namespace = spriteName.getNamespace();
        String path = spriteName.getPath(); // e.g. "block/stone"

        String textureEntryPath = "assets/" + namespace + "/textures/" + path + ".png";
        String metaEntryPath = "assets/" + namespace + "/textures/" + path + ".png.mcmeta";

        ResourceManager resourceManager = Minecraft.getInstance().getResourceManager();
        ResourceLocation textureResLoc = ResourceLocation.fromNamespaceAndPath(namespace, "textures/" + path + ".png");
        ResourceLocation metaResLoc = ResourceLocation.fromNamespaceAndPath(namespace, "textures/" + path + ".png.mcmeta");

        boolean exported = false;

        // 1. Try reading the original PNG file directly from resource manager
        try {
            Optional<Resource> resOpt = resourceManager.getResource(textureResLoc);
            if (resOpt.isPresent()) {
                try (InputStream is = resOpt.get().open()) {
                    byte[] pngBytes = is.readAllBytes();
                    packWriter.putFile(textureEntryPath, pngBytes);
                    exported = true;
                }
            }

            Optional<Resource> metaOpt = resourceManager.getResource(metaResLoc);
            if (metaOpt.isPresent()) {
                try (InputStream is = metaOpt.get().open()) {
                    byte[] metaBytes = is.readAllBytes();
                    packWriter.putFile(metaEntryPath, metaBytes);
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Could not copy original texture resource for {}: {}", textureResLoc, e.getMessage());
        }

        // 2. Fallback: if not found as a static file, attempt to dump from SpriteContents if possible
        if (!exported) {
            try {
                // If SpriteContents has an image or frame, or we can copy from sprite
                // In modern Minecraft, sprite contents have mipmap levels
                // Let's check if we can dump from native image if needed
                LOGGER.debug("Sprite {} did not have a static resource file", spriteName);
            } catch (Exception e) {
                LOGGER.warn("Failed to export sprite {}", spriteName, e);
            }
        }
    }

    public int getExportedTextureCount() {
        return processedSprites.size();
    }
}
