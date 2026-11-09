package net.notaglitch.bluemap_blockscan.model;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
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
        int initialSize = out.size();
        queryQuadsWithModelData(state, model, renderType, ModelData.EMPTY, out);

        // If no quads were extracted (e.g. Copycats and dynamic models requiring ModelData material),
        // retry with a synthesized fallback ModelData so geometry is preserved in the export.
        if (out.size() == initialSize) {
            ModelData fallback = createFallbackModelData(model);
            if (fallback != ModelData.EMPTY) {
                queryQuadsWithModelData(state, model, renderType, fallback, out);
            }
        }
    }

    private static void queryQuadsWithModelData(BlockState state, BakedModel model, RenderType renderType, ModelData modelData, List<ExtractedQuad> out) {
        // Query unculled quads (direction = null). These must NEVER have a cullface!
        try {
            List<BakedQuad> nullQuads = model.getQuads(state, null, RANDOM, modelData, renderType);
            if (nullQuads != null) {
                for (BakedQuad q : nullQuads) {
                    out.add(new ExtractedQuad(q, null));
                }
            }
        } catch (Exception ignored) {}

        // Query quads for each cardinal direction (cullDirection = dir)
        for (Direction dir : Direction.values()) {
            try {
                List<BakedQuad> dirQuads = model.getQuads(state, dir, RANDOM, modelData, renderType);
                if (dirQuads != null) {
                    for (BakedQuad q : dirQuads) {
                        out.add(new ExtractedQuad(q, dir));
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    /**
     * Inspects dynamic wrapper models (such as Create's Copycat or Copycats+) for static MATERIAL_PROPERTY
     * fields and binds a default blockstate (e.g. Oak Planks) so that their 3D shape can be extracted.
     */
    private static ModelData createFallbackModelData(BakedModel model) {
        try {
            ModelData.Builder builder = ModelData.builder();
            boolean found = false;
            Class<?> clazz = model.getClass();
            while (clazz != null && clazz != Object.class) {
                for (Field field : clazz.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers()) && ModelProperty.class.isAssignableFrom(field.getType())) {
                        String name = field.getName();
                        if (name.equalsIgnoreCase("MATERIAL_PROPERTY") || name.equalsIgnoreCase("MATERIAL")) {
                            field.setAccessible(true);
                            @SuppressWarnings("unchecked")
                            ModelProperty<BlockState> prop = (ModelProperty<BlockState>) field.get(null);
                            if (prop != null) {
                                builder.with(prop, Blocks.OAK_PLANKS.defaultBlockState());
                                found = true;
                            }
                        }
                    }
                }
                clazz = clazz.getSuperclass();
            }
            return found ? builder.build() : ModelData.EMPTY;
        } catch (Throwable t) {
            return ModelData.EMPTY;
        }
    }
}
