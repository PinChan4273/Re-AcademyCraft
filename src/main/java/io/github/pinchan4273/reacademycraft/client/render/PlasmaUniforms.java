package io.github.pinchan4273.reacademycraft.client.render;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import java.nio.ByteBuffer;
import net.minecraft.client.renderer.DynamicUniformStorage;
import org.jspecify.annotations.Nullable;

/** {@code plasma_body.fsh} の {@code PlasmaBody} の block（1.21.1 の {@code glUniform4fv} と JSON の uniform に当たる）。 */
public final class PlasmaUniforms {
    public static final int MAX_BALLS = 16;
    /** vec4 × 16、int、float を std140 で並べて、16 の倍数へ丸めた大きさ。 */
    private static final int SIZE = 16 * MAX_BALLS + 16;

    private static @Nullable DynamicUniformStorage<Block> storage;

    private PlasmaUniforms() { }

    /** balls は球ごとの x, y, z, 大きさ（カメラ空間、z は反転）。長さは {@code MAX_BALLS * 4}。 */
    private record Block(float[] balls, int count, float alpha) implements DynamicUniformStorage.DynamicUniform {
        @Override public void write(ByteBuffer buffer) {
            var std140 = Std140Builder.intoBuffer(buffer);
            for (int i = 0; i < MAX_BALLS; i++) std140.putVec4(balls[i * 4], balls[i * 4 + 1], balls[i * 4 + 2], balls[i * 4 + 3]);
            std140.putInt(count).putFloat(alpha);
        }
    }

    public static GpuBufferSlice write(float[] balls, int count, float alpha) {
        if (storage == null) storage = new DynamicUniformStorage<>("Academy plasma body", SIZE, 4);
        return storage.writeUniform(new Block(balls, count, alpha));
    }

    static void endFrame() { if (storage != null) storage.endFrame(); }

    static void close() {
        if (storage != null) storage.close();
        storage = null;
    }
}
