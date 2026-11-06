package io.github.pinchan4273.reacademycraft.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities;
import io.github.pinchan4273.reacademycraft.network.DarkWingAction;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * v1.5.0 §14: 未元物質人間のHUD。生命力はCPだけなので、ハート・食料・酸素のゲージを描かない。CPのゲージは{@link CpBarHud}が能力OFFでも出す。
 * 形態はサーバーの同期（{@code network/AbilitySnapshot}）で届き、通常人間へ戻れば次の同期で元の表示になる。
 * <p>
 * 外観（COMMAND-RE-150-DARK-BODY-VISUAL-R1-20261104の初稿）: 本人と見ている人のクライアントで、未元物質人間の頭上の輪（{@link DarkHaloLayer}）と、
 * 変化の瞬間の白いシルエットの状態を持つ。形態の有無と変化の合図は{@link DarkWingAction#BODY}・{@link DarkWingAction#BODY_FLASH}で届く。
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class ClientDarkBody {
    private ClientDarkBody() { }

    /** 今のプレイヤーが未元物質人間か。 */
    public static boolean active() {
        var player = Minecraft.getInstance().player;
        var data = player == null ? null : io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities.of(player).orElse(null);
        return data != null && data.isDarkBody();
    }
    /**
     * 最も高い優先度で取り消す（2026-11-03のユーザーの指摘: 他のMODがハートを自前で描くと、ハートだけ残った）。描画の合図を受けてゲージを
     * 置き換えるMODは、取り消し済みの合図では描かないことが多いので、それより先に取り消す。特定のMODは見ない（汎用の対応）。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void overlay(RenderGuiLayerEvent.Pre event) {
        // NeoForge 26.2のGUIの層（Forge版のVanillaGuiOverlayのPLAYER_HEALTH・FOOD_LEVEL・AIR_LEVEL）。
        var id = event.getName();
        if ((id.equals(VanillaGuiLayers.PLAYER_HEALTH) || id.equals(VanillaGuiLayers.FOOD_LEVEL) || id.equals(VanillaGuiLayers.AIR_LEVEL)) && active())
            event.setCanceled(true);
    }

    // ---- 頭上の輪と変化の白いシルエット ----

    /** 白いシルエットの時間（tick）: 0.25秒で白くなり、1.5秒保ち、0.75秒で戻る。 */
    static final int FLASH_RISE = 5, FLASH_HOLD = 30, FLASH_DECAY = 15;
    /** 輪の揺れの周期の速さ（ラジアン/tick）: 静止で約3秒、移動中で約1.2秒。 */
    static final float BOB_IDLE_SPEED = (float) (Math.PI * 2 / 60), BOB_MOVE_SPEED = (float) (Math.PI * 2 / 24);

    /** 1人分の外観の状態。 */
    public static final class Mark {
        boolean body;
        int flashTicks = -1;
        /** 動きの度合い（0〜1、滑らかにした値）と、揺れの位相。 */
        float moveO, move, phaseO, phase;
        /** 白さ（0〜1）。 */
        public float flash(float partial) {
            if (flashTicks < 0) return 0;
            float t = flashTicks + partial;
            if (t < FLASH_RISE) return t / FLASH_RISE;
            if (t < FLASH_RISE + FLASH_HOLD) return 1;
            return Math.max(0, 1 - (t - FLASH_RISE - FLASH_HOLD) / FLASH_DECAY);
        }
        public boolean body() { return body; }
        public float move(float partial) { return previewMove != null ? previewMove : Mth.lerp(partial, moveO, move); }
        public float phase(float partial) { return previewPhase != null ? previewPhase : Mth.lerp(partial, phaseO, phase); }
    }
    private static final Map<Integer, Mark> MARKS = new HashMap<>();
    @Nullable private static ClientLevel world;
    /** 撮影用: 揺れの位相と動きの度合いを固定する（nullで解除）。 */
    @Nullable private static Float previewPhase, previewMove;
    public static void previewBob(@Nullable Float phase, @Nullable Float move) { previewPhase = phase; previewMove = move; }

    /** サーバーからの形態と変化の合図（{@link ClientDarkWings}が受けて渡す）。 */
    public static void receive(DarkWingAction packet) {
        var level = Minecraft.getInstance().level;
        if (level != world) { MARKS.clear(); EXTERNAL.clear(); HOLDS.clear(); world = level; }
        var mark = MARKS.computeIfAbsent(packet.entityId(), id -> new Mark());
        if (packet.kind() == DarkWingAction.BODY_FLASH) { mark.body = true; mark.flashTicks = 0; }
        else mark.body = packet.value() != 0;
    }
    @Nullable public static Mark mark(int entityId) { return MARKS.get(entityId); }
    /**
     * 頭上の輪が描く状態: 自然能力の未元物質人間ならその状態、そうでなければ外部の演出（公開APIの{@code DARK_HALO}、演出API版3）の状態。
     * 自然能力の輪がある実体では、外部の輪を重ねない。
     */
    @Nullable public static Mark haloMark(int entityId) {
        var own = MARKS.get(entityId);
        return own != null && own.body ? own : EXTERNAL.get(entityId);
    }

    // ---- 外部の演出の頭上の輪（演出API版3。Forge 1.5.1 の 91f33867 の移植） ----

    /** 外部の演出の輪の状態（実体の番号ごと。揺れは自然能力と同じ{@link #advance}で進める）と、輪を出したままにしている演出の札。 */
    private static final Map<Integer, Mark> EXTERNAL = new HashMap<>();
    private static final Map<Integer, java.util.Set<Object>> HOLDS = new HashMap<>();
    /** 札で外部の輪を出す。 */
    static void holdExternal(int entityId, Object token) {
        var level = Minecraft.getInstance().level;
        if (level != world) { MARKS.clear(); EXTERNAL.clear(); HOLDS.clear(); world = level; }
        EXTERNAL.computeIfAbsent(entityId, id -> new Mark()).body = true;
        HOLDS.computeIfAbsent(entityId, id -> new java.util.HashSet<>()).add(token);
    }
    /** 札を離す。札が無くなれば、自然能力と同じくすぐ消す。 */
    static void releaseExternal(int entityId, Object token) {
        var holds = HOLDS.get(entityId);
        if (holds == null || !holds.remove(token) || !holds.isEmpty()) return;
        HOLDS.remove(entityId);
        EXTERNAL.remove(entityId);
    }
    /** 試験用の入口: 外部の輪の数。 */
    public static int externalHalos() { return EXTERNAL.size(); }

    /** 揺れを1tick進める（自然能力と外部の輪で共通）。 */
    private static void advance(Mark mark, Entity entity) {
        // 歩く・飛ぶほど大きく速く揺れる（翼を閉じて歩くときも揺れる）。
        var wings = ClientDarkWings.state(entity.getId());
        double speed = Math.hypot(entity.getX() - entity.xo, entity.getZ() - entity.zo) + Math.abs(entity.getY() - entity.yo) * .5;
        float target = wings != null && wings.flight != 0 ? 1 : (float) Mth.clamp(speed / .2, 0, 1);
        mark.moveO = mark.move;
        mark.move += (target - mark.move) * .15f;
        mark.phaseO = mark.phase;
        mark.phase += Mth.lerp(mark.move, BOB_IDLE_SPEED, BOB_MOVE_SPEED);
    }

    @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        var client = Minecraft.getInstance(); var level = client.level;
        if (level != world) { MARKS.clear(); EXTERNAL.clear(); HOLDS.clear(); world = level; return; }
        if (level == null || client.isPaused()) return;
        MARKS.entrySet().removeIf(entry -> {
            var mark = entry.getValue();
            Entity entity = level.getEntity(entry.getKey());
            if (entity == null || entity.isRemoved()) return true;
            if (mark.flashTicks >= 0 && ++mark.flashTicks > FLASH_RISE + FLASH_HOLD + FLASH_DECAY) mark.flashTicks = -1;
            advance(mark, entity);
            return !mark.body && mark.flashTicks < 0;
        });
        // 外部の輪: 実体を失った演出は自分で札を離すが、ここでも実体の無い状態は消す。
        EXTERNAL.entrySet().removeIf(entry -> {
            Entity entity = level.getEntity(entry.getKey());
            if (entity == null || entity.isRemoved()) { HOLDS.remove(entry.getKey()); return true; }
            advance(entry.getValue(), entity);
            return false;
        });
    }
    @SubscribeEvent public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) { MARKS.clear(); EXTERNAL.clear(); HOLDS.clear(); world = null; }

    /**
     * 変化の瞬間の白いシルエット: 体・装備・手の持ち物・翼を、同じ描画経路で描くとき、頂点の重ね色（オーバーレイ）を白へ、
     * 明るさを最大へ寄せる。被弾の赤い点滅（重ね色の赤の段）はそのまま残す。
     * 普段から体を淡く白く光らせる案は試したが、2026-11-04のユーザーの判断で採らない（白くするのは変化の瞬間だけ）。
     * <p>
     * 26.2: 1.21.1は描画の合図を取り消して、頂点の書き手を包んだ{@code MultiBufferSource}で描き直した。26.2の描画は提出（{@link SubmitNodeCollector}）
     * なので、{@code mixin/client/AvatarWhitenMixin}がそのプレイヤーの提出の入口で提出先を{@link WhitenCollector}へ差し替える。描くのは1回だけ
     * （元の描画を取り消して描き直す1.21.1と同じく、二重には描かない）。
     */
    public static SubmitNodeCollector whiten(int entityId, float partial, SubmitNodeCollector collector) {
        var mark = MARKS.get(entityId);
        float level = mark == null ? 0 : mark.flash(partial);
        if (level <= 0) return collector;
        return new WhitenCollector(collector, Math.round(level * 15), level);
    }

    /** 明るさ（パック済み）を最大へ寄せる。 */
    private static int packedLight(int light, float level) {
        return LightCoordsUtil.pack(Math.round(Mth.lerp(level, LightCoordsUtil.block(light), 15)), Math.round(Mth.lerp(level, LightCoordsUtil.sky(light), 15)));
    }
    /** 重ね色（パック済み）を白へ。被弾の赤（重ね色の縦の段が8未満）はそのまま。 */
    private static int packedOverlay(int overlay, int whiteU) {
        int u = overlay & 0xFFFF, v = overlay >> 16 & 0xFFFF;
        return v < 8 ? OverlayTexture.pack(u, v) : OverlayTexture.pack(whiteU, 10);
    }

    /** 頂点の重ね色を白へ、明るさを最大へ寄せる。被弾の赤（重ね色の縦の段が8未満）はそのまま。他の値はそのまま渡す。 */
    private record Whiten(VertexConsumer out, int whiteU, float level) implements VertexConsumer {
        private int light(int value) { return Math.round(Mth.lerp(level, value, 240)); }
        private int overlay(int u, int v) { return v < 8 ? OverlayTexture.pack(u, v) : OverlayTexture.pack(whiteU, 10); }
        // 頂点の書き手（Forge版のvertex・color・uv・overlayCoords・uv2・normalは、addVertex・setColor・setUv・setUv1・setUv2・setNormal）。
        @Override public VertexConsumer addVertex(float x, float y, float z) { out.addVertex(x, y, z); return this; }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { out.setColor(r, g, b, a); return this; }
        @Override public VertexConsumer setColor(int color) { out.setColor(color); return this; }
        @Override public VertexConsumer setUv(float u, float v) { out.setUv(u, v); return this; }
        @Override public VertexConsumer setUv1(int u, int v) { int o = overlay(u, v); out.setUv1(o & 0xFFFF, o >> 16 & 0xFFFF); return this; }
        @Override public VertexConsumer setUv2(int u, int v) { out.setUv2(light(u), light(v)); return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { out.setNormal(x, y, z); return this; }
        @Override public VertexConsumer setLineWidth(float width) { out.setLineWidth(width); return this; }
        @Override public void addVertex(float x, float y, float z, int color, float u, float v, int overlayValue, int lightValue, float nx, float ny, float nz) {
            out.addVertex(x, y, z, color, u, v, packedOverlay(overlayValue, whiteU), packedLight(lightValue, level), nx, ny, nz);
        }
    }

    /**
     * 1.21.1の包んだ{@code MultiBufferSource}に当たる提出先。明るさと重ね色を持つ提出（モデル・部品・持ち物・ブロック・文字）はその値を、
     * 頂点を自分で書く提出（翼・輪などの{@code submitCustomGeometry}）は頂点の書き手を{@link Whiten}で包む。他はそのまま渡す。
     */
    private static class WhitenOrdered implements net.minecraft.client.renderer.OrderedSubmitNodeCollector {
        private final net.minecraft.client.renderer.OrderedSubmitNodeCollector out;
        final int whiteU; final float level;
        WhitenOrdered(net.minecraft.client.renderer.OrderedSubmitNodeCollector out, int whiteU, float level) { this.out = out; this.whiteU = whiteU; this.level = level; }
        private int light(int value) { return packedLight(value, level); }
        private int overlay(int value) { return packedOverlay(value, whiteU); }
        @Override public void submitShadow(com.mojang.blaze3d.vertex.PoseStack pose, float radius, java.util.List<net.minecraft.client.renderer.entity.state.EntityRenderState.ShadowPiece> pieces) {
            out.submitShadow(pose, radius, pieces);
        }
        @Override public void submitNameTag(com.mojang.blaze3d.vertex.PoseStack pose, @Nullable net.minecraft.world.phys.Vec3 attachment, int offset, net.minecraft.network.chat.Component name,
                                            boolean seeThrough, int lightCoords, net.minecraft.client.renderer.state.level.CameraRenderState camera) {
            out.submitNameTag(pose, attachment, offset, name, seeThrough, light(lightCoords), camera);
        }
        @Override public void submitText(com.mojang.blaze3d.vertex.PoseStack pose, float x, float y, net.minecraft.util.FormattedCharSequence string, boolean dropShadow,
                                         net.minecraft.client.gui.Font.DisplayMode displayMode, int lightCoords, int color, int backgroundColor, int outlineColor) {
            out.submitText(pose, x, y, string, dropShadow, displayMode, light(lightCoords), color, backgroundColor, outlineColor);
        }
        @Override public void submitFlame(com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.client.renderer.entity.state.EntityRenderState state, org.joml.Quaternionf rotation) {
            out.submitFlame(pose, state, rotation);
        }
        @Override public void submitLeash(com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.client.renderer.entity.state.EntityRenderState.LeashState leash) { out.submitLeash(pose, leash); }
        @Override public <S> void submitModel(net.minecraft.client.model.Model<? super S> model, S state, com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.client.renderer.rendertype.RenderType type,
                                              int lightCoords, int overlayCoords, int tintedColor, net.minecraft.client.renderer.texture.TextureAtlasSprite sprite, int outlineColor,
                                              net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay crumbling) {
            out.submitModel(model, state, pose, type, light(lightCoords), overlay(overlayCoords), tintedColor, sprite, outlineColor, crumbling);
        }
        @Override public void submitMovingBlock(com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.client.renderer.block.MovingBlockRenderState block, int outlineColor) {
            out.submitMovingBlock(pose, block, outlineColor);
        }
        @Override public void submitBlockModel(com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.client.renderer.rendertype.RenderType type,
                                               java.util.List<net.minecraft.client.renderer.block.dispatch.BlockStateModelPart> parts, int[] tints, int lightCoords, int overlayCoords, int outlineColor) {
            out.submitBlockModel(pose, type, parts, tints, light(lightCoords), overlay(overlayCoords), outlineColor);
        }
        @Override public void submitBreakingBlockModel(com.mojang.blaze3d.vertex.PoseStack pose, java.util.List<net.minecraft.client.renderer.block.dispatch.BlockStateModelPart> parts, int progress) {
            out.submitBreakingBlockModel(pose, parts, progress);
        }
        @Override public void submitShapeOutline(com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.world.phys.shapes.VoxelShape shape, net.minecraft.client.renderer.rendertype.RenderType type,
                                                 int color, float width, boolean afterTerrain) {
            out.submitShapeOutline(pose, shape, type, color, width, afterTerrain);
        }
        @Override public void submitItem(com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.world.item.ItemDisplayContext context, int lightCoords, int overlayCoords, int outlineColor,
                                         int[] tints, java.util.List<net.minecraft.client.resources.model.geometry.BakedQuad> quads, net.minecraft.client.renderer.item.ItemStackRenderState.FoilType foil) {
            out.submitItem(pose, context, light(lightCoords), overlay(overlayCoords), outlineColor, tints, quads, foil);
        }
        @Override public void submitCustomGeometry(com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.client.renderer.rendertype.RenderType type, SubmitNodeCollector.CustomGeometryRenderer geometry) {
            int u = whiteU; float l = level;
            out.submitCustomGeometry(pose, type, (last, buffer) -> geometry.render(last, new Whiten(buffer, u, l)));
        }
        @Override public void submitQuadParticleGroup(net.minecraft.client.renderer.state.level.QuadParticleRenderState particles) { out.submitQuadParticleGroup(particles); }
        @Override public void submitGizmoPrimitives(net.minecraft.client.renderer.gizmos.DrawableGizmoPrimitives.Group group, net.minecraft.client.renderer.state.level.CameraRenderState camera, boolean onTop) {
            out.submitGizmoPrimitives(group, camera, onTop);
        }
        @Override public <T extends net.minecraft.client.renderer.feature.submit.SubmitNode, S extends T> void submitSpecial(net.neoforged.neoforge.client.submit.RenderPhaseKey<T> phaseKey, S node) {
            out.submitSpecial(phaseKey, node);
        }
    }
    private static final class WhitenCollector extends WhitenOrdered implements SubmitNodeCollector {
        private final SubmitNodeCollector root;
        WhitenCollector(SubmitNodeCollector root, int whiteU, float level) { super(root, whiteU, level); this.root = root; }
        @Override public net.minecraft.client.renderer.OrderedSubmitNodeCollector order(int order) { return new WhitenOrdered(root.order(order), whiteU, level); }
    }
}
