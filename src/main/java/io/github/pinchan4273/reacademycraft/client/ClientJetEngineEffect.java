package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.network.AimState;
import io.github.pinchan4273.reacademycraft.network.SkillVisual;
import io.github.pinchan4273.reacademycraft.skill.JetEngine;
import io.github.pinchan4273.reacademycraft.visual.LegacyRippleAnimation;
import io.github.pinchan4273.reacademycraft.world.AcademyParticles;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 原作JEContextC: Jet Engineの2つの段階。
 *
 * 狙い: 術者自身のclientだけが、視線の先12ブロック以内のブロックに、緑(51, 255, 51)のEntityRippleMarkを置き、
 * 毎tickそこへ動かし、キーを離すと消す。原作の描画は、目印の色自身のalphaを各輪のalphaで上書きするので、
 * 原作が設定する179は画面に出ない。この輪も、輪のalphaだけで描く。
 *
 * 飛行: 術者の近くのすべてのclientが、EntityDiamondShieldを出す。単位の菱形の1.5ブロック先の1点で4枚の三角形が
 * 合わさる形で、diamond_shield.pngを光の影響なしで描く（原作は壁越しに描いたが、この移植は深度テストをかける: VIS-JET-DEPTH-001）。術者の1ブロック前、1.1上に置き、頭のyawと
 * ピッチへ向ける。さらに毎tick、術者から3分の1ブロック以内にmdの粒子を11個出す。どちらも飛行が終わると消える。
 *
 * 原作は、その11個の粒子ごとに飛行の経路上の点を計算するが、使っていない。また飛行中は術者の歩く速さを
 * 0.07へ下げる。前者は何もしないので、後者はこの移植では飛行をサーバーが扱うので、どちらも移植していない。
 */
@Mod.EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class ClientJetEngineEffect {
    static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("academy", "textures/effects/diamond_shield.png");
    /** 原作のmark.color.set(51, 255, 51, 179)と、そのalphaを輪のalphaで置き換えるRippleMarkRender。 */
    static final int MARK_RED = 51, MARK_GREEN = 255, MARK_BLUE = 51, MARK_ALPHA = 255;
    static final float SCALE = 1.5f;
    /** 原作のmesh: 術者の面にある単位の菱形と、その1先で合わさる点、およびUV。 */
    private static final float[][] VERTICES = {{-1, 0, 0}, {0, -1, 0}, {1, 0, 0}, {0, 1, 0}, {0, 0, 1}};
    private static final float[][] UVS = {{0, 0}, {1, 1}, {0, 0}, {1, 1}, {0, 1}};
    private static final int[] TRIANGLES = {0, 1, 4, 1, 2, 4, 2, 3, 4, 3, 0, 4};

    private static final class Flight {
        final int entityId; @Nullable Vec3 position, previous; float yaw, pitch, previousYaw, previousPitch;
        Flight(int entityId) { this.entityId = entityId; }
    }
    private static final Map<Long, Flight> FLIGHTS = new LinkedHashMap<>();
    /** 術者自身の狙い: 原作は作った時点から1つの目印を保ち、その経過時間で輪を動かす。 */
    private static final Map<Long, Double> MARKS = new LinkedHashMap<>();
    private static final RandomSource RANDOM = RandomSource.create();
    @Nullable private static ClientLevel world;
    private static long drawnTriangles, marksDrawn, particles;
    private ClientJetEngineEffect() { }

    public static void receive(AimState state) {
        if (!JetEngine.ID.equals(state.skill())) return;
        var level = Minecraft.getInstance().level;
        if (level != world) { clear(); world = level; }
        if (state.alive()) MARKS.put(state.token(), Util.getMillis() / 1000.0);
        else MARKS.remove(state.token());
    }

    public static void receive(SkillVisual packet) {
        var level = Minecraft.getInstance().level;
        if (level != world) { clear(); world = level; }
        if (level == null || !level.dimension().location().equals(packet.dimension())) return;
        if (!packet.starts()) { FLIGHTS.remove(packet.token()); return; }
        if (!JetEngine.ID.equals(packet.kind())) return;
        var flight = new Flight(packet.entityId());
        var entity = level.getEntity(packet.entityId());
        if (entity != null) place(flight, entity);
        FLIGHTS.put(packet.token(), flight);
    }

    /** 原作updatePos: 術者の足元に視線の方向を足し、1.1上げる。頭のyawとピッチを使う。 */
    static void place(Flight flight, Entity entity) {
        flight.previous = flight.position; flight.previousYaw = flight.yaw; flight.previousPitch = flight.pitch;
        flight.position = entity.position().add(0, 1.1, 0).add(entity.getLookAngle());
        flight.yaw = entity.getYHeadRot(); flight.pitch = entity.getXRot();
        if (flight.previous == null) { flight.previous = flight.position; flight.previousYaw = flight.yaw; flight.previousPitch = flight.pitch; }
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var client = Minecraft.getInstance(); var level = client.level;
        if (level != world) { clear(); world = level; return; }
        if (level == null || client.isPaused()) return;
        FLIGHTS.values().removeIf(flight -> {
            var entity = level.getEntity(flight.entityId);
            if (entity == null || entity.isRemoved()) return true;
            place(flight, entity);
            // 原作c_tUpdateEffect: 飛行中は毎tick、術者の周りにmdの粒子を11個出す。
            for (int i = 0; i <= 10; i++) {
                level.addParticle(AcademyParticles.MD.get(),
                        entity.getX() + range(-.3, .3), entity.getY() + range(-.3, .3), entity.getZ() + range(-.3, .3),
                        range(-.02, .02), range(-.02, .02), range(-.02, .02));
                particles++;
            }
            return false;
        });
    }
    static double range(double min, double max) { return min + RANDOM.nextDouble() * (max - min); }

    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        // VIS-JET-DEPTH-001（第2版）: 狙いの目印は粒子の後、菱形はエンティティの直後（術者の体の深度が確定し、半透明のブロックより前）に描く。
        // 粒子の後に深度テストをして描くと、手前の色付きガラスや水が先に深度を書いていて、その奥の菱形がすべて落ちた（自動撮影で確認）。
        // エンティティの直後なら、手前のガラスや水は後から菱形の上へ色を重ねる（バニラの半透明なエンティティと同じ順）。
        boolean marksStage = event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES;
        boolean shieldStage = event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES;
        if (!marksStage && !shieldStage) return;
        var client = Minecraft.getInstance(); var level = client.level;
        if (level == null || (MARKS.isEmpty() && FLIGHTS.isEmpty())) return;
        var camera = event.getCamera().getPosition(); var pose = event.getPoseStack();
        float partial = event.getPartialTick();
        // 目印は術者自身の狙いに付いて回るので、送らずにここで計算する。
        if (marksStage && !MARKS.isEmpty() && client.player != null) {
            var at = JetEngine.destination(client.player).subtract(camera);
            double now = Util.getMillis() / 1000.0;
            for (double since : MARKS.values()) {
                pose.pushPose();
                try {
                    pose.translate(at.x, at.y, at.z);
                    marksDrawn += LegacyRippleRenderer.draw(pose, LegacyRippleAnimation.sample(Math.max(0, now - since)),
                            MARK_RED, MARK_GREEN, MARK_BLUE, MARK_ALPHA);
                } finally { pose.popPose(); }
            }
        }
        if (!shieldStage || FLIGHTS.isEmpty()) return;
        // 原作は菱形の盾をalphaテストなしで描いた（LegacyAlphaShaders）。
        RenderSystem.setShader(LegacyAlphaShaders::worldPositionTexColor);
        RenderSystem.setShaderTexture(0, TEXTURE);
        // シェーダーパックの間はMinecraftのシェーダー（alpha 0.1未満を捨てる）で描くので、盾の淡い外周がtexelの段差で切れ、斜めの輪郭が
        // ギザギザになる（T178-01）。その間だけテクスチャを線形補間で読み、切れ目をtexelの境でなく滑らかな線にする。形・色・位置は変えない。
        // 線形補間では、三角形の継ぎ目（テクスチャの端）で反対側の端の透明なtexelが混ざり細い線が出るので、その間は端で止める。
        boolean smooth = ShaderPackCompat.inUse();
        var texture = smooth ? client.getTextureManager().getTexture(TEXTURE) : null;
        int[] before = texture == null ? null : smoothen(texture);
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); RenderSystem.disableCull();
        // VIS-JET-DEPTH-001: 原作は盾を深度テストなしで描いたが、この移植では術者の前方だけの菱形なので、術者の体や不透明な地形の
        // 後ろにある部分は隠す（深度テストなしでは、背後の視点から体越しに見え、全身の盾のように見えた）。深度は比べるだけで書かない:
        // 半透明の盾が深度バッファを書くと、後に描く半透明の演出や粒子が盾の後ろで消える。比較の関数はMinecraftの標準のLEQUAL。
        RenderSystem.enableDepthTest(); RenderSystem.depthFunc(org.lwjgl.opengl.GL11.GL_LEQUAL); RenderSystem.depthMask(false);
        try {
            for (var flight : FLIGHTS.values()) {
                if (flight.position == null) continue;
                var at = flight.previous.lerp(flight.position, partial).subtract(camera);
                pose.pushPose();
                try {
                    pose.translate(at.x, at.y, at.z);
                    pose.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(partial, flight.previousYaw, flight.yaw)));
                    pose.mulPose(Axis.XP.rotationDegrees(Mth.lerp(partial, flight.previousPitch, flight.pitch)));
                    pose.scale(SCALE, SCALE, SCALE);
                    var m = pose.last().pose(); var buffer = Tesselator.getInstance().getBuilder();
                    buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_TEX_COLOR);
                    for (int index : TRIANGLES)
                        buffer.vertex(m, VERTICES[index][0], VERTICES[index][1], VERTICES[index][2])
                                .uv(UVS[index][0], UVS[index][1]).color(1f, 1f, 1f, 1f).endVertex();
                    BufferUploader.drawWithShader(buffer.end());
                    drawnTriangles += TRIANGLES.length / 3;
                } finally { pose.popPose(); }
            }
        } finally {
            // 標準の状態（深度テストあり・深度の書き込みあり・背面の省略あり）へ戻す。
            RenderSystem.depthMask(true); RenderSystem.enableCull(); RenderSystem.enableDepthTest();
            // エンティティの直後に描くので、後に続く描画のために混色も切っておく（エンティティの描画の確定の後の状態）。
            RenderSystem.disableBlend();
            if (texture != null) restore(texture, before);
        }
    }

    /** {@link #smoothen}が読み書きするGLのテクスチャの値: 縮小・拡大の補間、横・縦のwrap。 */
    private static final int[] SAMPLING = { org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER, org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER,
            org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_S, org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_T };
    /**
     * テクスチャを線形補間・端で止めて読むようにし、変える前の値を返す（{@link #restore}へ渡す）。前の値はGLから読む:
     * リソースパックの.mcmetaが指定した補間は、AbstractTextureの記録（setFilter・restoreLastBlurMipmap）を通らずにGLへ直接入るので、
     * その記録へ戻すと失われる。AbstractTextureの記録には触れない。
     */
    public static int[] smoothen(net.minecraft.client.renderer.texture.AbstractTexture texture) {
        texture.bind();
        int[] before = new int[SAMPLING.length];
        for (int i = 0; i < SAMPLING.length; i++) before[i] = org.lwjgl.opengl.GL11.glGetTexParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, SAMPLING[i]);
        int[] smooth = { org.lwjgl.opengl.GL11.GL_LINEAR, org.lwjgl.opengl.GL11.GL_LINEAR, org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE, org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE };
        for (int i = 0; i < SAMPLING.length; i++) RenderSystem.texParameter(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, SAMPLING[i], smooth[i]);
        return before;
    }
    /** {@link #smoothen}が返した値へ、補間とwrapをそのまま戻す。 */
    public static void restore(net.minecraft.client.renderer.texture.AbstractTexture texture, int[] before) {
        texture.bind();
        for (int i = 0; i < SAMPLING.length; i++) RenderSystem.texParameter(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, SAMPLING[i], before[i]);
    }
    /** テスト用: 盾のテクスチャ。 */
    public static net.minecraft.resources.ResourceLocation texture() { return TEXTURE; }

    private static void clear() { FLIGHTS.clear(); MARKS.clear(); }
    @SubscribeEvent public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) { clear(); world = null; }
    /** テスト用の入口。 */
    public static long drawnTriangles() { return drawnTriangles; }
    public static long marksDrawn() { return marksDrawn; }
    public static long particles() { return particles; }
    public static boolean marking() { return !MARKS.isEmpty(); }
    public static boolean flying(int entityId) { return FLIGHTS.values().stream().anyMatch(f -> f.entityId == entityId); }
}
