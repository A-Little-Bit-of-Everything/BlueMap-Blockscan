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

    /**
     * Determines whether a block already has a static, standard vanilla JSON model and blockstate
     * available in the client's resource manager (e.g. from its mod JAR).
     *
     * If true, BlueMap can read this model directly from the mod JAR with full fidelity
     * (including Blockbench rotations, proper UVs, and native culling).
     * Extracting/exporting a flattened synthetic box approximation would only degrade it.
     */
    public static boolean hasStaticModelAvailable(net.minecraft.world.level.block.Block block) {
        net.minecraft.resources.ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block);
        if (id == null) return false;

        String namespace = id.getNamespace();
        String path = id.getPath();

        // 1. Check if a static blockstate JSON exists in the loaded resource packs / mod JARs
        net.minecraft.server.packs.resources.ResourceManager rm = Minecraft.getInstance().getResourceManager();
        net.minecraft.resources.ResourceLocation bsLoc = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(namespace, "blockstates/" + path + ".json");
        java.util.Optional<net.minecraft.server.packs.resources.Resource> bsRes = rm.getResource(bsLoc);
        if (bsRes.isEmpty()) {
            return false;
        }

        // 2. Check the runtime BakedModel for the block's default state
        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
        BakedModel model = dispatcher.getBlockModel(block.defaultBlockState());
        if (model == null) {
            return false;
        }

        // Unwrap any BakedModelWrapper instances
        BakedModel unwrapped = model;
        while (unwrapped instanceof net.neoforged.neoforge.client.model.BakedModelWrapper<?> wrapper) {
            try {
                java.lang.reflect.Field f = net.neoforged.neoforge.client.model.BakedModelWrapper.class.getDeclaredField("originalModel");
                f.setAccessible(true);
                BakedModel next = (BakedModel) f.get(wrapper);
                if (next == null || next == unwrapped) break;
                unwrapped = next;
            } catch (Throwable t) {
                break;
            }
        }

        // If the unwrapped model is a standard vanilla SimpleBakedModel or WeightedBakedModel,
        // it was parsed from standard Minecraft JSON elements.
        // Custom loaders (e.g. OBJ loader, dynamic models, Copycats) are NOT SimpleBakedModel.
        return unwrapped instanceof net.minecraft.client.resources.model.SimpleBakedModel
                || unwrapped instanceof net.minecraft.client.resources.model.WeightedBakedModel;
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
                        } else if (name.equalsIgnoreCase("MATERIALS_PROPERTY") || name.equalsIgnoreCase("MATERIALS")) {
                            field.setAccessible(true);
                            @SuppressWarnings("unchecked")
                            ModelProperty<java.util.Map<String, BlockState>> prop = (ModelProperty<java.util.Map<String, BlockState>>) field.get(null);
                            if (prop != null) {
                                java.util.Map<String, BlockState> dummy = new java.util.HashMap<>();
                                for (Direction dir : Direction.values()) {
                                    dummy.put(dir.getName(), Blocks.OAK_PLANKS.defaultBlockState());
                                }
                                builder.with(prop, dummy);
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
