package io.github.pinchan4273.reacademycraft.client;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.model.geom.ModelPart;

/**
 * 甲虫の面の表（dark_matter_beetle.facets）。骨ごとに多角形の面を持ち、描画は骨の変換に沿って行う。
 * 1面18個: 頂点4つのx,y,z、法線、色。art-src/dark_matter/generate_beetle_facets.py が作る。
 */
final class DarkBeetleFacets {
    private static final String RESOURCE = "/assets/academy/models/entity/dark_matter_beetle.facets";
    static final int STRIDE = 18;

    /** 1本の骨の面。pathは根からの骨の名前。 */
    record Part(String[] path, float[] faces) { }
    private static List<Part> parts;

    private DarkBeetleFacets() { }

    static synchronized List<Part> parts() {
        if (parts != null) return parts;
        var loaded = new ArrayList<Part>();
        try (var raw = DarkBeetleFacets.class.getResourceAsStream(RESOURCE)) {
            if (raw == null) throw new IllegalStateException("Missing " + RESOURCE);
            var in = new DataInputStream(new BufferedInputStream(raw));
            int count = in.readInt();
            for (int i = 0; i < count; i++) {
                String path = in.readUTF();
                float[] faces = new float[in.readInt() * STRIDE];
                for (int f = 0; f < faces.length; f++) faces[f] = in.readFloat();
                loaded.add(new Part(path.split("/"), faces));
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + RESOURCE, e);
        }
        parts = List.copyOf(loaded);
        return parts;
    }

    /** 骨の鎖を解決する（先頭はモデルのroot）。 */
    static ModelPart[] chain(ModelPart root, String[] path) {
        var out = new ModelPart[path.length];
        out[0] = root;
        for (int i = 1; i < path.length; i++) out[i] = out[i - 1].getChild(path[i]);
        return out;
    }
}
