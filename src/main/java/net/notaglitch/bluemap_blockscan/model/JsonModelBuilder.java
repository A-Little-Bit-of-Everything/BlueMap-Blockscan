package net.notaglitch.bluemap_blockscan.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

public class JsonModelBuilder {

    public static JsonObject buildModelJson(List<ExtractedQuad> quads) {
        JsonObject root = new JsonObject();
        JsonObject texturesObj = new JsonObject();
        JsonArray elementsArray = new JsonArray();

        Map<ResourceLocation, String> textureToKey = new LinkedHashMap<>();
        int textureIndex = 0;

        for (ExtractedQuad quad : quads) {
            if (!textureToKey.containsKey(quad.spriteLocation)) {
                String key = "tex" + (textureIndex++);
                textureToKey.put(quad.spriteLocation, key);
                texturesObj.addProperty(key, quad.spriteLocation.toString());
            }
        }

        root.add("textures", texturesObj);

        // Optimization: If the quads represent a standard full 1x1x1 cube (e.g. standard blocks, rechiseled cubes),
        // emit a single cuboid element [0, 0, 0] -> [16, 16, 16]. This enables BlueMap full-cube optimizations,
        // flawless neighbor culling, ambient occlusion, and drastically shrinks the JSON file size.
        if (isFullCube(quads)) {
            JsonObject element = new JsonObject();

            JsonArray fromArray = new JsonArray();
            fromArray.add(0.0f);
            fromArray.add(0.0f);
            fromArray.add(0.0f);

            JsonArray toArray = new JsonArray();
            toArray.add(16.0f);
            toArray.add(16.0f);
            toArray.add(16.0f);

            element.add("from", fromArray);
            element.add("to", toArray);

            JsonObject facesObj = new JsonObject();
            for (ExtractedQuad quad : quads) {
                JsonObject faceObj = new JsonObject();
                String texKey = "#" + textureToKey.get(quad.spriteLocation);
                faceObj.addProperty("texture", texKey);
                faceObj.addProperty("cullface", quad.direction.getSerializedName());

                if (quad.tintIndex >= 0) {
                    faceObj.addProperty("tintindex", quad.tintIndex);
                }

                // If UV is non-standard (such as cropped connecting texture sheets), specify UV
                float minU = Math.round(quad.getMinU() * 100.0f) / 100.0f;
                float minV = Math.round(quad.getMinV() * 100.0f) / 100.0f;
                float maxU = Math.round(quad.getMaxU() * 100.0f) / 100.0f;
                float maxV = Math.round(quad.getMaxV() * 100.0f) / 100.0f;

                if (Math.abs(minU - 0.0f) > 0.05f || Math.abs(minV - 0.0f) > 0.05f ||
                    Math.abs(maxU - 16.0f) > 0.05f || Math.abs(maxV - 16.0f) > 0.05f) {
                    JsonArray uvArray = new JsonArray();
                    uvArray.add(minU);
                    uvArray.add(minV);
                    uvArray.add(maxU);
                    uvArray.add(maxV);
                    faceObj.add("uv", uvArray);
                }

                facesObj.add(quad.direction.getSerializedName(), faceObj);
            }

            element.add("faces", facesObj);
            elementsArray.add(element);
            root.add("elements", elementsArray);
            return root;
        }

        // Non-cube geometry (stairs, slabs, tables, chairs, railings, panels)
        for (ExtractedQuad quad : quads) {
            JsonObject element = new JsonObject();

            float minX = Math.max(0.0f, Math.min(16.0f, Math.round(quad.getMinX() * 1600.0f) / 100.0f));
            float maxX = Math.max(0.0f, Math.min(16.0f, Math.round(quad.getMaxX() * 1600.0f) / 100.0f));
            float minY = Math.max(0.0f, Math.min(16.0f, Math.round(quad.getMinY() * 1600.0f) / 100.0f));
            float maxY = Math.max(0.0f, Math.min(16.0f, Math.round(quad.getMaxY() * 1600.0f) / 100.0f));
            float minZ = Math.max(0.0f, Math.min(16.0f, Math.round(quad.getMinZ() * 1600.0f) / 100.0f));
            float maxZ = Math.max(0.0f, Math.min(16.0f, Math.round(quad.getMaxZ() * 1600.0f) / 100.0f));

            if (maxX < minX) { float t = minX; minX = maxX; maxX = t; }
            if (maxY < minY) { float t = minY; minY = maxY; maxY = t; }
            if (maxZ < minZ) { float t = minZ; minZ = maxZ; maxZ = t; }

            // Ensure non-zero thickness for 2D planes strictly within [0.0, 16.0]
            if (minX == maxX) {
                if (maxX >= 16.0f) minX = 15.999f;
                else maxX = Math.min(16.0f, minX + 0.001f);
            }
            if (minY == maxY) {
                if (maxY >= 16.0f) minY = 15.999f;
                else maxY = Math.min(16.0f, minY + 0.001f);
            }
            if (minZ == maxZ) {
                if (maxZ >= 16.0f) minZ = 15.999f;
                else maxZ = Math.min(16.0f, minZ + 0.001f);
            }

            JsonArray fromArray = new JsonArray();
            fromArray.add(minX);
            fromArray.add(minY);
            fromArray.add(minZ);

            JsonArray toArray = new JsonArray();
            toArray.add(maxX);
            toArray.add(maxY);
            toArray.add(maxZ);

            element.add("from", fromArray);
            element.add("to", toArray);

            JsonObject facesObj = new JsonObject();
            JsonObject faceObj = new JsonObject();

            String texKey = "#" + textureToKey.get(quad.spriteLocation);
            faceObj.addProperty("texture", texKey);

            JsonArray uvArray = new JsonArray();
            uvArray.add(Math.round(quad.getMinU() * 100.0f) / 100.0f);
            uvArray.add(Math.round(quad.getMinV() * 100.0f) / 100.0f);
            uvArray.add(Math.round(quad.getMaxU() * 100.0f) / 100.0f);
            uvArray.add(Math.round(quad.getMaxV() * 100.0f) / 100.0f);
            faceObj.add("uv", uvArray);

            Direction dir = quad.direction;
            String faceName = dir != null ? dir.getSerializedName() : "north";

            // Only assign cullface if this quad actually has a cull direction!
            // Internal/decorative quads (direction=null in getQuads) have cullDirection=null and MUST NOT be culled!
            if (quad.cullDirection != null) {
                faceObj.addProperty("cullface", quad.cullDirection.getSerializedName());
            }

            if (quad.tintIndex >= 0) {
                faceObj.addProperty("tintindex", quad.tintIndex);
            }

            facesObj.add(faceName, faceObj);
            element.add("faces", facesObj);

            elementsArray.add(element);
        }

        root.add("elements", elementsArray);
        return root;
    }

    private static boolean isFullCube(List<ExtractedQuad> quads) {
        if (quads.size() != 6) return false;

        boolean[] hasDir = new boolean[6];
        for (ExtractedQuad q : quads) {
            if (q.cullDirection == null || q.direction != q.cullDirection) return false;
            int ord = q.direction.ordinal();
            if (hasDir[ord]) return false;
            hasDir[ord] = true;

            float minX = q.getMinX();
            float maxX = q.getMaxX();
            float minY = q.getMinY();
            float maxY = q.getMaxY();
            float minZ = q.getMinZ();
            float maxZ = q.getMaxZ();

            switch (q.direction) {
                case DOWN -> {
                    if (Math.abs(minY) > 0.02f || Math.abs(maxY) > 0.02f) return false;
                    if (Math.abs(minX) > 0.02f || Math.abs(maxX - 1.0f) > 0.02f) return false;
                    if (Math.abs(minZ) > 0.02f || Math.abs(maxZ - 1.0f) > 0.02f) return false;
                }
                case UP -> {
                    if (Math.abs(minY - 1.0f) > 0.02f || Math.abs(maxY - 1.0f) > 0.02f) return false;
                    if (Math.abs(minX) > 0.02f || Math.abs(maxX - 1.0f) > 0.02f) return false;
                    if (Math.abs(minZ) > 0.02f || Math.abs(maxZ - 1.0f) > 0.02f) return false;
                }
                case NORTH -> {
                    if (Math.abs(minZ) > 0.02f || Math.abs(maxZ) > 0.02f) return false;
                    if (Math.abs(minX) > 0.02f || Math.abs(maxX - 1.0f) > 0.02f) return false;
                    if (Math.abs(minY) > 0.02f || Math.abs(maxY - 1.0f) > 0.02f) return false;
                }
                case SOUTH -> {
                    if (Math.abs(minZ - 1.0f) > 0.02f || Math.abs(maxZ - 1.0f) > 0.02f) return false;
                    if (Math.abs(minX) > 0.02f || Math.abs(maxX - 1.0f) > 0.02f) return false;
                    if (Math.abs(minY) > 0.02f || Math.abs(maxY - 1.0f) > 0.02f) return false;
                }
                case WEST -> {
                    if (Math.abs(minX) > 0.02f || Math.abs(maxX) > 0.02f) return false;
                    if (Math.abs(minY) > 0.02f || Math.abs(maxY - 1.0f) > 0.02f) return false;
                    if (Math.abs(minZ) > 0.02f || Math.abs(maxZ - 1.0f) > 0.02f) return false;
                }
                case EAST -> {
                    if (Math.abs(minX - 1.0f) > 0.02f || Math.abs(maxX - 1.0f) > 0.02f) return false;
                    if (Math.abs(minY) > 0.02f || Math.abs(maxY - 1.0f) > 0.02f) return false;
                    if (Math.abs(minZ) > 0.02f || Math.abs(maxZ - 1.0f) > 0.02f) return false;
                }
            }
        }
        for (boolean b : hasDir) {
            if (!b) return false;
        }
        return true;
    }
}
