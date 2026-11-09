package net.notaglitch.bluemap_blockscan.properties;

import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class BlockColorExtractor {

    private final Map<String, JsonObject> namespaceColorMaps = new HashMap<>();

    public void processBlock(Block block, boolean hasTintedQuads) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        String namespace = id.getNamespace();
        String path = id.getPath().toLowerCase(Locale.ROOT);
        BlockState defaultState = block.defaultBlockState();

        String colorCalculator = null;

        // 1. Foliage / Leaves / Vines / Saplings
        if (defaultState.is(BlockTags.LEAVES) || block instanceof LeavesBlock || path.contains("leaves") || path.contains("foliage")) {
            colorCalculator = "@foliage";
        } else if (block instanceof VineBlock || path.contains("vine")) {
            colorCalculator = "@foliage";
        } else if (defaultState.is(BlockTags.SAPLINGS) || block instanceof SaplingBlock) {
            colorCalculator = "@foliage";
        } else if (path.contains("lily_pad") || block instanceof WaterlilyBlock) {
            colorCalculator = "@foliage";
        }
        // 2. Grass / Paths / Ferns / Stems
        else if (path.contains("dirt_path") || path.contains("path") || block instanceof DirtPathBlock) {
            colorCalculator = "@grass";
        } else if ((defaultState.is(BlockTags.DIRT) && path.contains("grass")) || block instanceof GrassBlock) {
            colorCalculator = "@grass";
        } else if (path.contains("grass") || path.contains("fern") || path.contains("sugar_cane") || block instanceof SugarCaneBlock) {
            colorCalculator = "@grass";
        }
        // 3. Water / Liquids
        else if (block instanceof LiquidBlock && path.contains("water")) {
            colorCalculator = "@water";
        }
        // 4. Fallback for any other custom block whose model has tintindex >= 0
        else if (hasTintedQuads) {
            if (path.contains("leaf") || path.contains("plant") || path.contains("bush") || path.contains("flower") || path.contains("crop") || path.contains("branch")) {
                colorCalculator = "@foliage";
            } else {
                colorCalculator = "@grass";
            }
        }

        if (colorCalculator != null) {
            JsonObject map = namespaceColorMaps.computeIfAbsent(namespace, k -> new JsonObject());
            map.addProperty(id.toString(), colorCalculator);
        }
    }

    public Map<String, JsonObject> getNamespaceColorMaps() {
        return namespaceColorMaps;
    }
}
