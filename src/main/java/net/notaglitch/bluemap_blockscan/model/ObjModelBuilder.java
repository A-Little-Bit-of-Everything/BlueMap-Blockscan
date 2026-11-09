package net.notaglitch.bluemap_blockscan.model;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Locale;

public class ObjModelBuilder {

    public static String buildObj(String modelName, List<ExtractedQuad> quads) {
        StringBuilder sb = new StringBuilder();
        sb.append("# BlueMap Blockscan Wavefront OBJ Export\n");
        sb.append("o ").append(modelName).append("\n\n");

        int vertexOffset = 1;

        for (ExtractedQuad quad : quads) {
            // Write 4 vertices
            for (int i = 0; i < 4; i++) {
                sb.append(String.format(Locale.ROOT, "v %.4f %.4f %.4f\n", quad.x[i], quad.y[i], quad.z[i]));
            }

            // Write 4 texture coordinates (UV in 0..1 scale)
            for (int i = 0; i < 4; i++) {
                // OBJ UV v is flipped compared to Minecraft texture coordinate
                float u = quad.u[i] / 16.0f;
                float v = 1.0f - (quad.v[i] / 16.0f);
                sb.append(String.format(Locale.ROOT, "vt %.4f %.4f\n", u, v));
            }

            // Write quad face
            sb.append(String.format(Locale.ROOT, "f %d/%d %d/%d %d/%d %d/%d\n\n",
                    vertexOffset, vertexOffset,
                    vertexOffset + 1, vertexOffset + 1,
                    vertexOffset + 2, vertexOffset + 2,
                    vertexOffset + 3, vertexOffset + 3));

            vertexOffset += 4;
        }

        return sb.toString();
    }
}
