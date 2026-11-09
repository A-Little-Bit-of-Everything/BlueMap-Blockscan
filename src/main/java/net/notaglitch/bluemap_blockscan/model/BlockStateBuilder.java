package net.notaglitch.bluemap_blockscan.model;

import com.google.gson.JsonObject;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.Map;
import java.util.stream.Collectors;

public class BlockStateBuilder {

    private final JsonObject variants = new JsonObject();

    public static String serializeStateProperties(BlockState state) {
        if (state.getValues().isEmpty()) {
            return "";
        }
        return state.getValues().entrySet().stream()
                .sorted(java.util.Comparator.comparing(entry -> entry.getKey().getName()))
                .map(entry -> entry.getKey().getName() + "=" + getPropertyName(entry.getKey(), entry.getValue()))
                .collect(Collectors.joining(","));
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> String getPropertyName(Property<T> property, Comparable<?> value) {
        return property.getName((T) value);
    }

    public void addVariant(String stateVariantString, String modelResourceLocation) {
        JsonObject modelObj = new JsonObject();
        modelObj.addProperty("model", modelResourceLocation);
        variants.add(stateVariantString, modelObj);
    }

    public JsonObject buildBlockStateJson() {
        JsonObject root = new JsonObject();
        root.add("variants", variants);
        return root;
    }
}
