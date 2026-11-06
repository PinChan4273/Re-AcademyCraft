package io.github.pinchan4273.reacademycraft.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import io.github.pinchan4273.reacademycraft.world.entity.DarkMatterBeetle;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * 甲虫の出現と回収の演出（v1.5.1。Forgeの試作 17561eb5・c0b3c070 の移植）。輪は天界発現の予兆と同じ水平の輪（{@link ClientHeavenEffects#ring}、
 * 加算の半透明で地形の深度を見る）を、甲虫（判定の幅2.0、角を含む長さ約3ブロック）より大きい半径{@link #RADIUS}で描く。
 * <ul>
 * <li>出現: 実体の同期データ{@link DarkMatterBeetle#emergeLeft()}の間、体は描かず（{@link DarkBeetleRenderer}）、地面の輪が明るくなりながら、
 * 3本の輪が上から降りて縮む。現れ終わった瞬間に、地面の輪が外へ広がって消え、1本の輪が体を下から上へなでる。体は0.6倍から1倍へ広がる。</li>
 * <li>回収: 実体のイベント{@link DarkMatterBeetle#EVENT_RECALL}を受けたら、消えた実体を写しとして残し、体を縮めながら、出現の逆に輪が上がって広がり消える。</li>
 * <li>長押し: 自分の甲虫生成のキーを長押ししている間、自分の甲虫の足元に、回収の近さに合わせて明るく縮む輪を描く（本人だけ）。</li>
 * </ul>
 * どれも追加の通信を使わない（同期データ、バニラの実体のイベント、本人のキーの状態）。
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class ClientDarkBeetleSummon {
    /** 輪の半径（ブロック）。甲虫の体（半分の長さ約1.5）を外から囲む。 */
    public static final float RADIUS = 2.6f;
    /** 輪の高さの基準（甲虫の判定の高さ）。 */
    private static final float HEIGHT = 1.75f;
    /** 現れた後の光（tick）、回収の演出の長さと、体が縮み切るまで（tick）。 */
    public static final int APPEAR_FLASH = 8, RECALL_TICKS = 14, SHRINK_TICKS = 6;
    private static final float R = .93f, G = .96f, B = 1f;

    private static final class Ghost {
        final DarkMatterBeetle beetle; final Vec3 at; final float yaw; final boolean hidden; int age;
        Ghost(DarkMatterBeetle beetle) { this.beetle = beetle; at = beetle.position(); yaw = beetle.getYRot(); hidden = beetle.emerging(); }
    }
    private static final List<Ghost> GHOSTS = new ArrayList<>();
    private static Object world;

    private ClientDarkBeetleSummon() { }

    /** 回収の通知（この直後に実体はクライアントから消える）。 */
    public static void recalled(DarkMatterBeetle beetle) { GHOSTS.add(new Ghost(beetle)); }
    /** 試験用: 描いている回収の写しの数。 */
    public static int ghosts() { return GHOSTS.size(); }

    // Forge版のTickEvent.ClientTickEvent（END）は、NeoForgeではClientTickEvent.Post。
    @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.level != world) { GHOSTS.clear(); world = minecraft.level; }
        if (minecraft.isPaused()) return;
        GHOSTS.removeIf(ghost -> ++ghost.age >= RECALL_TICKS);
    }
    @SubscribeEvent public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) { GHOSTS.clear(); }

    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        // 1.21では部分tickはDeltaTrackerから読む（Forge版のevent.getPartialTick()）。
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        var camera = event.getCamera().getPosition();
        float hold = AbilityControls.beetleHold();
        List<DarkMatterBeetle> beetles = new ArrayList<>();
        for (var entity : minecraft.level.entitiesForRendering())
            if (entity instanceof DarkMatterBeetle beetle && (beetle.emerging() || (beetle.appearAge() >= 0 && beetle.appearAge() < APPEAR_FLASH)
                    || (hold > 0 && ClientDarkBeetles.isLocalPlayer(beetle.ownerId())))) beetles.add(beetle);
        if (beetles.isEmpty() && GHOSTS.isEmpty()) return;
        var pose = event.getPoseStack();
        // 回収の写しの体（縮みながら消える）。普通の実体と同じ描き方で、倍率だけ変える。
        if (!GHOSTS.isEmpty()) {
            var dispatcher = minecraft.getEntityRenderDispatcher();
            var buffers = minecraft.renderBuffers().bufferSource();
            for (var ghost : GHOSTS) {
                float k = Mth.clamp((ghost.age + partial) / SHRINK_TICKS, 0, 1);
                float scale = 1 - k * k * (3 - 2 * k);
                if (ghost.hidden || scale <= .02f) continue;
                var at = ghost.at.subtract(camera);
                ghost.beetle.ghostScale = scale;
                try {
                    dispatcher.render(ghost.beetle, at.x, at.y, at.z, ghost.yaw, partial, pose, buffers, dispatcher.getPackedLightCoords(ghost.beetle, partial));
                } finally {
                    ghost.beetle.ghostScale = 1;
                }
            }
            buffers.endBatch();
        }
        var matrix = pose.last().pose();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend(); RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.disableCull(); RenderSystem.depthMask(false);
        // 1.21ではTesselator.begin()がBufferBuilderを返し、描くのはClientDraw（頂点が無ければ何も描かない。Forge版のBufferUploader.drawWithShader(buffer.end())）。
        var buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (var beetle : beetles) {
            var at = beetle.getPosition(partial).subtract(camera);
            if (beetle.emerging()) emerge(buffer, matrix, at, beetle, partial);
            else if (beetle.appearAge() >= 0 && beetle.appearAge() < APPEAR_FLASH) appear(buffer, matrix, at, beetle.appearAge(), partial);
            if (hold > 0 && !beetle.emerging() && ClientDarkBeetles.isLocalPlayer(beetle.ownerId())) holding(buffer, matrix, at, hold);
        }
        for (var ghost : GHOSTS) recall(buffer, matrix, ghost.at.subtract(camera), ghost.age, partial);
        ClientDraw.drawWithShader(buffer);
        RenderSystem.depthMask(true); RenderSystem.enableCull(); RenderSystem.defaultBlendFunc();
    }

    /** 外部の演出の出現の輪の長さ（tick）: 出現（甲虫と同じ{@link DarkMatterBeetle#EMERGE_TICKS}）の後に、現れた直後の光（{@link #APPEAR_FLASH}）。 */
    public static final int EXTERNAL_TICKS = DarkMatterBeetle.EMERGE_TICKS + APPEAR_FLASH;
    /**
     * 外部の演出（公開APIの{@code DARK_EMERGE_RING}、演出API版3）: 足元{@code feet}に、甲虫の出現と同じ輪を、出てからの時間{@code age}（tick、partialを含む）で描く。
     * 描き方（加算・深度を書かない）と段階（{@code AFTER_PARTICLES}）は甲虫の出現と同じ。描いた四角形の数を返す。
     */
    static int drawExternal(com.mojang.blaze3d.vertex.PoseStack pose, Vec3 camera, Vec3 feet, float age) {
        if (age < 0 || age >= EXTERNAL_TICKS) return 0;
        var matrix = pose.last().pose();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend(); RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.disableCull(); RenderSystem.depthMask(false);
        try {
            var buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            var at = feet.subtract(camera);
            if (age < DarkMatterBeetle.EMERGE_TICKS) emerge(buffer, matrix, at, Mth.clamp(age / DarkMatterBeetle.EMERGE_TICKS, 0, 1));
            else appear(buffer, matrix, at, (int) (age - DarkMatterBeetle.EMERGE_TICKS), age - DarkMatterBeetle.EMERGE_TICKS - (int) (age - DarkMatterBeetle.EMERGE_TICKS));
            // 1.21: 頂点が無ければnull（描かない）。
            var built = buffer.build();
            if (built == null) return 0;
            int quads = built.drawState().vertexCount() / 4;
            com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(built);
            return quads;
        } finally { RenderSystem.depthMask(true); RenderSystem.enableCull(); RenderSystem.defaultBlendFunc(); }
    }

    private static void ring(BufferBuilder buffer, Matrix4f matrix, Vec3 center, float radius, float width, float alpha) {
        if (alpha <= .003f) return;
        // 細い線だけでは遠くから見えにくいので、外側へ広い淡い帯を重ねて光らせる（加算）。
        ClientHeavenEffects.ring(buffer, matrix, center, radius + width, width * 3.5f, R, G, B, Math.min(1, alpha) * .22f);
        ClientHeavenEffects.ring(buffer, matrix, center, radius, width, R, G, B, Math.min(1, alpha));
    }
    /** 出現の進み（0〜1）。同期の値が次に届くまでの間は、1tick先までしか進めない。 */
    public static float emergeProgress(DarkMatterBeetle beetle, float partial) {
        int done = DarkMatterBeetle.EMERGE_TICKS - beetle.emergeLeft();
        return Mth.clamp((done + partial) / DarkMatterBeetle.EMERGE_TICKS, 0, Math.min(1, (done + 1f) / DarkMatterBeetle.EMERGE_TICKS));
    }
    /** 出現の途中: 地面の輪が明るくなり、3本の輪が上から降りながら縮む（天界の予兆の輪の向きを逆にした形）。 */
    private static void emerge(BufferBuilder buffer, Matrix4f matrix, Vec3 at, DarkMatterBeetle beetle, float partial) {
        emerge(buffer, matrix, at, emergeProgress(beetle, partial));
    }
    /** 出現の輪を、進みt（0〜1）で描く（甲虫の出現と、外部の演出の出現の輪で共通）。 */
    private static void emerge(BufferBuilder buffer, Matrix4f matrix, Vec3 at, float t) {
        float fade = Math.min(1, t / .2f);
        ring(buffer, matrix, at.add(0, .04, 0), RADIUS, .16f, (.25f + .65f * t) * fade);
        ring(buffer, matrix, at.add(0, .04, 0), RADIUS * .62f, .07f, (.15f + .5f * t) * fade);
        for (int i = 0; i < 3; i++) {
            float phase = (t * 1.5f + i / 3f) % 1;
            float height = (1 - phase) * HEIGHT * 1.5f;
            ring(buffer, matrix, at.add(0, height, 0), RADIUS * (1.3f - .3f * phase), .09f, (.2f + .8f * t) * (.4f + .6f * phase) * fade);
        }
    }
    /** 現れた直後: 地面の輪が外へ広がって消え、1本の輪が体を下から上へなでる。 */
    private static void appear(BufferBuilder buffer, Matrix4f matrix, Vec3 at, int age, float partial) {
        float u = Mth.clamp((age + partial) / APPEAR_FLASH, 0, 1);
        ring(buffer, matrix, at.add(0, .04, 0), RADIUS * (1 + .7f * u), .05f + .2f * (1 - u), 1 - u);
        ring(buffer, matrix, at.add(0, u * HEIGHT * 1.6f, 0), RADIUS * .9f, .1f, (1 - u) * .8f);
    }
    /** 回収: 地面の輪が縮みながら消え、3本の輪が下から上がりながら広がって消える（出現の逆）。 */
    private static void recall(BufferBuilder buffer, Matrix4f matrix, Vec3 at, int age, float partial) {
        float u = Mth.clamp((age + partial) / RECALL_TICKS, 0, 1);
        ring(buffer, matrix, at.add(0, .04, 0), RADIUS * (1 - .4f * u), .16f, 1 - u);
        for (int i = 0; i < 3; i++) {
            float phase = Mth.clamp(u * 1.4f - i * .15f, 0, 1);
            ring(buffer, matrix, at.add(0, phase * HEIGHT * 1.6f, 0), RADIUS * (.8f + .5f * phase), .09f, (1 - phase) * (1 - .5f * u));
        }
    }
    /** 長押しの途中（本人だけ）: 回収に近づくほど、自分の甲虫の足元の輪が明るく縮む。届いたら（回収の時）最も明るい。 */
    private static void holding(BufferBuilder buffer, Matrix4f matrix, Vec3 at, float hold) {
        ring(buffer, matrix, at.add(0, .04, 0), RADIUS * (1.35f - .35f * hold), .06f + .06f * hold, .15f + .6f * hold);
        if (hold >= 1) ring(buffer, matrix, at.add(0, HEIGHT * .5f, 0), RADIUS, .1f, .8f);
    }
}
