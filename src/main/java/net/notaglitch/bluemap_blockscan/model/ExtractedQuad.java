package net.notaglitch.bluemap_blockscan.model;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ExtractedQuad {
    private static final Map<ResourceLocation, Float> HORIZONTAL_ASPECT_RATIOS = new ConcurrentHashMap<>();

    public final float[] x = new float[4];
    public final float[] y = new float[4];
    public final float[] z = new float[4];
    public final float[] u = new float[4];
    public final float[] v = new float[4];
    public final TextureAtlasSprite sprite;
    public final Direction direction;
    public final Direction cullDirection; // null if unculled!
    public final int tintIndex;
    public final ResourceLocation spriteLocation;
    public final boolean isShade;

    public ExtractedQuad(BakedQuad quad, Direction cullDirection) {
        this.sprite = quad.getSprite();
        this.direction = quad.getDirection();
        this.cullDirection = cullDirection;
        this.tintIndex = quad.getTintIndex();
        this.isShade = quad.isShade();

        TextureAtlasSprite sprite = quad.getSprite();
        if (sprite != null && sprite.contents() != null && sprite.contents().name() != null) {
            this.spriteLocation = sprite.contents().name();
        } else {
            this.spriteLocation = ResourceLocation.fromNamespaceAndPath("minecraft", "block/stone");
        }

        int[] vertexData = quad.getVertices();
        float u0 = sprite != null ? sprite.getU0() : 0.0f;
        float u1 = sprite != null ? sprite.getU1() : 1.0f;
        float v0 = sprite != null ? sprite.getV0() : 0.0f;
        float v1 = sprite != null ? sprite.getV1() : 1.0f;

        float uSpan = Math.abs(u1 - u0) > 1e-6f ? (u1 - u0) : 1.0f;
        float vSpan = Math.abs(v1 - v0) > 1e-6f ? (v1 - v0) : 1.0f;

        // In 1.21.1, DefaultVertexFormat.BLOCK has 8 ints per vertex (32 ints total)
        for (int i = 0; i < 4; i++) {
            int offset = i * 8;
            x[i] = Float.intBitsToFloat(vertexData[offset + 0]);
            y[i] = Float.intBitsToFloat(vertexData[offset + 1]);
            z[i] = Float.intBitsToFloat(vertexData[offset + 2]);

            float atlasU = Float.intBitsToFloat(vertexData[offset + 4]);
            float atlasV = Float.intBitsToFloat(vertexData[offset + 5]);

            // Convert atlas UV back to sprite relative [0..16]
            u[i] = Math.max(0.0f, Math.min(16.0f, (atlasU - u0) / uSpan * 16.0f));
            v[i] = Math.max(0.0f, Math.min(16.0f, (atlasV - v0) / vSpan * 16.0f));
        }

        // Check the actual PNG dimensions in the mod JAR via ResourceManager.
        // CTM mods like Fusion store connected textures as multi-tile horizontal sheets (e.g. 64x16 or 80x16).
        // Since BlueMap loads the raw wide PNG from the mod JAR, we must scale U by the horizontal aspect ratio
        // (e.g. U / 4.0 or U / 5.0) so BlueMap samples only the base standalone tile instead of compressing all
        // connected frames into vertical pinstripes.
        float hAspectRatio = getTextureHorizontalAspect(this.spriteLocation);
        if (hAspectRatio > 1.0f) {
            for (int i = 0; i < 4; i++) {
                u[i] = u[i] / hAspectRatio;
            }
        }
    }

    public static float getTextureHorizontalAspect(ResourceLocation spriteLocation) {
        if (spriteLocation == null) return 1.0f;
        return HORIZONTAL_ASPECT_RATIOS.computeIfAbsent(spriteLocation, loc -> {
            try {
                ResourceLocation fileLoc = ResourceLocation.fromNamespaceAndPath(loc.getNamespace(), "textures/" + loc.getPath() + ".png");
                var resourceOpt = Minecraft.getInstance().getResourceManager().getResource(fileLoc);
                if (resourceOpt.isPresent()) {
                    try (var is = resourceOpt.get().open()) {
                        byte[] header = new byte[24];
                        int read = is.readNBytes(header, 0, 24);
                        if (read == 24) {
                            if ((header[0] & 0xFF) == 0x89 && header[1] == 'P' && header[2] == 'N' && header[3] == 'G') {
                                int width = ((header[16] & 0xFF) << 24) | ((header[17] & 0xFF) << 16) | ((header[18] & 0xFF) << 8) | (header[19] & 0xFF);
                                int height = ((header[20] & 0xFF) << 24) | ((header[21] & 0xFF) << 16) | ((header[22] & 0xFF) << 8) | (header[23] & 0xFF);
                                if (height > 0 && width > height) {
                                    return (float) width / (float) height;
                                }
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}
            return 1.0f;
        });
    }

    public boolean isPlanarAxisAligned() {
        boolean xSame = Math.abs(x[0] - x[1]) < 0.001f && Math.abs(x[0] - x[2]) < 0.001f && Math.abs(x[0] - x[3]) < 0.001f;
        boolean ySame = Math.abs(y[0] - y[1]) < 0.001f && Math.abs(y[0] - y[2]) < 0.001f && Math.abs(y[0] - y[3]) < 0.001f;
        boolean zSame = Math.abs(z[0] - z[1]) < 0.001f && Math.abs(z[0] - z[2]) < 0.001f && Math.abs(z[0] - z[3]) < 0.001f;
        return xSame || ySame || zSame;
    }

    public float getMinX() { return Math.min(Math.min(x[0], x[1]), Math.min(x[2], x[3])); }
    public float getMaxX() { return Math.max(Math.max(x[0], x[1]), Math.max(x[2], x[3])); }
    public float getMinY() { return Math.min(Math.min(y[0], y[1]), Math.min(y[2], y[3])); }
    public float getMaxY() { return Math.max(Math.max(y[0], y[1]), Math.max(y[2], y[3])); }
    public float getMinZ() { return Math.min(Math.min(z[0], z[1]), Math.min(z[2], z[3])); }
    public float getMaxZ() { return Math.max(Math.max(z[0], z[1]), Math.max(z[2], z[3])); }

    public float getMinU() { return Math.min(Math.min(u[0], u[1]), Math.min(u[2], u[3])); }
    public float getMaxU() { return Math.max(Math.max(u[0], u[1]), Math.max(u[2], u[3])); }
    public float getMinV() { return Math.min(Math.min(v[0], v[1]), Math.min(v[2], v[3])); }
    public float getMaxV() { return Math.max(Math.max(v[0], v[1]), Math.max(v[2], v[3])); }
}
