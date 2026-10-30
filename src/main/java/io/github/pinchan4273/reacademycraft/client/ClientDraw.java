package io.github.pinchan4273.reacademycraft.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.MeshData;

/** 1.21の描画の小さな補助。 */
public final class ClientDraw {
    private ClientDraw() { }

    /**
     * 1.20.1の{@code BufferUploader.drawWithShader(buffer.end())}。1.20.1は頂点の無いバッファを描かずに捨てた。
     * 1.21のbuild()は頂点が無ければnullを返す（buildOrThrowは例外）ので、同じく何も描かない。
     */
    public static void drawWithShader(BufferBuilder buffer) {
        MeshData mesh = buffer.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);
    }
}
