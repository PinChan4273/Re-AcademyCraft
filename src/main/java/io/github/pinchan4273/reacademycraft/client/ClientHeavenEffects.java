package io.github.pinchan4273.reacademycraft.client;

import com.mojang.logging.LogUtils;
import io.github.pinchan4273.reacademycraft.network.HeavenMark;
import io.github.pinchan4273.reacademycraft.network.HeavenRegionState;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.bus.api.SubscribeEvent;
import org.joml.Matrix4f;

/**
 * 天界発現の描画と音の初稿（v1.5.0 §12）。バニラの粒子を使わない独自の描画で、すべて加算の半透明で地形の深度を見る（壁越しには見えない）。実機で見ていないので、濃さや大きさは初期値。
 * <ul>
 * <li>領域: 範囲の箱を、少しずつ内側へ縮めた数枚の面で薄く光らせる。面の端へ向かって透明になるので、境界は滑らかに薄まり、チャンクの格子は描かない。開始で広がり、解除で薄れる。</li>
 * <li>予兆: 対象の大きさに合わせた3本の水平のリングが、対象に付いて上がりながら縮み、発生の時に明るくなって消える（0.5秒）。</li>
 * <li>変異: 上下に走査する1本のリング（身体の変化はサーバーが発生の瞬間に行い、走査に合わせた身体の部分的な変化は描いていない）。</li>
 * <li>発光: 対象の当たり判定の輪郭を光らせる。実際の明るさと光線の威力には関わらない。落雷: 上から落ちる細い白い帯。</li>
 * </ul>
 * 表示の設定（強度・リング・境界・環境音）は{@link ClientSettings}で、効果と費用を変えない。起動の引数{@code -Dacademy.heaven.measure=true}で描画時間のp95をログへ出す（D06の比較の入口）。
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class ClientHeavenEffects {
    private static final float FADE_PER_TICK = .05f, HEIGHT = 16;
    private static final int LAYERS = 5, GRID = 6, EXPIRE = 60, AMBIENT_TICKS = 80;
    /** 輪1本の四角形の数（外部の演出の計数でも使う）。 */
    static final int RING_SEGMENTS = 32;
    /** 層の間隔・濃さと、面の端で透明へ薄れる割合。濃さは2026-11-03の試遊の指摘「境界をもう少し見えやすく」で.012から上げた（縦の筋は使わない）。 */
    private static final float LAYER_INSET = 1.6f, LAYER_ALPHA = .03f, EDGE_FADE = .12f;
    private static final boolean MEASURE = Boolean.getBoolean("academy.heaven.measure");
    private static final long[] SAMPLES = new long[1200];
    private static int sampleAt;

    private static final class Region {
        int chunkX, chunkZ, shape, silent;
        float centerY, x, z, halfX, halfZ, previousAlpha, alpha;
        boolean on, placed;
    }
    private static final class Mark {
        final int entityId, kind, life; int age; final long seed;
        Mark(int entityId, int kind, int life, long seed) { this.entityId = entityId; this.kind = kind; this.life = life; this.seed = seed; }
    }
    private static final Map<Integer, Region> REGIONS = new HashMap<>();
    private static final List<Mark> MARKS = new ArrayList<>();
    private static final Random RANDOM = new Random();
    private static int ambient;

    private ClientHeavenEffects() { }

    public static void receive(HeavenRegionState state) {
        var region = REGIONS.computeIfAbsent(state.ownerId(), id -> new Region());
        region.chunkX = state.chunkX(); region.chunkZ = state.chunkZ(); region.shape = state.shape(); region.centerY = state.centerY();
        region.on = state.on(); region.silent = 0;
        if (!region.placed) { region.placed = true; place(region, 1); }
    }
    private static float radiusChunks(Region region) { return region.shape == 1 ? .5f : region.shape == 25 ? 2.5f : 1.5f; }
    /** 表示の中心と幅を、サーバーの範囲へfractionだけ近づける（チャンクを跨いでも滑らかに動く）。 */
    private static void place(Region region, float fraction) {
        float x = (region.chunkX + .5f) * 16, z = (region.chunkZ + .5f) * 16, half = radiusChunks(region) * 16;
        region.x += (x - region.x) * fraction; region.z += (z - region.z) * fraction;
        region.halfX += (half - region.halfX) * fraction; region.halfZ = region.halfX;
    }
    public static void receive(HeavenMark packet) {
        if (packet.kind() == HeavenMark.GLOW_END) { MARKS.removeIf(m -> m.entityId == packet.entityId() && m.kind == HeavenMark.GLOW); return; }
        // 発生と同時に予兆を消す。
        if (packet.kind() != HeavenMark.TELEGRAPH) MARKS.removeIf(m -> m.entityId == packet.entityId() && m.kind == HeavenMark.TELEGRAPH);
        if (packet.kind() == HeavenMark.GLOW) {
            for (var mark : MARKS) if (mark.entityId == packet.entityId() && mark.kind == HeavenMark.GLOW) { mark.age = 0; return; }
        }
        int life = packet.kind() == HeavenMark.LIGHTNING ? 6 : packet.kind() == HeavenMark.STATUS ? 8 : packet.ticks();
        MARKS.add(new Mark(packet.entityId(), packet.kind(), Math.max(1, life), RANDOM.nextLong()));
        sound(packet);
    }
    /** 短い音。発光の送り直しでは鳴らさない（受け取りの最初の1回だけここへ来る）。 */
    private static void sound(HeavenMark packet) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !ClientSettings.getOn(ClientSettings.HEAVEN_SOUND)) return;
        var entity = minecraft.level.getEntity(packet.entityId());
        if (entity == null) return;
        SoundEvent event; float volume, pitch;
        switch (packet.kind()) {
            case HeavenMark.TELEGRAPH -> { event = SoundEvents.AMETHYST_BLOCK_CHIME; volume = .5f; pitch = 1.4f; }
            case HeavenMark.GLOW -> { event = SoundEvents.AMETHYST_BLOCK_RESONATE; volume = .4f; pitch = 1.6f; }
            case HeavenMark.LIGHTNING -> { event = SoundEvents.LIGHTNING_BOLT_IMPACT; volume = .35f; pitch = 1.5f; }
            case HeavenMark.MUTATE -> { event = SoundEvents.ENCHANTMENT_TABLE_USE; volume = .6f; pitch = .8f; }
            default -> { event = SoundEvents.AMETHYST_CLUSTER_HIT; volume = .5f; pitch = 1.1f; }
        }
        minecraft.level.playLocalSound(entity.getX(), entity.getY(), entity.getZ(), event, SoundSource.PLAYERS, volume, pitch, false);
    }

    @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.isPaused()) return;
        if (minecraft.level == null) { REGIONS.clear(); MARKS.clear(); return; }
        boolean inside = false;
        for (var it = REGIONS.values().iterator(); it.hasNext(); ) {
            var region = it.next();
            region.previousAlpha = region.alpha;
            if (++region.silent > EXPIRE) region.on = false; // 送り直しが来ない（離れた、次元を移った）。
            region.alpha = Mth.clamp(region.alpha + (region.on ? FADE_PER_TICK : -FADE_PER_TICK), 0, 1);
            place(region, .25f);
            if (!region.on && region.alpha <= 0) { it.remove(); continue; }
            var player = minecraft.player;
            if (player != null && region.on && Math.abs(player.getX() - region.x) <= region.halfX && Math.abs(player.getZ() - region.z) <= region.halfZ
                    && Math.abs(player.getY() - region.centerY) <= HEIGHT) inside = true;
        }
        // 控えめな環境音。
        if (inside && ClientSettings.getOn(ClientSettings.HEAVEN_SOUND) && ++ambient >= AMBIENT_TICKS && minecraft.player != null) {
            ambient = 0;
            var player = minecraft.player;
            minecraft.level.playLocalSound(player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_AMBIENT, SoundSource.AMBIENT, .15f, 1.5f, false);
        }
        MARKS.removeIf(mark -> ++mark.age > mark.life);
    }
    /** 26.2: 1.21.1のAFTER_PARTICLESの段階は、このMODの演出の提出の同じ名前の段（WorldSubmits）。 */
    @SubscribeEvent public static void render(net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent extract) {
        io.github.pinchan4273.reacademycraft.client.render.WorldSubmits.on(extract, io.github.pinchan4273.reacademycraft.client.render.WorldSubmits.Stage.AFTER_PARTICLES, ClientHeavenEffects::renderFrame);
    }
    private static void renderFrame(io.github.pinchan4273.reacademycraft.client.render.WorldFrame event) {
        if (REGIONS.isEmpty() && MARKS.isEmpty()) return;
        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        long started = MEASURE ? System.nanoTime() : 0;
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        var camera = event.getCamera().position();
        var matrix = event.getPoseStack().last().pose();
        // 1.21.1のposition_color、blendFunc(SRC_ALPHA, ONE)（加算）、カリングなし、深度は読むが書かない。
        io.github.pinchan4273.reacademycraft.client.render.Immediate.setShader(io.github.pinchan4273.reacademycraft.client.render.Immediate.Shader.COLOR);
        io.github.pinchan4273.reacademycraft.client.render.Immediate.enableBlend(); io.github.pinchan4273.reacademycraft.client.render.Immediate.additiveBlendFunc();
        io.github.pinchan4273.reacademycraft.client.render.Immediate.disableCull(); io.github.pinchan4273.reacademycraft.client.render.Immediate.depthMask(false);
        var buffer = io.github.pinchan4273.reacademycraft.client.render.Immediate.begin(com.mojang.blaze3d.PrimitiveTopology.QUADS);
        float intensity = ClientSettings.heavenGlowPercent() / 100f;
        boolean border = ClientSettings.getOn(ClientSettings.HEAVEN_BORDER), rings = ClientSettings.getOn(ClientSettings.HEAVEN_RINGS);
        if (intensity > 0) for (var region : REGIONS.values()) region(buffer, matrix, camera, region, partial, intensity, border);
        for (var mark : MARKS) {
            var entity = minecraft.level.getEntity(mark.entityId);
            if (entity == null) continue;
            switch (mark.kind) {
                case HeavenMark.TELEGRAPH -> { if (rings) telegraph(buffer, matrix, camera, entity, mark, partial); }
                case HeavenMark.MUTATE -> { if (rings) scan(buffer, matrix, camera, entity, mark, partial); }
                case HeavenMark.GLOW -> outline(buffer, matrix, camera, entity, partial);
                case HeavenMark.LIGHTNING -> lightning(buffer, matrix, camera, entity, mark, partial);
                case HeavenMark.STATUS -> flash(buffer, matrix, camera, entity, mark, partial);
                default -> { }
            }
        }
        buffer.end();
        io.github.pinchan4273.reacademycraft.client.render.Immediate.depthMask(true); io.github.pinchan4273.reacademycraft.client.render.Immediate.enableCull(); io.github.pinchan4273.reacademycraft.client.render.Immediate.defaultBlendFunc();
        if (MEASURE) measure(System.nanoTime() - started);
    }
    private static void measure(long nanos) {
        SAMPLES[sampleAt++] = nanos;
        if (sampleAt < SAMPLES.length) return;
        sampleAt = 0;
        long[] copy = SAMPLES.clone();
        Arrays.sort(copy);
        LogUtils.getLogger().info("heaven client draw p95={}ms regions={} marks={}", String.format(java.util.Locale.ROOT, "%.3f", copy[(int) (copy.length * .95)] / 1e6), REGIONS.size(), MARKS.size());
    }

    // ---- 領域 ----

    private static void region(io.github.pinchan4273.reacademycraft.client.render.Immediate.Vertices buffer, Matrix4f matrix, Vec3 camera, Region region, float partial, float intensity, boolean border) {
        float alpha = Mth.lerp(partial, region.previousAlpha, region.alpha);
        if (alpha <= 0) return;
        // 開始で中心から広がる。解除は広がったまま薄れる。
        float grow = region.on ? alpha : 1;
        float x = (float) (region.x - camera.x), y = (float) (region.centerY - camera.y), z = (float) (region.z - camera.z);
        for (int layer = 0; layer < (border ? LAYERS : 2); layer++) {
            float inset = layer * LAYER_INSET;
            float hx = Math.max(.5f, region.halfX * grow - inset), hz = Math.max(.5f, region.halfZ * grow - inset), hy = Math.max(.5f, HEIGHT * grow - inset);
            float a = LAYER_ALPHA * alpha * intensity;
            // 側面4つと上下。
            face(buffer, matrix, x - hx, y - hy, z - hz, 2 * hx, 0, 0, 0, 2 * hy, 0, a);
            face(buffer, matrix, x - hx, y - hy, z + hz, 2 * hx, 0, 0, 0, 2 * hy, 0, a);
            face(buffer, matrix, x - hx, y - hy, z - hz, 0, 0, 2 * hz, 0, 2 * hy, 0, a);
            face(buffer, matrix, x + hx, y - hy, z - hz, 0, 0, 2 * hz, 0, 2 * hy, 0, a);
            face(buffer, matrix, x - hx, y - hy, z - hz, 2 * hx, 0, 0, 0, 0, 2 * hz, a);
            face(buffer, matrix, x - hx, y + hy, z - hz, 2 * hx, 0, 0, 0, 0, 2 * hz, a);
        }
    }
    /** 面を格子に分け、端で透明になるように色を付ける。 */
    private static void face(io.github.pinchan4273.reacademycraft.client.render.Immediate.Vertices buffer, Matrix4f matrix, float ox, float oy, float oz, float ux, float uy, float uz, float vx, float vy, float vz, float alpha) {
        for (int i = 0; i < GRID; i++) for (int j = 0; j < GRID; j++) {
            float u0 = i / (float) GRID, u1 = (i + 1) / (float) GRID, v0 = j / (float) GRID, v1 = (j + 1) / (float) GRID;
            corner(buffer, matrix, ox, oy, oz, ux, uy, uz, vx, vy, vz, u0, v0, alpha);
            corner(buffer, matrix, ox, oy, oz, ux, uy, uz, vx, vy, vz, u1, v0, alpha);
            corner(buffer, matrix, ox, oy, oz, ux, uy, uz, vx, vy, vz, u1, v1, alpha);
            corner(buffer, matrix, ox, oy, oz, ux, uy, uz, vx, vy, vz, u0, v1, alpha);
        }
    }
    private static void corner(io.github.pinchan4273.reacademycraft.client.render.Immediate.Vertices buffer, Matrix4f matrix, float ox, float oy, float oz, float ux, float uy, float uz, float vx, float vy, float vz, float u, float v, float alpha) {
        float edge = Math.min(Math.min(u, 1 - u), Math.min(v, 1 - v)) / EDGE_FADE;
        float a = alpha * Mth.clamp(edge, 0, 1);
        buffer.addVertex(matrix, ox + ux * u + vx * v, oy + uy * u + vy * v, oz + uz * u + vz * v).setColor(.93f, .96f, 1, a);
    }

    // ---- 怪現象 ----

    /** 水平の輪（加算の半透明、POSITION_COLORの四角形）。甲虫の出現と回収の演出（{@link ClientDarkBeetleSummon}）も同じ輪を使う。 */
    static void ring(io.github.pinchan4273.reacademycraft.client.render.Immediate.Vertices buffer, Matrix4f matrix, Vec3 center, float radius, float width, float r, float g, float b, float alpha) {
        for (int i = 0; i < RING_SEGMENTS; i++) {
            float a0 = i * Mth.TWO_PI / RING_SEGMENTS, a1 = (i + 1) * Mth.TWO_PI / RING_SEGMENTS;
            float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
            float x = (float) center.x, y = (float) center.y, z = (float) center.z, inner = radius - width;
            buffer.addVertex(matrix, x + c0 * inner, y, z + s0 * inner).setColor(r, g, b, alpha);
            buffer.addVertex(matrix, x + c1 * inner, y, z + s1 * inner).setColor(r, g, b, alpha);
            buffer.addVertex(matrix, x + c1 * radius, y, z + s1 * radius).setColor(r, g, b, alpha);
            buffer.addVertex(matrix, x + c0 * radius, y, z + s0 * radius).setColor(r, g, b, alpha);
        }
    }
    private static float size(Entity entity) { return ringRadius(entity.getBbWidth()); }
    /** 体の幅からの輪の半径（自然能力の予兆と同じ決まり。外部の演出が実体を持たないときは幅1で使う）。 */
    static float ringRadius(float width) { return Math.max(.6f, width * .9f); }
    /**
     * 外部の演出（公開APIの{@code HEAVEN_RING}、演出API版3。Forge 1.5.1 の 91f33867 の移植）: 自然能力の予兆と同じ3本のリングを、同じ描き方
     * （{@link io.github.pinchan4273.reacademycraft.client.render.Immediate}の position_color・加算・深度を書かない、段階は AFTER_PARTICLES）で描く。
     * シェーダーパックの間は自然能力と同じく{@code ShaderPackDraw}の描き方へ書き直される。見る人の設定で「輪」がOFFなら描かない（自然能力と同じ）。描いた四角形の数を返す。
     */
    static int drawExternalTelegraph(com.mojang.blaze3d.vertex.PoseStack pose, Vec3 camera, Vec3 feet, float height, float radius, float t) {
        if (!ClientSettings.getOn(ClientSettings.HEAVEN_RINGS)) return 0;
        var matrix = pose.last().pose();
        io.github.pinchan4273.reacademycraft.client.render.Immediate.setShader(io.github.pinchan4273.reacademycraft.client.render.Immediate.Shader.COLOR);
        io.github.pinchan4273.reacademycraft.client.render.Immediate.enableBlend(); io.github.pinchan4273.reacademycraft.client.render.Immediate.additiveBlendFunc();
        io.github.pinchan4273.reacademycraft.client.render.Immediate.disableCull(); io.github.pinchan4273.reacademycraft.client.render.Immediate.depthMask(false);
        try {
            var buffer = io.github.pinchan4273.reacademycraft.client.render.Immediate.begin(com.mojang.blaze3d.PrimitiveTopology.QUADS);
            telegraph(buffer, matrix, feet.subtract(camera), height, radius, Mth.clamp(t, 0, 1));
            buffer.end();
            return 3 * RING_SEGMENTS;
        } finally {
            io.github.pinchan4273.reacademycraft.client.render.Immediate.depthMask(true); io.github.pinchan4273.reacademycraft.client.render.Immediate.enableCull();
            io.github.pinchan4273.reacademycraft.client.render.Immediate.defaultBlendFunc();
        }
    }
    /** 上がりながら縮む3本のリング。終わりに近いほど明るい。 */
    private static void telegraph(io.github.pinchan4273.reacademycraft.client.render.Immediate.Vertices buffer, Matrix4f matrix, Vec3 camera, Entity entity, Mark mark, float partial) {
        float t = Mth.clamp((mark.age + partial) / mark.life, 0, 1);
        telegraph(buffer, matrix, entity.getPosition(partial).subtract(camera), entity.getBbHeight(), size(entity), t);
    }
    /** 予兆の3本のリングを、足元at（カメラからの相対）・体の高さ・半径・進みt（0〜1）で描く（自然能力と外部の演出で共通）。 */
    private static void telegraph(io.github.pinchan4273.reacademycraft.client.render.Immediate.Vertices buffer, Matrix4f matrix, Vec3 at, float height, float radius, float t) {
        for (int i = 0; i < 3; i++) {
            float phase = (t + i / 3f) % 1;
            float scale = 1.5f - phase * .7f;
            ring(buffer, matrix, at.add(0, phase * height, 0), radius * scale, .08f, .95f, .97f, 1, (.25f + t * .75f) * (1 - phase * .4f));
        }
    }
    /** 変異の走査: 1本のリングが下から上へ、上から下へ。 */
    private static void scan(io.github.pinchan4273.reacademycraft.client.render.Immediate.Vertices buffer, Matrix4f matrix, Vec3 camera, Entity entity, Mark mark, float partial) {
        float t = Mth.clamp((mark.age + partial) / mark.life, 0, 1);
        float travel = t < .5f ? t * 2 : 2 - t * 2;
        var at = entity.getPosition(partial).subtract(camera).add(0, travel * entity.getBbHeight(), 0);
        ring(buffer, matrix, at, size(entity) * 1.3f, .12f, 1, 1, 1, 1 - t * .5f);
    }
    /** 状態異常の発生の短い光（広がって消えるリング）。 */
    private static void flash(io.github.pinchan4273.reacademycraft.client.render.Immediate.Vertices buffer, Matrix4f matrix, Vec3 camera, Entity entity, Mark mark, float partial) {
        float t = Mth.clamp((mark.age + partial) / mark.life, 0, 1);
        var at = entity.getPosition(partial).subtract(camera).add(0, entity.getBbHeight() * .5f, 0);
        ring(buffer, matrix, at, size(entity) * (1 + t), .1f, 1, 1, 1, 1 - t);
    }
    /** 発光の輪郭: 当たり判定の12本の辺。 */
    private static void outline(io.github.pinchan4273.reacademycraft.client.render.Immediate.Vertices buffer, Matrix4f matrix, Vec3 camera, Entity entity, float partial) {
        var offset = entity.getPosition(partial).subtract(entity.position());
        AABB box = entity.getBoundingBox().move(offset).move(-camera.x, -camera.y, -camera.z).inflate(.05);
        double[] xs = {box.minX, box.maxX}, ys = {box.minY, box.maxY}, zs = {box.minZ, box.maxZ};
        for (double y : ys) for (double z : zs) edge(buffer, matrix, new Vec3(box.minX, y, z), new Vec3(box.maxX, y, z));
        for (double x : xs) for (double z : zs) edge(buffer, matrix, new Vec3(x, box.minY, z), new Vec3(x, box.maxY, z));
        for (double x : xs) for (double y : ys) edge(buffer, matrix, new Vec3(x, y, box.minZ), new Vec3(x, y, box.maxZ));
    }
    /** カメラ（原点）を向いた細い帯。 */
    private static void edge(io.github.pinchan4273.reacademycraft.client.render.Immediate.Vertices buffer, Matrix4f matrix, Vec3 a, Vec3 b) { band(buffer, matrix, a, b, .035f, .95f, .97f, 1, .8f); }
    private static void band(io.github.pinchan4273.reacademycraft.client.render.Immediate.Vertices buffer, Matrix4f matrix, Vec3 a, Vec3 b, float width, float r, float g, float bl, float alpha) {
        var side = b.subtract(a).cross(a.add(b).scale(.5));
        if (side.lengthSqr() < 1e-8) return;
        side = side.normalize().scale(width);
        buffer.addVertex(matrix, (float) (a.x + side.x), (float) (a.y + side.y), (float) (a.z + side.z)).setColor(r, g, bl, alpha);
        buffer.addVertex(matrix, (float) (b.x + side.x), (float) (b.y + side.y), (float) (b.z + side.z)).setColor(r, g, bl, alpha);
        buffer.addVertex(matrix, (float) (b.x - side.x), (float) (b.y - side.y), (float) (b.z - side.z)).setColor(r, g, bl, alpha);
        buffer.addVertex(matrix, (float) (a.x - side.x), (float) (a.y - side.y), (float) (a.z - side.z)).setColor(r, g, bl, alpha);
    }
    /** 落雷: 空から対象へ折れ曲がって落ちる白い帯。 */
    private static void lightning(io.github.pinchan4273.reacademycraft.client.render.Immediate.Vertices buffer, Matrix4f matrix, Vec3 camera, Entity entity, Mark mark, float partial) {
        float t = Mth.clamp((mark.age + partial) / mark.life, 0, 1);
        var random = new Random(mark.seed);
        Vec3 end = entity.getPosition(partial).subtract(camera).add(0, entity.getBbHeight() * .5f, 0);
        Vec3 from = end.add(random.nextDouble() * 4 - 2, 24, random.nextDouble() * 4 - 2);
        int steps = 10;
        Vec3 last = from;
        for (int i = 1; i <= steps; i++) {
            float f = i / (float) steps;
            Vec3 next = from.lerp(end, f);
            if (i < steps) next = next.add(random.nextDouble() - .5, 0, random.nextDouble() - .5);
            band(buffer, matrix, last, next, .12f * (1 - t * .6f), 1, 1, 1, 1 - t);
            last = next;
        }
    }
    @SubscribeEvent public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) { REGIONS.clear(); MARKS.clear(); }
}
