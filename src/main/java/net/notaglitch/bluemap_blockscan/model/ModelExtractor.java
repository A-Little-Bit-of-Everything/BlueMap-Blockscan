package net.notaglitch.bluemap_blockscan.model;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.data.ModelData;

import java.util.ArrayList;
import java.util.List;

public class ModelExtractor {

    private static final RandomSource RANDOM = RandomSource.create(42L);

    public static List<ExtractedQuad> extractQuadsForState(BlockState state) {
        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
        BakedModel model = dispatcher.getBlockModel(state);
        return extractQuadsFromModel(state, model);
    }

    public static List<ExtractedQuad> extractQuadsFromModel(BlockState state, BakedModel model) {
        List<ExtractedQuad> results = new ArrayList<>();
        if (model == null) {
            return results;
        }

        ChunkRenderTypeSet renderTypes = model.getRenderTypes(state, RANDOM, ModelData.EMPTY);

        if (renderTypes == null || renderTypes.isEmpty()) {
            extractQuadsForRenderType(state, model, null, results);
        } else {
            for (RenderType renderType : renderTypes) {
                extractQuadsForRenderType(state, model, renderType, results);
            }
        }

        return results;
    }

    private static void extractQuadsForRenderType(BlockState state, BakedModel model, RenderType renderType, List<ExtractedQuad> out) {
        // Query unculled quads (direction = null)
        try {
            List<BakedQuad> nullQuads = model.getQuads(state, null, RANDOM, ModelData.EMPTY, renderType);
            if (nullQuads != null) {
                for (BakedQuad q : nullQuads) {
                    out.add(new ExtractedQuad(q));
                }
            }
        } catch (Exception ignored) {}

        // Query quads for each cardinal direction
        for (Direction dir : Direction.values()) {
            try {
                List<BakedQuad> dirQuads = model.getQuads(state, dir, RANDOM, ModelData.EMPTY, renderType);
                if (dirQuads != null) {
                    for (BakedQuad q : dirQuads) {
                        out.add(new ExtractedQuad(q));
                    }
                }
            } catch (Exception ignored) {}
        }
    }
}
