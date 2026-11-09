package net.notaglitch.bluemap_blockscan.model;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

public class ExtractedQuad {
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

        float hAspectRatio = 1.0f;
        if (sprite != null && sprite.contents() != null) {
            int w = sprite.contents().width();
            int h = sprite.contents().height();
            if (h > 0 && w > h) {
                hAspectRatio = (float) w / (float) h;
            }
        }

        float minRawU = 16.0f;
        float maxRawU = 0.0f;

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

            if (u[i] < minRawU) minRawU = u[i];
            if (u[i] > maxRawU) maxRawU = u[i];
        }

        // If this texture is a horizontal connected-texture sheet (e.g. Fusion/Rechiseled width > height),
        // and the quad is mapped across the entire sheet (rawSpan > 14), scale U down to map only
        // the base square tile (16.0 / hAspectRatio) so BlueMap renders a clean square tile
        // instead of compressing all connected tiles into vertical pinstripes.
        if (hAspectRatio > 1.0f && (maxRawU - minRawU) > 14.0f) {
            for (int i = 0; i < 4; i++) {
                u[i] = minRawU + (u[i] - minRawU) / hAspectRatio;
            }
        }
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
