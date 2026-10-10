package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.network.MdBallEffect;
import io.github.pinchan4273.reacademycraft.network.MdRayEffect;
import io.github.pinchan4273.reacademycraft.visual.LegacyChargingArcPose;
import io.github.pinchan4273.reacademycraft.visual.LegacyMdBallAnimation;
import io.github.pinchan4273.reacademycraft.visual.LegacyMdRayAnimation;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Meltdownerの球と光線: Electron Bomb・Scatter Bomb・Electron Missileが撃つ小さな光線と、Meltdowner自身の光線。
 * ゲームの処理・ダメージ・時間はサーバーが持つ。ここで描くのは、サーバーが受理した「既に起きたこと」のsnapshotで、原作の曲線で描く。
 */
@Mod.EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class ClientMdEffects {
    /** Scatter Bombは同時に複数の球を持つので、術者ではなくsessionで区別する。 */
    private static final Map<UUID, Map<Long, Orb>> ORBS = new HashMap<>();
    private static final List<Beam> BEAMS = new ArrayList<>();
    private static ClientLevel world;
    private static long acceptedOrbs, acceptedBeams, orbQuads, beamQuads;
    private static int lastOrbQuads, lastBeamQuads, reloadGeneration;

    private static final class Orb {
        final Player source; final MdBallEffect packet; final LegacyMdBallAnimation animation;
        final RandomSource random = RandomSource.create();
        Orb(Player source, MdBallEffect packet) {
            this.source = source; this.packet = packet;
            animation = new LegacyMdBallAnimation(packet.lifeTicks());
        }
        boolean valid() {
            var client = Minecraft.getInstance();
            return source.isAlive() && !source.isRemoved()
                    && source.level() == client.level && source.level().getEntity(source.getId()) == source;
        }
        /**
         * 原作EntityMdBall.Rは、球をentityの位置から1.6上げて描く（"HACK: Force set the render pos":
         * y = ent.posY - clientPlayer.posY + 1.6、1.12が描く基準の足元から）。つまり術者の足元、-1.2〜0.2のsubのずれ、
         * そして1.6（胸から頭の高さ）。球が撃つ光線の始点（ball.posY + 目の高さ）と同じ高さになる。
         */
        Vec3 position(float partial) {
            var snapshot = animation.snapshot();
            return source.getPosition(partial)
                    .add(packet.offsetX() + snapshot.offsetX(), packet.offsetY() + snapshot.offsetY() + RENDER_RAISE,
                            packet.offsetZ() + snapshot.offsetZ());
        }
        static final double RENDER_RAISE = 1.6;
    }

    /** この方向に沿った光の帯を、カメラへ向けて、原作のRendererRayGlowと同じ配置で描く。 */
    private static void drawGlow(com.mojang.blaze3d.vertex.PoseStack pose, LegacyMdRayAnimation.Snapshot clipped, LegacyMdRayAnimation.Profile profile,
                                 Vec3 direction, Vec3 view) {
        var broadside = direction.cross(view);
        if (broadside.lengthSqr() <= 1e-6) return;
        broadside = broadside.normalize();
        int glow = LegacyMdRenderer.drawBeamGlow(pose, clipped, profile,
                new org.joml.Vector3f((float) direction.x, (float) direction.y, (float) direction.z),
                new org.joml.Vector3f((float) broadside.x, (float) broadside.y, (float) broadside.z));
        lastBeamQuads += glow; beamQuads += glow;
        if (glow > 0) lastGlows++;
    }
    private static int lastGlows;
    /** テスト用の入口: 直前のフレームで描いた光の帯の数。 */
    public static int lastGlows() { return lastGlows; }

    /** 原作EntityRailgunFXは、あらかじめ作った15本の弧を持ち、その中から選ぶ。 */
    private static final class RailgunArcs {
        static final int COUNT = 15;
        static final java.util.List<io.github.pinchan4273.reacademycraft.visual.LegacyArcGeometry.Pattern> PATTERNS = build();
        static final java.util.List<io.github.pinchan4273.reacademycraft.visual.LegacyArcMesh.Mesh> MESHES = bake();
        private static java.util.List<io.github.pinchan4273.reacademycraft.visual.LegacyArcMesh.Mesh> bake() {
            var random = new java.util.Random();
            var out = new ArrayList<io.github.pinchan4273.reacademycraft.visual.LegacyArcMesh.Mesh>(COUNT);
            for (var pattern : PATTERNS)
                out.add(io.github.pinchan4273.reacademycraft.visual.LegacyArcMesh.bake(pattern, pattern.length(), random));
            return List.copyOf(out);
        }
        private static java.util.List<io.github.pinchan4273.reacademycraft.visual.LegacyArcGeometry.Pattern> build() {
            var random = new java.util.Random();
            var out = new ArrayList<io.github.pinchan4273.reacademycraft.visual.LegacyArcGeometry.Pattern>(COUNT);
            for (int i = 0; i < COUNT; i++)
                out.add(io.github.pinchan4273.reacademycraft.visual.LegacyArcGeometry.generate(
                        io.github.pinchan4273.reacademycraft.visual.LegacyArcGeometry.Profile.RAILGUN, random));
            return List.copyOf(out);
        }
    }
    /** 原作SubArc: 光線のどこかで30tickのあいだちらつく小さな弧。 */
    private static final class SubArc {
        final Vec3 local; final double rotX, rotY, rotZ;
        int template, ticks; boolean draw, dead;
        SubArc(Vec3 local, RandomSource random) {
            this.local = local;
            template = random.nextInt(RailgunArcs.COUNT);
            rotX = random.nextDouble() * 360; rotY = random.nextDouble() * 360; rotZ = random.nextDouble() * 360;
        }
        void tick(RandomSource random) {
            if (random.nextDouble() < .5) template = random.nextInt(RailgunArcs.COUNT);
            if (random.nextDouble() < .9) ticks++;
            if (ticks == 30) dead = true;
            if (draw) { if (random.nextDouble() < .4) draw = false; }
            else if (random.nextDouble() < .3) draw = true;
        }
    }

    private static final class Beam {
        final MdRayEffect packet; final LegacyMdRayAnimation animation;
        final LegacyChargingArcPose.Orientation orientation;
        final RandomSource random = RandomSource.create();
        /**
         * 原作EntityMdRayBarrageのSubRay: 25〜29本あり、それぞれ50〜60度の範囲内の自身のyawと、その半分の範囲内のピッチで
         * 向きを変える。それ以外の光線では空。
         */
        final float[][] scatter;
        /** 原作EntityRailgunFXの弧。光線を作るときに、光線に沿って並べる。それ以外では空。 */
        final List<SubArc> arcs = new ArrayList<>();
        Beam(MdRayEffect packet) {
            this.packet = packet;
            var delta = packet.target().subtract(packet.origin());
            orientation = LegacyChargingArcPose.rayOrientation(delta.x, delta.y, delta.z);
            var profile = profile(packet.kind());
            // 小さな光線の長さは常に原作の15。Meltdownerの光線の長さは発動ごとに違い、サーバーが両端の距離として送ってくる。
            animation = packet.kind() == MdRayEffect.MELTDOWNER || packet.kind() == MdRayEffect.RAILGUN
                    ? new LegacyMdRayAnimation(profile, Math.max(1e-3, delta.length()))
                    : new LegacyMdRayAnimation(profile, profile.defaultLength);
            if (packet.kind() == MdRayEffect.BARRAGE) {
                float range = 50 + random.nextFloat() * 10;
                scatter = new float[25 + random.nextInt(5)][];
                for (int i = 0; i < scatter.length; i++)
                    scatter[i] = new float[] {-range + random.nextFloat() * 2 * range, -range / 2 + random.nextFloat() * range};
            } else scatter = new float[0][];
            if (packet.kind() == MdRayEffect.RAILGUN) {
                // 原作は1ブロック先から終点まで1〜2ずつ進み、軸の周りのランダムな角度に、軸から0.1〜0.25ブロック離して弧を置く。
                double length = delta.length();
                for (double at = 1; at <= length; at += 1 + random.nextDouble()) {
                    double angle = random.nextDouble() * Math.PI * 2, radius = .1 + random.nextDouble() * .15;
                    arcs.add(new SubArc(new Vec3(at, radius * Math.sin(angle), radius * Math.cos(angle)), random));
                }
            }
        }
    }
    /**
     * 原作の光線がEntityRayBaseのviewOptimizeを保っていた種類: Meltdowner、Railgun（反射も含む。術者のEntityRailgunFX）、
     * Ray Barrageの狙いの光線。小さな光線はElectron Bomb・Scatter Bomb（viewOptimizeを切る）とElectron Missile（切らない）で
     * 共有しているので、そのままにしている。
     */
    static boolean handFixed(int kind) {
        return kind == MdRayEffect.MELTDOWNER || kind == MdRayEffect.RAILGUN || kind == MdRayEffect.BARRAGE_PRE || kind == MdRayEffect.BARRAGE_PRE_HIT;
    }
    static LegacyMdRayAnimation.Profile profile(int kind) {
        return switch (kind) {
            case MdRayEffect.MELTDOWNER -> LegacyMdRayAnimation.Profile.MELTDOWNER;
            case MdRayEffect.BARRAGE_PRE -> LegacyMdRayAnimation.Profile.BARRAGE_PRE;
            case MdRayEffect.BARRAGE_PRE_HIT -> LegacyMdRayAnimation.Profile.BARRAGE_PRE_HIT;
            case MdRayEffect.BARRAGE -> LegacyMdRayAnimation.Profile.BARRAGE;
            case MdRayEffect.RAILGUN -> LegacyMdRayAnimation.Profile.RAILGUN;
            default -> LegacyMdRayAnimation.Profile.SMALL;
        };
    }

    private ClientMdEffects() { }
    /** 原作の小さな光線は、粒子に各軸U(-0.015, 0.015)の速度を与える。 */
    private static double wiggle(RandomSource random) { return -.015 + random.nextDouble() * .03; }

    private static void currentWorld() {
        var level = Minecraft.getInstance().level;
        if (world != level) { reset(); world = level; }
    }

    private static Player caster(java.util.UUID player, int entityId, net.minecraft.resources.ResourceLocation dimension) {
        var client = Minecraft.getInstance();
        if (client.level == null || client.player == null || !client.level.dimension().location().equals(dimension)) return null;
        var entity = client.level.getEntity(entityId);
        if (!(entity instanceof Player source) || !source.getUUID().equals(player)
                || !source.isAlive()) return null;
        return source;
    }

    public static void receive(MdBallEffect packet) {
        currentWorld();
        var source = caster(packet.player(), packet.entityId(), packet.dimension());
        if (source == null || Minecraft.getInstance().getOverlay() != null) return;
        var orbs = ORBS.computeIfAbsent(packet.player(), ignored -> new java.util.LinkedHashMap<>());
        // 既に表示している球は、その場で更新するか終わらせる（Electron Missileの切り替えのモード）。
        var shown = orbs.get(packet.session());
        if (shown != null) { shown.animation.renew(packet.lifeTicks()); return; }
        if (orbs.size() >= 16) return;
        orbs.put(packet.session(), new Orb(source, packet)); acceptedOrbs++;
    }

    public static void receive(MdRayEffect packet) {
        currentWorld();
        if (caster(packet.player(), packet.entityId(), packet.dimension()) == null
                || Minecraft.getInstance().getOverlay() != null) return;
        // 球のsessionを付けて送られた光線は、その球を終わらせる（原作Electron Missileが、撃った元の球を消すのと同じ）。
        // Electron Bombの光線は独自のsessionを持つ。原作では、その球はフェードアウトさせる。
        var orbs = ORBS.get(packet.player());
        if (orbs != null) { orbs.remove(packet.session()); if (orbs.isEmpty()) ORBS.remove(packet.player()); }
        if (BEAMS.size() < 64) { BEAMS.add(new Beam(packet)); acceptedBeams++; }
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        currentWorld();
        var client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;
        if (client.getOverlay() != null) { cancelEffects(); return; }
        if (client.isPaused()) return;
        for (var id : List.copyOf(ORBS.keySet())) {
            var orbs = ORBS.get(id);
            orbs.values().removeIf(orb -> {
                if (!orb.valid()) return true;
                orb.animation.tick(orb.random);
                return orb.animation.snapshot().finished();
            });
            if (orbs.isEmpty()) ORBS.remove(id);
        }
        BEAMS.removeIf(beam -> {
            beam.animation.tick(beam.random);
            // 原作EntityMdRaySmall.onUpdate: 小さな光線に沿って、1tickに1つmdの粒子を出す。
            if (beam.packet.kind() == MdRayEffect.SMALL && !beam.animation.snapshot().finished()) {
                var direction = beam.packet.target().subtract(beam.packet.origin()).normalize();
                var at = beam.packet.origin().add(direction.scale(beam.random.nextDouble() * 10));
                client.level.addParticle(io.github.pinchan4273.reacademycraft.world.AcademyParticles.MD.get(), at.x, at.y, at.z,
                        wiggle(beam.random), wiggle(beam.random), wiggle(beam.random));
            }
            // 原作は、光線が30tick経つと処理全体を消す。
            if (!beam.arcs.isEmpty()) {
                if (beam.animation.elapsedTicks() >= 30) beam.arcs.clear();
                else { for (var arc : beam.arcs) arc.tick(beam.random); beam.arcs.removeIf(arc -> arc.dead); }
            }
            return beam.animation.snapshot().finished();
        });
    }

    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        lastOrbQuads = lastBeamQuads = lastGlows = 0;
        LegacyMdRenderer.traceFrame();
        var client = Minecraft.getInstance();
        if (client.level != world || client.player == null || client.getOverlay() != null) return;
        var camera = event.getCamera().getPosition();
        var pose = event.getPoseStack();
        float partial = event.getPartialTick();
        for (var orb : ORBS.values().stream().flatMap(orbs -> orbs.values().stream()).toList()) {
            if (!orb.valid()) continue;
            var at = orb.position(partial).subtract(camera);
            pose.pushPose();
            try {
                pose.translate(at.x, at.y, at.z);
                // 原作は球をカメラへ向けて描く。2枚の絵を描く前に、カメラの方向へ向ける。
                pose.mulPose(event.getCamera().rotation());
                int drawn = LegacyMdRenderer.drawOrb(pose, orb.animation.snapshot());
                lastOrbQuads += drawn; orbQuads += drawn;
            } finally { pose.popPose(); }
        }
        for (var beam : BEAMS) {
            // 原作は、view optimizeが必要な光線の始点を、撃った者の手元へずらす。手元は、ローカルプレイヤー自身の光線で一人称の視点なら
            // 一人称の位置、それ以外は三人称の位置。そこから同じ終点へ向ける（ViewOptimize。Electron Bomb・Scatter Bomb・
            // Ray Barrageは切るので対象外）。
            var origin = beam.packet.origin(); var orientation = beam.orientation;
            if (handFixed(beam.packet.kind())) {
                var hand = LegacyChargingArcPose.handOffset(client.options.getCameraType().isFirstPerson(),
                        client.player != null && client.player.getUUID().equals(beam.packet.player()));
                var shift = LegacyChargingArcPose.rayStartShift(hand, beam.orientation.yawDegrees());
                origin = origin.add(shift[0], shift[1], shift[2]);
                var delta = beam.packet.target().subtract(origin);
                orientation = LegacyChargingArcPose.rayOrientation(delta.x, delta.y, delta.z);
            }
            var at = origin.subtract(camera);
            var sample = beam.animation.snapshot();
            // アニメーションの長さは原作のものだが、確定した対象より先には描かない。
            var clipped = new LegacyMdRayAnimation.Snapshot(
                    Math.min(sample.length(), orientation.length()), sample.width(), sample.alpha(), sample.glow(), sample.finished());
            var profile = beam.animation.profile();
            pose.pushPose();
            try {
                pose.translate(at.x, at.y, at.z);
                // 光の帯はカメラへ向ける必要があるので、光線自身の回転を掛ける前に、ワールドの軸から作る。
                // 円柱は、その回転を掛けた空間で描く。
                var view = origin.subtract(camera);
                if (beam.scatter.length > 0) drawSubrays(pose, clipped, profile, beam.orientation.yawDegrees(), beam.orientation.pitchDegrees(), beam.scatter, view);
                else {
                    LegacyMdRenderer.traceRay(0);
                    drawGlow(pose, clipped, profile, beam.packet.target().subtract(origin).normalize(), view);
                    pose.mulPose(Axis.YP.rotationDegrees(-(orientation.yawDegrees() + 90)));
                    pose.mulPose(Axis.ZP.rotationDegrees(-orientation.pitchDegrees()));
                    int drawn = LegacyMdRenderer.drawBeam(pose, clipped, profile);
                    lastBeamQuads += drawn; beamQuads += drawn;
                    int arcs = drawArcs(pose, beam);
                    lastBeamQuads += arcs; beamQuads += arcs;
                }
            } finally { pose.popPose(); }
        }
    }

    /**
     * Ray BarrageのSubRayを、poseの原点から、光線のyawとピッチ（度）からのそれぞれのずれの方向へ描く。
     * 光の帯は、viewの方向のカメラへ向ける。原作EntityMdRayBarrage.BarrageRendererは、SubRayごとに光線のentityの向きを
     * 変えてplainDoRenderを呼ぶ。そのため、細い光線を1本ずつ、光の帯・内側の円柱・外側の円柱（RendererRayComposite）の順で
     * 描き終えてから次へ進む。半透明の合成は描く順序に依存するので、重なる所では、後の光線の光の帯が前の光線の円柱の上に重なる。
     * テストの固定の場面からも呼ぶので、publicにしている。
     */
    public static void drawSubrays(com.mojang.blaze3d.vertex.PoseStack pose, LegacyMdRayAnimation.Snapshot clipped, LegacyMdRayAnimation.Profile profile,
                                   float yaw, float pitch, float[][] scatter, Vec3 view) {
        for (int i = 0; i < scatter.length; i++) {
            var sub = scatter[i];
            LegacyMdRenderer.traceRay(i);
            var turned = new org.joml.Vector3f(1, 0, 0).rotate(new org.joml.Quaternionf()
                    .rotateY((float) Math.toRadians(-(yaw + sub[0] + 90)))
                    .rotateZ((float) Math.toRadians(-(pitch + sub[1]))));
            drawGlow(pose, clipped, profile, new Vec3(turned.x(), turned.y(), turned.z()), view);
            pose.pushPose();
            try {
                pose.mulPose(Axis.YP.rotationDegrees(-(yaw + sub[0] + 90)));
                pose.mulPose(Axis.ZP.rotationDegrees(-(pitch + sub[1])));
                int drawn = LegacyMdRenderer.drawBeam(pose, clipped, profile);
                lastBeamQuads += drawn; beamQuads += drawn;
            } finally { pose.popPose(); }
        }
        LegacyMdRenderer.traceRay(0);
    }

    /**
     * 原作SubArcHandler.drawAll（光線自身の空間で）: このtickに見えている弧を、光線上のそれぞれの位置に、
     * 3つの角度で回し、3分の1の大きさで中心を合わせて描く。
     */
    private static int drawArcs(com.mojang.blaze3d.vertex.PoseStack pose, Beam beam) {
        int drawn = 0;
        for (var arc : beam.arcs) {
            if (!arc.draw || arc.dead) continue;
            var mesh = RailgunArcs.MESHES.get(arc.template);
            if (mesh.quads().isEmpty()) continue;
            pose.pushPose();
            try {
                pose.translate(arc.local.x, arc.local.y, arc.local.z);
                pose.mulPose(Axis.ZP.rotationDegrees((float) arc.rotZ));
                pose.mulPose(Axis.YP.rotationDegrees((float) arc.rotY));
                pose.mulPose(Axis.XP.rotationDegrees((float) arc.rotX));
                pose.scale(.3f, .3f, .3f);
                pose.translate(-RailgunArcs.PATTERNS.get(arc.template).length() / 2, 0, 0);
                LegacyArcRenderer.draw(pose, mesh, 1);
                drawn += mesh.quads().size();
            } finally { pose.popPose(); }
        }
        return drawn;
    }

    @Mod.EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class MdReloadRegistration {
        @SubscribeEvent public static void register(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener) manager -> { cancelEffects(); reloadGeneration++; });
        }
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { reset(); }

    private static void cancelEffects() { ORBS.clear(); BEAMS.clear(); lastOrbQuads = lastBeamQuads = 0; }

    public static void reset() {
        cancelEffects(); world = null;
        acceptedOrbs = acceptedBeams = orbQuads = beamQuads = 0; reloadGeneration = 0;
    }

    public static boolean orbActive(UUID id) { return ORBS.containsKey(id); }
    /** テスト用の入口: 術者の最初の球を描く位置。 */
    @javax.annotation.Nullable public static Vec3 orbPosition(UUID id, float partial) {
        var orbs = ORBS.get(id);
        return orbs == null || orbs.isEmpty() ? null : orbs.values().iterator().next().position(partial);
    }
    public static int orbsFor(UUID id) { var orbs = ORBS.get(id); return orbs == null ? 0 : orbs.size(); }
    /** テスト用の入口: 今の球の不透明度。撮影の前に、見えるようになるまで待つために使う。 */
    public static float orbAlpha(UUID id) {
        var orbs = ORBS.get(id);
        return orbs == null || orbs.isEmpty() ? 0 : orbs.values().iterator().next().animation.snapshot().alpha();
    }
    public static int activeOrbs() { return ORBS.values().stream().mapToInt(Map::size).sum(); }
    public static int activeBeams() { return BEAMS.size(); }
    public static int activeBeams(LegacyMdRayAnimation.Profile profile) {
        return (int) BEAMS.stream().filter(beam -> beam.animation.profile() == profile).count();
    }
    /** テスト用の入口: この種類の最新の光線。撮影の前に、伸び切るまで待つために使う。 */
    public static LegacyMdRayAnimation.Snapshot newestBeam(LegacyMdRayAnimation.Profile profile) {
        for (int i = BEAMS.size() - 1; i >= 0; i--)
            if (BEAMS.get(i).animation.profile() == profile) return BEAMS.get(i).animation.snapshot();
        return null;
    }
    public static long acceptedOrbs() { return acceptedOrbs; }
    public static long acceptedBeams() { return acceptedBeams; }
    public static long orbQuads() { return orbQuads; }
    public static long beamQuads() { return beamQuads; }
    public static int lastOrbQuads() { return lastOrbQuads; }
    public static int lastBeamQuads() { return lastBeamQuads; }
    public static int reloadGeneration() { return reloadGeneration; }
}
