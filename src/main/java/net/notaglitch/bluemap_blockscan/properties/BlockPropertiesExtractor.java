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

        boolean occluding = defaultState.canOcclude();
        boolean solid = defaultState.isSolid();
        boolean hasWaterlogged = defaultState.hasProperty(BlockStateProperties.WATERLOGGED);
        boolean randomOffset = defaultState.hasOffsetFunction();

        boolean isLeaves = defaultState.is(BlockTags.LEAVES) || block instanceof LeavesBlock;
        boolean isGlass = block instanceof TransparentBlock || block instanceof StainedGlassBlock || defaultState.is(Tags.Blocks.GLASS_BLOCKS);

        blockProps.addProperty("occluding", occluding);
        blockProps.addProperty("culling", solid);

        if (isGlass || isLeaves) {
            blockProps.addProperty("cullingIdentical", true);
        }

        if (hasWaterlogged && defaultState.getValue(BlockStateProperties.WATERLOGGED)) {
            blockProps.addProperty("alwaysWaterlogged", true);
        }

        if (randomOffset) {
            blockProps.addProperty("randomOffset", true);
        }

        // Note: BlueMap's BlockPropertiesConfig parser only accepts culling, occluding,
        // alwaysWaterlogged, randomOffset, and cullingIdentical. Any unknown key like "light"
        // causes an IllegalStateException and discards the entire namespace configuration.

        JsonObject map = namespacePropertyMaps.computeIfAbsent(namespace, k -> new JsonObject());
        map.add(id.toString(), blockProps);
    }

    public Map<String, JsonObject> getNamespacePropertyMaps() {
        return namespacePropertyMaps;
    }
}
