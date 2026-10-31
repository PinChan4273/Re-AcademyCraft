package io.github.pinchan4273.reacademycraft.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import io.github.pinchan4273.reacademycraft.api.client.BeamResult;
import io.github.pinchan4273.reacademycraft.api.client.EffectRequest;
import io.github.pinchan4273.reacademycraft.api.client.EffectStyle;
import io.github.pinchan4273.reacademycraft.api.client.ExternalPresentation;
import io.github.pinchan4273.reacademycraft.network.VmWave;
import io.github.pinchan4273.reacademycraft.visual.ExternalBeamBook;
import io.github.pinchan4273.reacademycraft.visual.ExternalEffectRules;
import io.github.pinchan4273.reacademycraft.visual.LegacyArcGenAnimation;
import io.github.pinchan4273.reacademycraft.visual.LegacyArcGeometry;
import io.github.pinchan4273.reacademycraft.visual.LegacyArcMesh;
import io.github.pinchan4273.reacademycraft.visual.LegacyChargingArcPose;
import io.github.pinchan4273.reacademycraft.visual.LegacyChargingArcAnimation;
import io.github.pinchan4273.reacademycraft.visual.LegacyIntensifyBurstAnimation;
import io.github.pinchan4273.reacademycraft.visual.LegacyMdBallAnimation;
import io.github.pinchan4273.reacademycraft.visual.LegacyRippleAnimation;
import io.github.pinchan4273.reacademycraft.visual.LegacySurroundAnimation;
import io.github.pinchan4273.reacademycraft.visual.LegacyThunderBoltArcAnimation;
import io.github.pinchan4273.reacademycraft.world.AcademyParticles;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import javax.annotation.Nullable;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

/**
 * 外部の演出（公開API{@link ExternalPresentation}の{@code play}の中身。演出API版2）。自然能力の演出の一覧・台帳・上限とは別の帳簿
 * （{@link ExternalBeamBook}、持ち主ごと）で持ち、描画の部品とテクスチャだけを共用する。自然能力のクラスの入口は呼ばない
 * （台帳の枠を使うことと、自然能力の演出を置き換えることを避ける）。ローカルプレイヤーの視線や速度も変えない。
 * 寿命と見た目は自然能力の同じ演出に合わせる。一時停止の間は進まず、ログアウト・世界の切り替え・資源の読み直しで消す。
 */
@Mod.EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class ClientExternalEffects {
    private ClientExternalEffects() { }

    public static final int DEFAULT_LIMIT = 32, MIN_LIMIT = 1, MAX_LIMIT = 256, MAX_PLASMA_PER_OWNER = 2;

    /** 帳簿に入る1つの演出。 */
    interface Effect {
        void tick(Minecraft client);
        boolean finished();
        /** 演出を止める（帳簿から外す前に呼ぶ）。自然能力のクラスへ預けた物を、外部の分だけすぐ取り除く。何度呼んでもよい。 */
        default void stop() { }
        /** 各フレームの描画の前に呼ぶ。追従する実体の同一性を確かめる（自然能力のクラスは番号だけで実体を探すので）。 */
        default void frame(ClientLevel level) { }
        /** 描く段階。描かない演出はnull。 */
        @Nullable default RenderLevelStageEvent.Stage stage() { return null; }
        /** 描いた四角形の数を返す。 */
        default int render(PoseStack pose, Camera camera, float partial, Minecraft client) { return 0; }
    }

    private static final ExternalBeamBook<Effect> BOOK = new ExternalBeamBook<>();
    private static ClientLevel world;
    private static int lastFrameQuads, lastFrameBatches;
    private static long lastFrameNanos, totalQuads;

    public static int limit() { return ClientSettings.getInt(ClientSettings.EXTERNAL_EFFECTS_PER_OWNER, DEFAULT_LIMIT, MIN_LIMIT, MAX_LIMIT); }

    private static void onMainThread() {
        if (!Minecraft.getInstance().isSameThread()) throw new IllegalStateException("ExternalPresentation must be called on the client main thread");
    }
    private static void currentWorld() {
        var level = Minecraft.getInstance().level;
        if (world != level) { cancelAll(); world = level; }
    }

    public static BeamResult play(EffectRequest request) { return offer(request, false); }
    public static BeamResult start(EffectRequest request) { return offer(request, true); }

    private static BeamResult offer(EffectRequest request, boolean lasting) {
        onMainThread();
        currentWorld();
        var client = Minecraft.getInstance();
        String owner = request == null || request.ownerModId() == null ? "" : request.ownerModId();
        if (client.level == null || client.player == null || client.getOverlay() != null) { BOOK.refuse(owner, BeamResult.NOT_READY); return BeamResult.NOT_READY; }
        var refused = ExternalEffectRules.check(request, client.level.dimension().location(), id -> ModList.get().isLoaded(id));
        if (refused == null && request.style().lasting() != lasting) refused = BeamResult.INVALID;
        if (refused == null && needsEntity(request.style()) && follow(request, client.level) == null) refused = BeamResult.INVALID;
        if (refused != null) { BOOK.refuse(owner, refused); return refused; }
        if (request.style() == EffectStyle.PLASMA_CANNON && BOOK.owned(owner).stream().filter(Plasma.class::isInstance).count() >= MAX_PLASMA_PER_OWNER
                && BOOK.get(owner, request.effectId()) == null) {
            BOOK.refuse(owner, BeamResult.FULL);
            return BeamResult.FULL;
        }
        return BOOK.offer(owner, request.effectId(), limit(), () -> make(request, client.level));
    }
    private static boolean needsEntity(EffectStyle style) {
        return switch (style) { case RAILGUN_HAND, LIGHT_SHIELD, JET_SHIELD, STORM_WING, MINE_RAY_BASIC, MINE_RAY_EXPERT, MINE_RAY_LUCK -> true; default -> false; };
    }
    public static boolean update(String ownerModId, long effectId, @Nullable Vec3 position, @Nullable Vec3 target) {
        onMainThread();
        var client = Minecraft.getInstance();
        if (client.level == null || !(BOOK.get(ownerModId, effectId) instanceof Lasting lasting) || lasting.over || lasting.fading()) return false;
        // nullは今の値のまま。合わせた次の値を、受け付けと同じ規則で確かめ、通らなければ状態も期間も変えない。
        var now = lasting.current;
        var next = new EffectRequest(now.ownerModId(), now.effectId(), now.dimension(), now.style(), position != null ? position : now.position(),
                target != null ? target : now.target(), now.entityId(), now.entityUuid(), now.scale());
        if (ExternalEffectRules.check(next, client.level.dimension().location(), id -> ModList.get().isLoaded(id)) != null) return false;
        lasting.current = next;
        lasting.lease = ExternalPresentation.EFFECT_LEASE_TICKS;
        lasting.applied(position != null, target != null);
        return true;
    }
    public static boolean end(String ownerModId, long effectId) {
        onMainThread();
        var effect = BOOK.get(ownerModId, effectId);
        if (effect == null) return false;
        if (effect instanceof Fading fading) return fading.fade();
        effect.stop();
        return BOOK.end(ownerModId, effectId);
    }
    public static int clear(String ownerModId) {
        onMainThread();
        // 薄れる演出は、薄れ終わるまで帳簿に残す（上限の外で残らない）。数には入れる。
        int fading = 0;
        for (var effect : BOOK.owned(ownerModId)) {
            if (effect instanceof Fading fade) { if (fade.fade()) fading++; }
            else effect.stop();
        }
        return fading + BOOK.clearIf(ownerModId, effect -> !(effect instanceof Fading));
    }
    /** すべて消す（ログアウト・世界の切り替え・資源の読み直し）。自然能力のクラスへ預けた分も止める。 */
    private static void cancelAll() {
        for (var effect : BOOK.all()) effect.stop();
        BOOK.cancelAll();
    }
    public static int active(String ownerModId) { return BOOK.active(ownerModId); }
    public static ExternalPresentation.Diagnostics diagnostics(String ownerModId) {
        return new ExternalPresentation.Diagnostics(BOOK.active(ownerModId), BOOK.count(ownerModId, BeamResult.ACCEPTED),
                BOOK.count(ownerModId, BeamResult.DUPLICATE), BOOK.count(ownerModId, BeamResult.FULL), BOOK.count(ownerModId, BeamResult.UNSUPPORTED),
                BOOK.count(ownerModId, BeamResult.INVALID), BOOK.count(ownerModId, BeamResult.NOT_READY),
                BOOK.totalActive(), lastFrameQuads, lastFrameBatches, lastFrameNanos, totalQuads);
    }

    private static Effect make(EffectRequest request, ClientLevel level) {
        return switch (request.style()) {
            case ARC_WEAK -> new WeakArc(request, level);
            case ARC_STRONG -> new StrongArcs(request, level);
            case ARC_AREA -> new AreaArc(request);
            case ARC_BURST -> new Burst(request, level);
            case LIGHTNING -> { lightning(request, level); yield Done.INSTANCE; }
            case TELEPORT_TRAIL -> { trail(request, level, false); yield Done.INSTANCE; }
            case SHIFT_TRAIL -> { trail(request, level, true); yield Done.INSTANCE; }
            case WAVE_RINGS -> new Waves(request);
            case BLOOD_SPLASH -> new Splashes(request, level, false);
            case BLOOD_SPLASH_LARGE -> new Splashes(request, level, true);
            case CRITICAL_FORMULA -> new Formulae(request, level);
            case BLOOD_SPRAY -> new Sprays(request, level);
            case GROUND_DEBRIS -> { debris(request, level); yield Done.INSTANCE; }
            case RAILGUN_HAND -> new RailgunHand(request);
            case ORB -> new Orb(request, level);
            case ARC_CONTINUOUS -> new ContinuousArc(request, level);
            case ARC_SURROUND -> new Surround(request, level, LegacySurroundAnimation.Kind.THIN, ArcTemplates.THIN);
            case ARC_SURROUND_NORMAL -> new Surround(request, level, LegacySurroundAnimation.Kind.NORMAL, ArcTemplates.NORMAL);
            case ARC_SURROUND_BOLD -> new Surround(request, level, LegacySurroundAnimation.Kind.BOLD, ArcTemplates.BOLD);
            case RIPPLE -> new Ripple(request);
            case LIGHT_SHIELD -> new Shield(request, level);
            case JET_SHIELD -> new Visual(request, io.github.pinchan4273.reacademycraft.skill.JetEngine.ID, 0, ClientJetEngineEffect::receive);
            case STORM_WING -> new StormWing(request);
            case PLASMA_CANNON -> new Plasma(request);
            case MINE_RAY_BASIC -> new Visual(request, io.github.pinchan4273.reacademycraft.skill.MineRay.BASIC.id(), 0, ClientMineRayEffect::receive);
            case MINE_RAY_EXPERT -> new Visual(request, io.github.pinchan4273.reacademycraft.skill.MineRay.EXPERT.id(), 0, ClientMineRayEffect::receive);
            case MINE_RAY_LUCK -> new Visual(request, io.github.pinchan4273.reacademycraft.skill.MineRay.LUCK.id(), 0, ClientMineRayEffect::receive);
        };
    }

    /** 番号とUUIDの両方が合う実体。無ければnull。 */
    private static @Nullable Entity follow(EffectRequest request, ClientLevel level) {
        if (request.entityUuid() == null || request.entityId() < 0) return null;
        var entity = level.getEntity(request.entityId());
        return entity != null && entity.getUUID().equals(request.entityUuid()) && !entity.isRemoved() ? entity : null;
    }
    /** 受け付けたときの実体がいなくなったか、同じ番号が別の実体になったか。実体を持たない演出はfalse。 */
    private static boolean lost(@Nullable Entity entity, ClientLevel level) {
        return entity != null && (entity.isRemoved() || level.getEntity(entity.getId()) != entity);
    }

    /** すぐ終わる演出（粒子や実体を出して、帳簿には残さない）。 */
    private enum Done implements Effect {
        INSTANCE;
        @Override public void tick(Minecraft client) { }
        @Override public boolean finished() { return true; }
    }

    // ---- 電弧 ------------------------------------------------------------------------------------

    private static List<LegacyArcGeometry.Pattern> patterns(LegacyArcGeometry.Profile profile) {
        var out = new ArrayList<LegacyArcGeometry.Pattern>(20); var random = new Random();
        for (int i = 0; i < 20; i++) out.add(LegacyArcGeometry.generate(profile, random));
        return List.copyOf(out);
    }
    private static final class ArcTemplates {
        static final List<LegacyArcGeometry.Pattern> WEAK = patterns(LegacyArcGeometry.Profile.WEAK);
        static final List<LegacyArcGeometry.Pattern> STRONG = patterns(LegacyArcGeometry.Profile.STRONG);
        static final List<LegacyArcGeometry.Pattern> AREA = patterns(LegacyArcGeometry.Profile.AOE);
        record Thin(double length, LegacyArcMesh.Mesh mesh) { }
        static final List<Thin> THIN = around(LegacyArcGeometry.Profile.SURROUND_THIN);
        static final List<Thin> NORMAL = around(LegacyArcGeometry.Profile.SURROUND_NORMAL);
        static final List<Thin> BOLD = around(LegacyArcGeometry.Profile.SURROUND_BOLD);
        private static List<Thin> around(LegacyArcGeometry.Profile profile) {
            var random = new Random(); var out = new ArrayList<Thin>(LegacyIntensifyBurstAnimation.TEMPLATES);
            for (int i = 0; i < LegacyIntensifyBurstAnimation.TEMPLATES; i++) {
                var pattern = LegacyArcGeometry.generate(profile, random);
                out.add(new Thin(pattern.length(), LegacyArcMesh.bake(pattern, pattern.length(), random)));
            }
            return List.copyOf(out);
        }
    }
    /** {@code hand}があれば、自然能力と同じく、回した空間で手元へずらす。 */
    private static int drawArc(PoseStack pose, Camera camera, Vec3 from, LegacyChargingArcPose.Orientation orientation, LegacyArcMesh.Mesh mesh, @Nullable Entity hand, Minecraft client) {
        var at = from.subtract(camera.getPosition());
        pose.pushPose();
        try {
            pose.translate(at.x, at.y, at.z);
            pose.mulPose(Axis.YP.rotationDegrees(-(orientation.yawDegrees() + 90)));
            pose.mulPose(Axis.ZP.rotationDegrees(-orientation.pitchDegrees()));
            if (hand != null) {
                var offset = LegacyChargingArcPose.handOffset(client.options.getCameraType().isFirstPerson(), hand == client.player);
                pose.translate(offset.x(), offset.y(), offset.z());
            }
            LegacyArcRenderer.draw(pose, mesh, 1);
            return mesh.quads().size();
        } finally { pose.popPose(); }
    }
    private static LegacyChargingArcPose.Orientation orientationOf(EffectRequest request) {
        var delta = request.target().subtract(request.position());
        return LegacyChargingArcPose.orientation(delta.x, delta.y, delta.z);
    }

    /** 自然能力のアーク（{@link ClientArcGenEffect}）と同じ弱い電弧。 */
    private static final class WeakArc implements Effect {
        final EffectRequest request; final LegacyChargingArcPose.Orientation orientation; final LegacyArcGenAnimation animation = new LegacyArcGenAnimation();
        final Random random = new Random(); LegacyArcMesh.Mesh mesh; int template = -1; boolean fresh = true, gone;
        @Nullable final Entity entity;
        WeakArc(EffectRequest request, ClientLevel level) { this.request = request; entity = follow(request, level); orientation = orientationOf(request); bake(); }
        void bake() {
            int next = animation.snapshot().template();
            if (template != next) { mesh = LegacyArcMesh.bake(ArcTemplates.WEAK.get(next), orientation.length(), random); template = next; }
        }
        @Override public void frame(ClientLevel level) { if (lost(entity, level)) gone = true; }
        @Override public void tick(Minecraft client) {
            frame(client.level);
            if (gone) return;
            if (fresh) { fresh = false; return; }
            animation.tick(random);
            if (!animation.snapshot().finished()) bake();
        }
        @Override public boolean finished() { return gone || animation.snapshot().finished(); }
        @Override public RenderLevelStageEvent.Stage stage() { return RenderLevelStageEvent.Stage.AFTER_PARTICLES; }
        @Override public int render(PoseStack pose, Camera camera, float partial, Minecraft client) {
            return animation.snapshot().visible() ? drawArc(pose, camera, request.position(), orientation, mesh, entity, client) : 0;
        }
    }

    /** 自然能力のサンダーボルト（{@link ClientThunderBoltEffect}）の主電弧3本。 */
    private static final class StrongArcs implements Effect {
        private static final class Arc {
            final LegacyThunderBoltArcAnimation animation = LegacyThunderBoltArcAnimation.mainArc(); final Random random = new Random();
            LegacyArcMesh.Mesh mesh; int template = -1;
            void bake(double cutoff) {
                int next = animation.snapshot().template();
                if (template != next) { mesh = LegacyArcMesh.bake(ArcTemplates.STRONG.get(next), cutoff, random); template = next; }
            }
        }
        final EffectRequest request; final LegacyChargingArcPose.Orientation orientation; final List<Arc> arcs = List.of(new Arc(), new Arc(), new Arc()); boolean fresh = true, gone;
        @Nullable final Entity entity;
        StrongArcs(EffectRequest request, ClientLevel level) { this.request = request; entity = follow(request, level); orientation = orientationOf(request); for (var arc : arcs) arc.bake(orientation.length()); }
        @Override public void frame(ClientLevel level) { if (lost(entity, level)) gone = true; }
        @Override public void tick(Minecraft client) {
            frame(client.level);
            if (gone) return;
            if (fresh) { fresh = false; return; }
            for (var arc : arcs) { arc.animation.tick(arc.random); if (!arc.animation.snapshot().finished()) arc.bake(orientation.length()); }
        }
        @Override public boolean finished() { return gone || arcs.stream().allMatch(arc -> arc.animation.snapshot().finished()); }
        @Override public RenderLevelStageEvent.Stage stage() { return RenderLevelStageEvent.Stage.AFTER_PARTICLES; }
        @Override public int render(PoseStack pose, Camera camera, float partial, Minecraft client) {
            int quads = 0;
            for (var arc : arcs) {
                var frame = arc.animation.snapshot();
                if (frame.visible() && !frame.finished()) quads += drawArc(pose, camera, request.position(), orientation, arc.mesh, entity, client);
            }
            return quads;
        }
    }

    /** 自然能力のサンダーボルト（{@link ClientThunderBoltEffect}）の範囲の電弧1本。手元へはずらさない。 */
    private static final class AreaArc implements Effect {
        final EffectRequest request; final LegacyChargingArcPose.Orientation orientation; final Random random = new Random();
        final LegacyThunderBoltArcAnimation animation = LegacyThunderBoltArcAnimation.aoeArc(random);
        LegacyArcMesh.Mesh mesh; int template = -1; boolean fresh = true;
        AreaArc(EffectRequest request) { this.request = request; orientation = orientationOf(request); bake(); }
        void bake() {
            int next = animation.snapshot().template();
            if (template != next) { mesh = LegacyArcMesh.bake(ArcTemplates.AREA.get(next), orientation.length(), random); template = next; }
        }
        @Override public void tick(Minecraft client) {
            if (fresh) { fresh = false; return; }
            animation.tick(random);
            if (!animation.snapshot().finished()) bake();
        }
        @Override public boolean finished() { return animation.snapshot().finished(); }
        @Override public RenderLevelStageEvent.Stage stage() { return RenderLevelStageEvent.Stage.AFTER_PARTICLES; }
        @Override public int render(PoseStack pose, Camera camera, float partial, Minecraft client) {
            var frame = animation.snapshot();
            return frame.visible() && !frame.finished() ? drawArc(pose, camera, request.position(), orientation, mesh, null, client) : 0;
        }
    }

    /** 自然能力のボディインテンシファイ（{@link ClientIntensifyBurst}）の体の周りの細い電弧。 */
    private static final class Burst implements Effect {
        final EffectRequest request; final ClientLevel level; @Nullable final Entity entity; final Vec3 position;
        final LegacyIntensifyBurstAnimation animation = new LegacyIntensifyBurstAnimation(); final Random random = new Random(); int age; boolean lost;
        Burst(EffectRequest request, ClientLevel level) {
            this.request = request; this.level = level; entity = follow(request, level); position = request.position();
        }
        @Override public void tick(Minecraft client) {
            if (entity != null && (entity.isRemoved() || level.getEntity(entity.getId()) != entity)) { lost = true; return; }
            animation.update(++age, random);
        }
        @Override public boolean finished() { return lost || animation.finished(); }
        @Override public RenderLevelStageEvent.Stage stage() { return RenderLevelStageEvent.Stage.AFTER_PARTICLES; }
        @Override public int render(PoseStack pose, Camera camera, float partial, Minecraft client) {
            var at = (entity == null ? position : entity.getPosition(partial)).subtract(camera.getPosition());
            int quads = 0;
            pose.pushPose();
            try {
                pose.translate(at.x, at.y, at.z);
                if (entity != null) pose.mulPose(Axis.YP.rotationDegrees(-entity.getViewYRot(partial)));
                for (var frame : animation.snapshot()) {
                    if (frame.dead() || !frame.visible()) continue;
                    var thin = ArcTemplates.THIN.get(frame.template()); var mesh = thin.mesh();
                    pose.pushPose();
                    try {
                        pose.translate(frame.x(), frame.y(), frame.z());
                        pose.mulPose(Axis.ZP.rotationDegrees((float) frame.rotZ()));
                        pose.mulPose(Axis.YP.rotationDegrees((float) frame.rotY()));
                        pose.mulPose(Axis.XP.rotationDegrees((float) frame.rotX()));
                        pose.scale(.3f, .3f, .3f); pose.translate(-thin.length() / 2, 0, 0);
                        LegacyArcRenderer.draw(pose, mesh, 1);
                        quads += mesh.quads().size();
                    } finally { pose.popPose(); }
                }
            } finally { pose.popPose(); }
            return quads;
        }
    }

    // ---- すぐ終わる演出 --------------------------------------------------------------------------

    private static int nextLightningId = -2_000_000;
    /** 自然能力のサンダークラップ（{@link ClientThunderClap}）と同じ、見た目だけの雷。負のローカルIDを自然能力の範囲と分ける。 */
    private static void lightning(EffectRequest request, ClientLevel level) {
        var bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt == null) return;
        for (int attempts = 0; attempts < 256; attempts++) {
            if (nextLightningId == Integer.MIN_VALUE) nextLightningId = -2_000_000;
            int id = nextLightningId--;
            if (level.getEntity(id) != null) continue;
            bolt.setId(id); bolt.setPos(request.position()); bolt.setOldPosAndRot(); bolt.setVisualOnly(true);
            level.putNonPlayerEntity(id, bolt);
            return;
        }
    }

    private static final RandomSource RANDOM = RandomSource.create();
    /** 自然能力の{@link ClientTeleporterBursts}の軌跡と同じ。 */
    private static void trail(EffectRequest request, ClientLevel level, boolean shift) {
        var delta = request.target().subtract(request.position());
        double distance = delta.length();
        var direction = delta.normalize();
        var at = request.position();
        double step = 1, walked = step;
        int spawned = 0;
        while (walked <= distance && spawned < 128) {
            at = at.add(direction.scale(step));
            double sideways = shift ? .05 : .02;
            level.addParticle(AcademyParticles.TP.get(), at.x, at.y, at.z,
                    -sideways + RANDOM.nextDouble() * 2 * sideways, -.02 + RANDOM.nextDouble() * .07, -sideways + RANDOM.nextDouble() * 2 * sideways);
            spawned++;
            step = shift ? .6 + RANDOM.nextDouble() * .4 : 1 + RANDOM.nextDouble();
            walked += step;
        }
    }

    /** 自然能力の{@link ClientGroundshock}の破片と煙（視線は変えない）。円の中のブロックは多くても約113個。 */
    private static void debris(EffectRequest request, ClientLevel level) {
        var centre = BlockPos.containing(request.position());
        int radius = Mth.ceil(request.scale());
        var random = level.random;
        double limit = request.scale() * request.scale();
        for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
            if (dx * dx + dz * dz > limit) continue;
            var pos = centre.offset(dx, 0, dz);
            var state = level.getBlockState(pos);
            int count = 4 + random.nextInt(4);
            if (!state.isAir()) for (int i = 0; i < count; i++)
                level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, state),
                        pos.getX() + random.nextDouble(), pos.getY() + 1 + random.nextDouble() * .5 + .2, pos.getZ() + random.nextDouble(),
                        -.2 + random.nextDouble() * .4, .1 + random.nextDouble() * .2, -.2 + random.nextDouble() * .4);
            if (random.nextFloat() < .5f)
                level.addAlwaysVisibleParticle(AcademyParticles.SMOKE.get(), true,
                        pos.getX() + .5 + (random.nextDouble() * .6 - .3), pos.getY() + 1 + random.nextDouble() * .2, pos.getZ() + .5 + (random.nextDouble() * .6 - .3),
                        random.nextDouble() * .06 - .03, .03 + random.nextDouble() * .03, random.nextDouble() * .06 - .03);
        }
    }

    // ---- 輪の波 ----------------------------------------------------------------------------------

    /** 自然能力の{@link ClientVmWaves}と同じ輪の波。輪の作り方は同じクラスを使い、一覧は別に持つ。 */
    private static final class Waves implements Effect {
        final ClientVmWaves.Wave wave;
        Waves(EffectRequest request) {
            var d = request.target().subtract(request.position()).normalize();
            float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z)), pitch = (float) -Math.toDegrees(Math.asin(Mth.clamp(d.y, -1, 1)));
            wave = new ClientVmWaves.Wave(new VmWave(request.dimension(), request.position(), yaw, pitch, 20, 10, 2, Math.min(request.scale(), 7.999f)), RANDOM);
        }
        @Override public void tick(Minecraft client) { wave.ticks++; }
        @Override public boolean finished() { return wave.ticks >= ClientVmWaves.LIFE; }
        @Override public RenderLevelStageEvent.Stage stage() { return RenderLevelStageEvent.Stage.AFTER_PARTICLES; }
        @Override public int render(PoseStack pose, Camera camera, float partial, Minecraft client) {
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
            RenderSystem.setShaderTexture(0, ClientVmWaves.TEXTURE);
            RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); RenderSystem.disableCull(); RenderSystem.disableDepthTest();
            int quads = 0;
            try {
                double maxAlpha = Mth.clamp(ClientVmWaves.ALPHA.valueAt(wave.ticks / (double) ClientVmWaves.LIFE), 0, 1);
                var at = wave.position.subtract(camera.getPosition());
                pose.pushPose();
                pose.translate(at.x, at.y, at.z);
                pose.mulPose(Axis.YP.rotationDegrees(-wave.yaw));
                pose.mulPose(Axis.XP.rotationDegrees(wave.pitch));
                pose.translate(0, 0, wave.ticks / 40.0);
                double sizeScale = ClientVmWaves.SIZE.valueAt(Mth.clamp(wave.ticks / 20.0, 0, 1.62));
                for (var ring : wave.rings) {
                    float alpha = (float) Math.min(maxAlpha, Mth.clamp(ClientVmWaves.ALPHA.valueAt((wave.ticks - ring.timeOffset()) / (double) ring.life()), 0, 1));
                    if (alpha <= 0) continue;
                    pose.pushPose();
                    pose.translate(0, 0, ring.offset());
                    float scale = (float) (ring.size() * sizeScale);
                    pose.scale(scale, scale, 1);
                    var m = pose.last().pose(); var buffer = Tesselator.getInstance().getBuilder();
                    float a = alpha * .7f;
                    buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
                    buffer.vertex(m, -.5f, -.5f, 0).uv(0, 0).color(1, 1, 1, a).endVertex();
                    buffer.vertex(m, 1, -.5f, 0).uv(1, 0).color(1, 1, 1, a).endVertex();
                    buffer.vertex(m, 1, 1, 0).uv(1, 1).color(1, 1, 1, a).endVertex();
                    buffer.vertex(m, -.5f, 1, 0).uv(0, 1).color(1, 1, 1, a).endVertex();
                    BufferUploader.drawWithShader(buffer.end());
                    pose.popPose();
                    quads++;
                }
                pose.popPose();
            } finally { RenderSystem.enableDepthTest(); RenderSystem.enableCull(); }
            return quads;
        }
    }

    // ---- 血と数式 --------------------------------------------------------------------------------

    /** 体の箱（実体が無ければ、1×2の箱）。 */
    private record Box(double x, double y, double z, double width, double height) { }
    private static Box box(EffectRequest request, ClientLevel level) {
        var entity = follow(request, level);
        if (entity != null) return new Box(entity.getX(), entity.getY(), entity.getZ(), entity.getBbWidth(), entity.getBbHeight());
        return new Box(request.position().x, request.position().y, request.position().z, 1, 2);
    }

    /** 自然能力の{@link ClientTeleporterBursts}の血しぶきと同じ（4〜6個、10枚の絵を1tickに1枚）。 */
    private static final class Splashes implements Effect {
        private record Splash(Vec3 at, float size) { }
        final List<Splash> splashes = new ArrayList<>(); int frame;
        Splashes(EffectRequest request, ClientLevel level, boolean large) {
            var box = box(request, level);
            if (large) {
                // 自然能力の{@link ClientBloodRetro}と同じ（6〜9個、大きさ1.4〜1.8、体の箱の幅の範囲）。
                for (int i = 0, n = 6 + RANDOM.nextInt(4); i < n; i++)
                    splashes.add(new Splash(new Vec3(box.x + (RANDOM.nextDouble() * 2 - 1) * box.width, box.y + RANDOM.nextDouble() * box.height,
                            box.z + (RANDOM.nextDouble() * 2 - 1) * box.width), 1.4f + RANDOM.nextFloat() * .4f));
                return;
            }
            for (int i = 0, max = 4 + RANDOM.nextInt(3); i < max; i++) {
                double angle = RANDOM.nextDouble() * Math.PI * 2;
                double radius = .5 * (.8 * box.width + RANDOM.nextDouble() * .2 * box.width);
                splashes.add(new Splash(new Vec3(box.x + radius * Math.sin(angle), box.y + RANDOM.nextDouble() * box.height, box.z + radius * Math.cos(angle)),
                        .8f + RANDOM.nextFloat() * .5f));
            }
        }
        @Override public void tick(Minecraft client) { frame++; }
        @Override public boolean finished() { return frame >= ClientTeleporterBursts.BLOOD_FRAMES; }
        @Override public RenderLevelStageEvent.Stage stage() { return RenderLevelStageEvent.Stage.AFTER_PARTICLES; }
        @Override public int render(PoseStack pose, Camera camera, float partial, Minecraft client) {
            if (finished()) return 0;
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
            RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); RenderSystem.disableCull(); RenderSystem.depthMask(false);
            try {
                RenderSystem.setShaderTexture(0, ClientTeleporterBursts.BLOOD[frame]);
                var colour = ClientTeleporterBursts.BLOOD_COLOUR;
                for (var splash : splashes)
                    ClientTeleporterBursts.billboard(pose, camera.rotation(), splash.at.subtract(camera.getPosition()), splash.size, true,
                            colour[0] / 255f, colour[1] / 255f, colour[2] / 255f, colour[3] / 255f);
            } finally { RenderSystem.depthMask(true); RenderSystem.enableCull(); }
            return splashes.size();
        }
    }

    /** 自然能力の{@link ClientTeleporterBursts}の会心の数式と同じ（5〜7個）。 */
    private static final class Formulae implements Effect {
        private static final class Formula {
            Vec3 at; final Vec3 drift; final float size, alpha; final int picture, life; int ticks;
            Formula(Vec3 at, Vec3 drift, float size, float alpha, int picture, int life) { this.at = at; this.drift = drift; this.size = size; this.alpha = alpha; this.picture = picture; this.life = life; }
            float alpha() {
                if (ticks < 2) return alpha * ticks / 2f;
                if (ticks <= life) return alpha;
                return Math.max(0, alpha * (1 - (ticks - life) / 20f));
            }
            boolean finished() { return ticks > life + 20; }
        }
        final List<Formula> formulae = new ArrayList<>();
        private static double range() { return (RANDOM.nextDouble() * 2 - 1) * .03; }
        Formulae(EffectRequest request, ClientLevel level) {
            var box = box(request, level);
            for (int i = 0, max = 5 + RANDOM.nextInt(3); i < max; i++) {
                double angle = RANDOM.nextDouble() * Math.PI * 2;
                double radius = box.width * (.5 + RANDOM.nextDouble() * .2);
                float alpha = Math.min(255, 152 + RANDOM.nextInt(384 - 152)) / 255f;
                formulae.add(new Formula(new Vec3(box.x + radius * Math.sin(angle), box.y + RANDOM.nextDouble() * box.height, box.z + radius * Math.cos(angle)),
                        new Vec3(range(), range(), range()), 1 + RANDOM.nextFloat() * .7f, alpha, RANDOM.nextInt(ClientTeleporterBursts.FORMULA_PICTURES), 10 + RANDOM.nextInt(6)));
            }
        }
        @Override public void tick(Minecraft client) { for (var formula : formulae) { formula.ticks++; formula.at = formula.at.add(formula.drift); } }
        @Override public boolean finished() { return formulae.stream().allMatch(Formula::finished); }
        @Override public RenderLevelStageEvent.Stage stage() { return RenderLevelStageEvent.Stage.AFTER_PARTICLES; }
        @Override public int render(PoseStack pose, Camera camera, float partial, Minecraft client) {
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
            RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); RenderSystem.disableCull(); RenderSystem.depthMask(false);
            int quads = 0;
            try {
                for (var formula : formulae) {
                    float alpha = formula.alpha();
                    if (alpha <= 0) continue;
                    RenderSystem.setShaderTexture(0, ClientTeleporterBursts.FORMULA[formula.picture]);
                    ClientTeleporterBursts.billboard(pose, camera.rotation(), formula.at.subtract(camera.getPosition()), formula.size, false,
                            ClientTeleporterBursts.FORMULA_GREY, ClientTeleporterBursts.FORMULA_GREY, ClientTeleporterBursts.FORMULA_GREY, alpha);
                    quads++;
                }
            } finally { RenderSystem.depthMask(true); RenderSystem.enableCull(); }
            return quads;
        }
    }

    /** 自然能力の{@link ClientBloodRetro}の血の跡と同じ（当たった面に2つ、1200tick、そのブロックが無くなると消える）。 */
    private static final class Sprays implements Effect {
        private record Spray(BlockPos pos, Direction face, Vec3 centre, int picture, float size, float rotation, float u, float v) { }
        final List<Spray> sprays = new ArrayList<>(); final ClientLevel level; int age;
        Sprays(EffectRequest request, ClientLevel level) {
            this.level = level;
            var hit = level.clip(new ClipContext(request.position(), request.target(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, null));
            if (hit.getType() != HitResult.Type.BLOCK) return;
            var pos = hit.getBlockPos(); var face = hit.getDirection();
            var shape = level.getBlockState(pos).getShape(level, pos);
            var box = shape.isEmpty() ? new net.minecraft.world.phys.AABB(0, 0, 0, 1, 1, 1) : shape.bounds();
            var centre = new Vec3(pos.getX() + (box.minX + box.maxX) / 2 + face.getStepX() * .51 * (box.maxX - box.minX),
                    pos.getY() + (box.minY + box.maxY) / 2 + face.getStepY() * .51 * (box.maxY - box.minY),
                    pos.getZ() + (box.minZ + box.maxZ) / 2 + face.getStepZ() * .51 * (box.maxZ - box.minZ));
            boolean flat = face.getAxis() == Direction.Axis.Y;
            for (int i = 0; i < 2; i++) {
                float size = (1.1f + RANDOM.nextFloat() * .3f) * (flat ? 1 : .8f);
                sprays.add(new Spray(pos, face, centre, RANDOM.nextInt(10), size, RANDOM.nextFloat() * 360,
                        (float) (RANDOM.nextGaussian() * .15), (float) (RANDOM.nextGaussian() * .15)));
            }
        }
        @Override public void tick(Minecraft client) {
            age++;
            sprays.removeIf(spray -> level.getBlockState(spray.pos).isAir());
        }
        @Override public boolean finished() { return sprays.isEmpty() || age > ClientBloodRetro.LIFE; }
        @Override public RenderLevelStageEvent.Stage stage() { return RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS; }
        @Override public int render(PoseStack pose, Camera camera, float partial, Minecraft client) {
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
            RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); RenderSystem.disableCull();
            RenderSystem.enableDepthTest(); RenderSystem.depthMask(false);
            try {
                for (var spray : sprays) {
                    boolean flat = spray.face.getAxis() == Direction.Axis.Y;
                    RenderSystem.setShaderTexture(0, (flat ? ClientBloodRetro.WALL : ClientBloodRetro.GROUND)[spray.picture % 3]);
                    pose.pushPose();
                    var at = spray.centre.subtract(camera.getPosition());
                    pose.translate(at.x, at.y, at.z);
                    pose.mulPose(spray.face.getRotation());
                    pose.mulPose(Axis.XP.rotationDegrees(-90));
                    pose.translate(spray.u, spray.v, 0);
                    pose.scale(spray.size, spray.size, spray.size);
                    pose.mulPose(Axis.ZP.rotationDegrees(spray.rotation));
                    var m = pose.last().pose(); var buffer = Tesselator.getInstance().getBuilder();
                    buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
                    buffer.vertex(m, -.5f, -.5f, 0).uv(0, 1).color(1f, 1f, 1f, 1f).endVertex();
                    buffer.vertex(m, .5f, -.5f, 0).uv(1, 1).color(1f, 1f, 1f, 1f).endVertex();
                    buffer.vertex(m, .5f, .5f, 0).uv(1, 0).color(1f, 1f, 1f, 1f).endVertex();
                    buffer.vertex(m, -.5f, .5f, 0).uv(0, 0).color(1f, 1f, 1f, 1f).endVertex();
                    BufferUploader.drawWithShader(buffer.end());
                    pose.popPose();
                }
            } finally { RenderSystem.depthMask(true); RenderSystem.enableCull(); }
            return sprays.size();
        }
    }

    // ---- 持続する演出 ----------------------------------------------------------------------------

    /** 更新で期間を延ばす演出。期間が切れると終わる（呼び出し側がいなくなっても残らない）。 */
    private abstract static class Lasting implements Effect {
        /** 今の要求。{@link #update}が確かめた値だけが入る。 */
        EffectRequest current;
        int lease = ExternalPresentation.EFFECT_LEASE_TICKS; boolean over;
        Lasting(EffectRequest request) { current = request; }
        /** {@link #current}が変わった後に呼ぶ。引数は、位置と向かう先を更新で渡されたか（nullなら今の値のまま）。 */
        void applied(boolean positionGiven, boolean targetGiven) { }
        /** 薄れている途中か。 */
        boolean fading() { return false; }
        /** 期間が切れたときの終わり方。 */
        void expire() { stop(); over = true; }
        void tickLease() {
            if (over || fading()) return;
            if (--lease <= 0) expire();
        }
        @Override public void tick(Minecraft client) { tickLease(); }
        @Override public boolean finished() { return over; }
    }

    private static final class Orb extends Lasting {
        final LegacyMdBallAnimation animation = new LegacyMdBallAnimation(ExternalPresentation.EFFECT_LEASE_TICKS); final RandomSource random = RandomSource.create();
        @Nullable final Entity entity; Vec3 offset = Vec3.ZERO;
        Orb(EffectRequest request, ClientLevel level) {
            super(request);
            entity = follow(request, level);
            if (entity != null) offset = request.position().subtract(entity.position());
        }
        @Override void applied(boolean positionGiven, boolean targetGiven) {
            animation.renew(ExternalPresentation.EFFECT_LEASE_TICKS);
            // 位置を渡されたときだけ、ずれを取り直す。期間だけの更新で、古い絶対位置へ戻さない。
            if (positionGiven && entity != null) offset = current.position().subtract(entity.position());
        }
        @Override public void frame(ClientLevel level) { if (lost(entity, level)) over = true; }
        Vec3 base(float partial) { return entity == null ? current.position() : entity.getPosition(partial).add(offset); }
        @Override public void tick(Minecraft client) {
            frame(client.level);
            if (over) return;
            tickLease();
            animation.tick(random);
            if (animation.snapshot().finished()) over = true;
        }
        @Override public RenderLevelStageEvent.Stage stage() { return ClientMdEffects.STAGE; }
        @Override public int render(PoseStack pose, Camera camera, float partial, Minecraft client) {
            var snapshot = animation.snapshot();
            var at = base(partial).add(snapshot.offsetX(), snapshot.offsetY(), snapshot.offsetZ()).subtract(camera.getPosition());
            pose.pushPose();
            try {
                pose.translate(at.x, at.y, at.z);
                pose.mulPose(camera.rotation());
                return LegacyMdRenderer.drawOrb(pose, snapshot);
            } finally { pose.popPose(); }
        }
    }

    /** 自然能力の{@link ClientMagneticEffects}の細い電弧。音は鳴らさない。 */
    private static final class ContinuousArc extends Lasting {
        private static final class Templates {
            static final List<LegacyArcGeometry.Pattern> THIN = patterns(LegacyArcGeometry.Profile.THIN_CONTINUOUS);
        }
        final LegacyChargingArcAnimation animation = new LegacyChargingArcAnimation(1, .1, .6); final Random random = new Random();
        LegacyArcMesh.Mesh mesh; int template = -1; double length = -1;
        @Nullable final Entity entity;
        ContinuousArc(EffectRequest request, ClientLevel level) { super(request); entity = follow(request, level); }
        @Override public void frame(ClientLevel level) { if (lost(entity, level)) over = true; }
        @Override public void tick(Minecraft client) {
            frame(client.level);
            if (over) return;
            tickLease();
            animation.tick(random);
            double d = current.target().subtract(current.position()).length();
            var frame = animation.snapshot();
            if (template != frame.template() || length != d) {
                mesh = LegacyArcMesh.bake(Templates.THIN.get(frame.template()), d, random); template = frame.template(); length = d;
            }
        }
        @Override public RenderLevelStageEvent.Stage stage() { return RenderLevelStageEvent.Stage.AFTER_PARTICLES; }
        @Override public int render(PoseStack pose, Camera camera, float partial, Minecraft client) {
            if (mesh == null || !animation.snapshot().visible()) return 0;
            return drawArc(pose, camera, current.position(), orientationOf(current), mesh, entity, client);
        }
    }

    /** 自然能力の{@link ClientChargingSurround}の体の周りを這う電弧。 */
    private static final class Surround extends Lasting {
        final LegacySurroundAnimation animation; final List<ArcTemplates.Thin> templates; final Random random = new Random(); @Nullable final Entity entity;
        Surround(EffectRequest request, ClientLevel level, LegacySurroundAnimation.Kind kind, List<ArcTemplates.Thin> templates) {
            super(request);
            this.templates = templates;
            entity = follow(request, level);
            var size = entity != null ? new Vec3(entity.getBbWidth() * 1.3, entity.getBbHeight() * 1.3, entity.getBbWidth() * 1.3) : new Vec3(request.scale(), request.scale(), request.scale());
            animation = new LegacySurroundAnimation(kind, size);
        }
        @Override public void frame(ClientLevel level) { if (lost(entity, level)) over = true; }
        @Override public void tick(Minecraft client) {
            frame(client.level);
            if (over) return;
            tickLease();
            animation.tick(random);
        }
        @Override public RenderLevelStageEvent.Stage stage() { return RenderLevelStageEvent.Stage.AFTER_PARTICLES; }
        @Override public int render(PoseStack pose, Camera camera, float partial, Minecraft client) {
            var at = (entity == null ? current.position() : entity.getPosition(partial)).subtract(camera.getPosition());
            int quads = 0;
            pose.pushPose();
            try {
                pose.translate(at.x, at.y, at.z);
                if (entity != null) pose.mulPose(Axis.YP.rotationDegrees(-entity.getViewYRot(partial)));
                for (var frame : animation.snapshot()) {
                    if (frame.dead() || !frame.visible()) continue;
                    var thin = templates.get(frame.template()); var mesh = thin.mesh();
                    pose.pushPose();
                    try {
                        pose.translate(frame.position().x, frame.position().y, frame.position().z);
                        pose.mulPose(Axis.ZP.rotationDegrees((float) frame.rotZ()));
                        pose.mulPose(Axis.YP.rotationDegrees((float) frame.rotY()));
                        pose.mulPose(Axis.XP.rotationDegrees((float) frame.rotX()));
                        pose.scale(.3f, .3f, .3f); pose.translate(-thin.length() / 2, 0, 0);
                        LegacyArcRenderer.draw(pose, mesh, 1);
                        quads += mesh.quads().size();
                    } finally { pose.popPose(); }
                }
            } finally { pose.popPose(); }
            return quads;
        }
    }

    private static final class Ripple extends Lasting {
        final long started = net.minecraft.Util.getMillis();
        Ripple(EffectRequest request) { super(request); }
        @Override public RenderLevelStageEvent.Stage stage() { return RenderLevelStageEvent.Stage.AFTER_PARTICLES; }
        @Override public int render(PoseStack pose, Camera camera, float partial, Minecraft client) {
            var at = current.position().subtract(camera.getPosition());
            pose.pushPose();
            try {
                pose.translate(at.x, at.y, at.z);
                return LegacyRippleRenderer.draw(pose, LegacyRippleAnimation.sample(Math.max(0, (net.minecraft.Util.getMillis() - started) / 1000.0)));
            } finally { pose.popPose(); }
        }
    }

    /**
     * 描画が大きい演出は、自然能力のクラスへ外部の番号（負の数。サーバーが配る正の番号と重ならない）で預け、そのまま描いてもらう。
     * 預かり先の一覧に上限は無いので、自然能力の演出の枠を奪わない。上限と期間はこの帳簿が持つ。
     */
    private static long nextToken = -1;

    /** {@link io.github.pinchan4273.reacademycraft.network.SkillVisual}で始めて止める演出（ジェット・竜巻・採掘の光線）。 */
    private static class Visual extends Lasting {
        final long token = nextToken--; final EffectRequest request; final java.util.function.Consumer<io.github.pinchan4273.reacademycraft.network.SkillVisual> target;
        Visual(EffectRequest request, net.minecraft.resources.ResourceLocation kind, int value, java.util.function.Consumer<io.github.pinchan4273.reacademycraft.network.SkillVisual> target) {
            super(request);
            this.request = request; this.target = target;
            target.accept(new io.github.pinchan4273.reacademycraft.network.SkillVisual(request.dimension(), request.entityId(), token, kind, value));
        }
        @Override public void stop() { target.accept(new io.github.pinchan4273.reacademycraft.network.SkillVisual(request.dimension(), request.entityId(), token, null, 0)); }
        /** 同じ番号でも別の実体（UUIDが違う）なら、預けた物を止める。自然能力のクラスは番号だけで実体を探す。 */
        @Override public void frame(ClientLevel level) {
            if (!over && follow(request, level) == null) { stop(); over = true; }
        }
        @Override public void tick(Minecraft client) {
            frame(client.level);
            if (!over) tickLease();
        }
    }

    /**
     * 止めると、自然能力と同じように薄れて消える演出。薄れている間も帳簿に残り、上限に数える。効果IDも使ったまま。
     * ログアウト・世界の切り替え・資源の読み直し・実体の入れ替わりでは、{@link #stop}ですぐ取り除く。
     */
    private abstract static class Fading extends Visual {
        /** 預け先から消えないときに自分で取り除くまでのtick（どちらの演出も、薄れるのにこれより短い）。 */
        private static final int MAX_FADE_TICKS = 200;
        boolean fading; int fadeTicks;
        Fading(EffectRequest request, net.minecraft.resources.ResourceLocation kind, int value, java.util.function.Consumer<io.github.pinchan4273.reacademycraft.network.SkillVisual> target) {
            super(request, kind, value, target);
        }
        abstract boolean handed();
        /** 薄れ始める。今回で始めたらtrue（すでに薄れていればfalse）。 */
        boolean fade() {
            if (fading || over) return false;
            fading = true;
            target.accept(new io.github.pinchan4273.reacademycraft.network.SkillVisual(request.dimension(), request.entityId(), token, null, 0));
            return true;
        }
        @Override boolean fading() { return fading; }
        @Override void expire() { fade(); }
        @Override public void tick(Minecraft client) {
            frame(client.level);
            if (over) return;
            if (!fading) { tickLease(); return; }
            if (!handed() || ++fadeTicks >= MAX_FADE_TICKS) { stop(); over = true; }
        }
    }

    private static final class StormWing extends Fading {
        StormWing(EffectRequest request) { super(request, io.github.pinchan4273.reacademycraft.skill.StormWing.ID, 20, ClientStormWingEffect::receive); }
        @Override boolean handed() { return ClientStormWingEffect.hasExternal(token); }
        @Override public void stop() { ClientStormWingEffect.endExternal(token); }
    }

    private static final class Shield extends Lasting {
        final long token = nextToken--; final EffectRequest request; final ClientLevel level; int sequence;
        Shield(EffectRequest request, ClientLevel level) { super(request); this.request = request; this.level = level; send(); }
        void send() {
            var entity = follow(request, level);
            if (entity == null) return;
            var normal = current.target().subtract(entity.position());
            if (normal.lengthSqr() < 1e-6) return;
            normal = normal.normalize();
            ClientLightShieldEffect.receive(new io.github.pinchan4273.reacademycraft.network.LightShieldState(request.dimension(), request.entityId(), token, ++sequence, true,
                    (float) normal.x, (float) normal.y, (float) normal.z, request.scale(), io.github.pinchan4273.reacademycraft.skill.LightShieldMode.DEFAULT));
        }
        @Override void applied(boolean positionGiven, boolean targetGiven) { send(); }
        @Override public void stop() { ClientLightShieldEffect.endExternal(token); }
        @Override public void frame(ClientLevel level) {
            if (!over && follow(request, level) == null) { stop(); over = true; }
        }
        @Override public void tick(Minecraft client) {
            frame(level);
            if (!over) tickLease();
        }
    }

    private static final class Plasma extends Fading {
        int phase;
        Plasma(EffectRequest request) {
            super(request, io.github.pinchan4273.reacademycraft.skill.PlasmaCannon.ID, 0, ClientPlasmaCannonEffect::receive);
            ClientPlasmaCannonEffect.scaleExternal(token, request.scale());
            move();
        }
        void move() { ClientPlasmaCannonEffect.move(new io.github.pinchan4273.reacademycraft.network.SkillVisualMove(token, current.position(), phase)); }
        @Override void applied(boolean positionGiven, boolean targetGiven) {
            if (targetGiven) phase = 1;
            move();
        }
        @Override boolean handed() { return ClientPlasmaCannonEffect.hasExternal(token); }
        @Override public void stop() { ClientPlasmaCannonEffect.endExternal(token); }
        @Override public void frame(ClientLevel level) { }
    }

    /** 自然能力の{@link ClientRailgunHand}へ預ける（寿命1.6秒の間、帳簿の枠を持つ）。 */
    private static final class RailgunHand implements Effect {
        final EffectRequest request; final Object burst; int ticks; boolean gone;
        RailgunHand(EffectRequest request) { this.request = request; burst = ClientRailgunHand.startExternal(request.entityId()); }
        @Override public void stop() { ClientRailgunHand.endExternal(burst); gone = true; }
        @Override public void frame(ClientLevel level) { if (!gone && follow(request, level) == null) stop(); }
        /**
         * 自然能力の一覧は実時間（1600ms）で消すので、tickの数だけで帳簿から外すと、消える前に帳簿の外へ出る。
         * 一覧から消えるまで帳簿に持つ。描画が止まって一覧から消えないときのために、64tick（寿命の2倍）で自分で取り除く。
         */
        @Override public void tick(Minecraft client) {
            frame(client.level);
            if (!gone && (++ticks >= 64 || !ClientRailgunHand.hasExternal(burst))) stop();
        }
        @Override public boolean finished() { return gone; }
    }

    // ---- 帳簿を進める・描く ----------------------------------------------------------------------

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        currentWorld();
        var client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;
        if (client.getOverlay() != null) { cancelAll(); return; }
        if (client.isPaused()) return;
        BOOK.advance();
        for (var effect : BOOK.all()) effect.tick(client);
        BOOK.retire(Effect::finished);
    }

    /** 自然能力の演出（普通の優先度）の後に描く。 */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void render(RenderLevelStageEvent event) {
        var stage = event.getStage();
        // フレームの最初の段階で計数を戻す（AFTER_SKY は、どの描く段階よりも前）。
        if (stage == RenderLevelStageEvent.Stage.AFTER_SKY) {
            lastFrameQuads = lastFrameBatches = 0; lastFrameNanos = 0;
            var level = Minecraft.getInstance().level;
            if (level != null && level == world) for (var effect : BOOK.all()) effect.frame(level);
            return;
        }
        if (stage != RenderLevelStageEvent.Stage.AFTER_PARTICLES && stage != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS && stage != ClientMdEffects.STAGE) return;
        var client = Minecraft.getInstance();
        if (client.level != world || client.player == null || client.getOverlay() != null) return;
        var effects = BOOK.all();
        if (effects.isEmpty()) return;
        long started = System.nanoTime();
        int quads = 0;
        for (var effect : effects) {
            // 実体を失ったなどで終わった演出は、次のtickで帳簿から外れるまでの間も描かない。
            if (effect.stage() != stage || effect.finished()) continue;
            int drawn = effect.render(event.getPoseStack(), event.getCamera(), event.getPartialTick(), client);
            quads += drawn;
            if (drawn > 0) lastFrameBatches++;
        }
        lastFrameQuads += quads; totalQuads += quads;
        lastFrameNanos += System.nanoTime() - started;
    }

    @Mod.EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ExternalEffectsReloadRegistration {
        @SubscribeEvent public static void registerExternalEffectsReload(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener) manager -> cancelAll());
        }
    }

    @SubscribeEvent public static void externalEffectsLogout(ClientPlayerNetworkEvent.LoggingOut event) { cancelAll(); world = null; }

    /** 試験用の入口: 球の、揺れを足す前の位置。球でなければnull。 */
    public static @Nullable Vec3 orbBaseForTest(String ownerModId, long effectId, float partial) {
        return BOOK.get(ownerModId, effectId) instanceof Orb orb ? orb.base(partial) : null;
    }

    /** 試験用の入口: 帳簿の数も含めて捨てる。 */
    public static void resetForTest() { BOOK.reset(); world = null; lastFrameQuads = lastFrameBatches = 0; lastFrameNanos = totalQuads = 0; }
}
