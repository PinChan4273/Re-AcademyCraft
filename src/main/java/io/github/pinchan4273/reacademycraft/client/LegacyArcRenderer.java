package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.client.render.AcademyPipelines;
import io.github.pinchan4273.reacademycraft.client.render.WorldDraw;
import io.github.pinchan4273.reacademycraft.visual.LegacyArcMesh;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;

/**
 * WeAthFolDによる原作のテクスチャ付きの弧の四角（7b1401c）を、26.2で提出するためのadapter。
 * 色だけを書き、深度は書かない（囲みの描画の前提）。弧の配置とアニメーションは呼び出し側が行う
 * （ClientChargingSurround・ClientChargingArc・ClientMagneticBlockArcsなど）。
 * 26.2: 状態の保存と復元は要らない（RenderPipeline が不変）。シェーダーパックの間の描き方（1.21.1 の ARC_PACK に当たる物）は
 * {@code ShaderPackDraw} が、ここで覚えさせた RenderType から作る（vanilla のパイプラインの写し）。
 */
public final class LegacyArcRenderer {
    public static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(
            "academy", "textures/effects/arc/line_segment.png");
    private static final RenderType ARC = io.github.pinchan4273.reacademycraft.client.render.ShaderPackDraw.register(RenderType.create("academy_legacy_arc",
            RenderSetup.builder(AcademyPipelines.TEX_COLOR_CUT).withTexture("Sampler0", TEXTURE).createRenderSetup()), TEXTURE);

    private LegacyArcRenderer() { }

    /** 渡されたposeを通してmodel空間の形を提出する。プレイヤーやワールドの状態は読まない。 */
    public static void submit(WorldDraw draw, PoseStack pose, LegacyArcMesh.Mesh mesh, float opacity) {
        if (!Float.isFinite(opacity) || opacity < 0 || opacity > 1
                || mesh.quads().size() > LegacyArcMesh.MAX_VERTICES / 4)
            throw new IllegalArgumentException("Invalid arc draw bounds/opacity");
        if (opacity == 0 || mesh.quads().isEmpty()) return;
        draw.geometry(pose, ARC, (last, buffer) -> {
            Matrix4f matrix = last.pose();
            for (var quad : mesh.quads()) {
                float alpha = quad.alpha() * opacity;
                vertex(buffer, matrix, quad.first(), alpha); vertex(buffer, matrix, quad.second(), alpha);
                vertex(buffer, matrix, quad.third(), alpha); vertex(buffer, matrix, quad.fourth(), alpha);
            }
        });
    }

    private static void vertex(VertexConsumer buffer, Matrix4f matrix, LegacyArcMesh.Vertex vertex, float alpha) {
        var p = vertex.position();
        buffer.addVertex(matrix, (float) p.x, (float) p.y, (float) p.z)
                .setColor(1f, 1f, 1f, alpha).setUv(vertex.u(), vertex.v());
    }
}
