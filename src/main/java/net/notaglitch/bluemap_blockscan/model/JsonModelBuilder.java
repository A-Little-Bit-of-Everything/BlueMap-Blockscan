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

        // Convert each quad into an element
        for (ExtractedQuad quad : quads) {
            JsonObject element = new JsonObject();

            float minX = Math.round(quad.getMinX() * 1600.0f) / 100.0f;
            float maxX = Math.round(quad.getMaxX() * 1600.0f) / 100.0f;
            float minY = Math.round(quad.getMinY() * 1600.0f) / 100.0f;
            float maxY = Math.round(quad.getMaxY() * 1600.0f) / 100.0f;
            float minZ = Math.round(quad.getMinZ() * 1600.0f) / 100.0f;
            float maxZ = Math.round(quad.getMaxZ() * 1600.0f) / 100.0f;

            // Ensure to >= from
            if (maxX < minX) { float t = minX; minX = maxX; maxX = t; }
            if (maxY < minY) { float t = minY; minY = maxY; maxY = t; }
            if (maxZ < minZ) { float t = minZ; minZ = maxZ; maxZ = t; }

            // Add tiny epsilon if plane to ensure standard parser tolerance
            if (minX == maxX) maxX += 0.001f;
            if (minY == maxY) maxY += 0.001f;
            if (minZ == maxZ) maxZ += 0.001f;

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

            if (dir != null) {
                faceObj.addProperty("cullface", dir.getSerializedName());
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
}
