package net.notaglitch.bluemap_blockscan.properties;

import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.common.Tags;

import java.util.HashMap;
import java.util.Map;

public class BlockPropertiesExtractor {

    private final Map<String, JsonObject> namespacePropertyMaps = new HashMap<>();

    public void processBlock(Block block) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        String namespace = id.getNamespace();
        BlockState defaultState = block.defaultBlockState();

        JsonObject blockProps = new JsonObject();

        boolean hasWaterlogged = defaultState.hasProperty(BlockStateProperties.WATERLOGGED);
        boolean randomOffset = defaultState.hasOffsetFunction();

        boolean isLeaves = defaultState.is(BlockTags.LEAVES) || block instanceof LeavesBlock;
        boolean isGlass = block instanceof TransparentBlock || block instanceof StainedGlassBlock || defaultState.is(Tags.Blocks.GLASS_BLOCKS);

        if (isGlass) {
            blockProps.addProperty("occluding", false);
            blockProps.addProperty("cullingIdentical", true);
        } else if (isLeaves) {
            blockProps.addProperty("cullingIdentical", true);
        }

        if (hasWaterlogged && defaultState.getValue(BlockStateProperties.WATERLOGGED)) {
            blockProps.addProperty("alwaysWaterlogged", true);
        }

        if (randomOffset) {
            blockProps.addProperty("randomOffset", true);
        }

        if (!blockProps.isEmpty()) {
            JsonObject map = namespacePropertyMaps.computeIfAbsent(namespace, k -> new JsonObject());
            map.add(id.toString(), blockProps);
        }
    }

    public Map<String, JsonObject> getNamespacePropertyMaps() {
        return namespacePropertyMaps;
    }
}
