package io.github.pinchan4273.reacademycraft.client;

import com.mojang.math.Axis;
import io.github.pinchan4273.reacademycraft.api.client.BeamRequest;
import io.github.pinchan4273.reacademycraft.api.client.BeamResult;
import io.github.pinchan4273.reacademycraft.api.client.ExternalPresentation;
import io.github.pinchan4273.reacademycraft.visual.ExternalBeamBook;
import io.github.pinchan4273.reacademycraft.visual.ExternalBeamRules;
import io.github.pinchan4273.reacademycraft.visual.LegacyChargingArcPose;
import io.github.pinchan4273.reacademycraft.visual.LegacyMdRayAnimation;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
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
 * 外部の演出の光線（公開API{@link ExternalPresentation}の中身。演出API版1、Issue #5 AAA-RE-VFX-20261025-001）。
 * 自然能力の{@link ClientMdEffects}の光線・球の一覧とは別の帳簿（{@link ExternalBeamBook}、持ち主ごと）で持ち、同じ描画の部品で、
 * 同じ段階（{@code AFTER_BLOCK_ENTITIES}）の、自然能力の光線の後（イベントの優先度を最も低くする）に描く。自然能力のコードは変えない。
 * 寿命と終わり方は自然能力のMeltdownerの光線と同じ（50tick、一時停止の間は進まない、ログアウト・世界の切り替え・資源の読み直し・
 * オーバーレイの最中に終える）。術者がいなくなっても、受け付けた光線は寿命まで描く。
 */
@Mod.EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class ClientExternalBeams {
    private ClientExternalBeams() { }

    /** 外部の光線の最大の長さ（{@link ExternalBeamRules#MAX_LENGTH}）。 */
    public static final double MAX_LENGTH = ExternalBeamRules.MAX_LENGTH;
    /** 持ち主ごとの上限の既定（既定の32は、クライアントごと・持ち主のMOD IDごとの同時32本として採用した値（2026-10-25のユーザーの決定、Issue #5 6399564392）。）と、設定で選べる範囲。 */
    public static final int DEFAULT_LIMIT = 32, MIN_LIMIT = 1, MAX_LIMIT = 256;

    private static final class Beam {
        final BeamRequest request; final LegacyMdRayAnimation animation;
        final LegacyChargingArcPose.Orientation orientation;
        final RandomSource random = RandomSource.create();
        final float[][] scatter;
        final java.util.List<ClientMdEffects.SubArc> arcs;
        boolean small() { return request.style() == io.github.pinchan4273.reacademycraft.api.client.BeamStyle.MELTDOWNER_RAY_SMALL; }
        /** 自然能力と同じく、始点を術者の手元へずらす種類。 */
        boolean handFixed() {
            return switch (request.style()) { case MELTDOWNER_BEAM, RAILGUN, BARRAGE_AIM -> true; case MELTDOWNER_RAY_SMALL, BARRAGE_SCATTER -> false; };
        }
        static LegacyMdRayAnimation.Profile profile(io.github.pinchan4273.reacademycraft.api.client.BeamStyle style) {
            return switch (style) {
                case MELTDOWNER_BEAM -> LegacyMdRayAnimation.Profile.MELTDOWNER;
                case MELTDOWNER_RAY_SMALL -> LegacyMdRayAnimation.Profile.SMALL;
                case RAILGUN -> LegacyMdRayAnimation.Profile.RAILGUN;
                case BARRAGE_AIM -> LegacyMdRayAnimation.Profile.BARRAGE_PRE;
                case BARRAGE_SCATTER -> LegacyMdRayAnimation.Profile.BARRAGE;
            };
        }
        Beam(BeamRequest request) {
            this.request = request;
            var delta = request.to().subtract(request.from());
            orientation = LegacyChargingArcPose.rayOrientation(delta.x, delta.y, delta.z);
            animation = new LegacyMdRayAnimation(profile(request.style()), delta.length(), MAX_LENGTH);
            if (request.style() == io.github.pinchan4273.reacademycraft.api.client.BeamStyle.BARRAGE_SCATTER) {
                float range = 50 + random.nextFloat() * 10;
                scatter = new float[25 + random.nextInt(5)][];
                for (int i = 0; i < scatter.length; i++)
                    scatter[i] = new float[] {-range + random.nextFloat() * 2 * range, -range / 2 + random.nextFloat() * range};
            } else scatter = new float[0][];
            arcs = request.style() == io.github.pinchan4273.reacademycraft.api.client.BeamStyle.RAILGUN
                    ? ClientMdEffects.railgunArcs(delta.length(), random) : new java.util.ArrayList<>();
        }
    }
    private static final ExternalBeamBook<Beam> BOOK = new ExternalBeamBook<>();
    private static ClientLevel world;
    private static int lastFrameQuads, lastFrameBatches;
    private static long lastFrameNanos, totalQuads;

    public static int limit() { return ClientSettings.getInt(ClientSettings.EXTERNAL_BEAMS_PER_OWNER, DEFAULT_LIMIT, MIN_LIMIT, MAX_LIMIT); }

    private static void onMainThread() {
        if (!Minecraft.getInstance().isSameThread()) throw new IllegalStateException("ExternalPresentation must be called on the client main thread");
    }
    private static void currentWorld() {
        var level = Minecraft.getInstance().level;
        if (world != level) { BOOK.cancelAll(); world = level; }
    }

    public static BeamResult beam(BeamRequest request) {
        onMainThread();
        currentWorld();
        var client = Minecraft.getInstance();
        String owner = request == null || request.ownerModId() == null ? "" : request.ownerModId();
        if (client.level == null || client.player == null || client.getOverlay() != null) { BOOK.refuse(owner, BeamResult.NOT_READY); return BeamResult.NOT_READY; }
        var refused = ExternalBeamRules.check(request, client.level.dimension().location(), id -> ModList.get().isLoaded(id));
        if (refused != null) { BOOK.refuse(owner, refused); return refused; }
        return BOOK.offer(owner, request.effectId(), limit(), () -> new Beam(request));
    }
    public static boolean end(String ownerModId, long effectId) { onMainThread(); return BOOK.end(ownerModId, effectId); }
    public static int clear(String ownerModId) { onMainThread(); return BOOK.clear(ownerModId); }
    public static int active(String ownerModId) { return BOOK.active(ownerModId); }
    public static ExternalPresentation.Diagnostics diagnostics(String ownerModId) {
        return new ExternalPresentation.Diagnostics(BOOK.active(ownerModId), BOOK.count(ownerModId, BeamResult.ACCEPTED),
                BOOK.count(ownerModId, BeamResult.DUPLICATE), BOOK.count(ownerModId, BeamResult.FULL), BOOK.count(ownerModId, BeamResult.UNSUPPORTED),
                BOOK.count(ownerModId, BeamResult.INVALID), BOOK.count(ownerModId, BeamResult.NOT_READY),
                BOOK.totalActive(), lastFrameQuads, lastFrameBatches, lastFrameNanos, totalQuads);
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        currentWorld();
        var client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;
        if (client.getOverlay() != null) { BOOK.cancelAll(); return; }
        if (client.isPaused()) return;
        BOOK.advance();
        for (var beam : BOOK.all()) {
            beam.animation.tick(beam.random);
            // 自然能力のレールガンと同じ: 光線が30tick経つと弧を消す。
            if (!beam.arcs.isEmpty()) {
                if (beam.animation.elapsedTicks() >= 30) beam.arcs.clear();
                else { for (var arc : beam.arcs) arc.tick(beam.random); beam.arcs.removeIf(arc -> arc.dead); }
            }
            // 自然能力の細い光線と同じ（原作EntityMdRaySmall.onUpdate）: 光線に沿って、1tickに1つmdの粒子を出す。
            if (beam.small() && !beam.animation.snapshot().finished()) {
                var delta = beam.request.to().subtract(beam.request.from());
                var at = beam.request.from().add(delta.normalize().scale(beam.random.nextDouble() * Math.min(10, delta.length())));
                client.level.addParticle(io.github.pinchan4273.reacademycraft.world.AcademyParticles.MD.get(), at.x, at.y, at.z,
                        wiggle(beam.random), wiggle(beam.random), wiggle(beam.random));
            }
        }
        BOOK.retire(beam -> beam.animation.snapshot().finished());
    }

    private static double wiggle(RandomSource random) { return -.015 + random.nextDouble() * .03; }

    /** 術者（番号とUUIDの両方が合う、この世界の生きたプレイヤー）。いなければnull。 */
    private static @Nullable Player caster(BeamRequest request) {
        var client = Minecraft.getInstance();
        if (request.casterId() == null || request.casterEntityId() < 0 || client.level == null) return null;
        var entity = client.level.getEntity(request.casterEntityId());
        return entity instanceof Player player && player.getUUID().equals(request.casterId()) && player.isAlive() ? player : null;
    }

    /** 自然能力の光線（{@link ClientMdEffects#render}、普通の優先度）の後に描く。 */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != ClientMdEffects.STAGE) return;
        lastFrameQuads = lastFrameBatches = 0; lastFrameNanos = 0;
        var client = Minecraft.getInstance();
        if (client.level != world || client.player == null || client.getOverlay() != null) return;
        var beams = BOOK.all();
        if (beams.isEmpty()) return;
        long started = System.nanoTime();
        var camera = event.getCamera().getPosition();
        var pose = event.getPoseStack();
        for (var beam : beams) {
            var origin = beam.request.from();
            var orientation = beam.orientation;
            var caster = beam.handFixed() ? caster(beam.request) : null;
            if (caster != null) {
                // 自然能力のMeltdownerと同じく、始点を撃った者の手元へずらす（自分なら一人称・三人称に合わせる）。術者がいなければずらさない。
                var hand = LegacyChargingArcPose.handOffset(client.options.getCameraType().isFirstPerson(), caster == client.player);
                var shift = LegacyChargingArcPose.rayStartShift(hand, beam.orientation.yawDegrees());
                origin = origin.add(shift[0], shift[1], shift[2]);
                var delta = beam.request.to().subtract(origin);
                orientation = LegacyChargingArcPose.rayOrientation(delta.x, delta.y, delta.z);
            }
            var sample = beam.animation.snapshot();
            var clipped = new LegacyMdRayAnimation.Snapshot(Math.min(sample.length(), orientation.length()), sample.width(), sample.alpha(),
                    sample.glow(), sample.finished());
            var profile = beam.animation.profile();
            var at = origin.subtract(camera);
            pose.pushPose();
            try {
                pose.translate(at.x, at.y, at.z);
                if (beam.scatter.length > 0) {
                    lastFrameQuads += ClientMdEffects.drawExternalSubrays(pose, clipped, profile, orientation.yawDegrees(), orientation.pitchDegrees(), beam.scatter, origin.subtract(camera));
                    lastFrameBatches += beam.scatter.length * 3;
                    continue;
                }
                var direction = beam.request.to().subtract(origin).normalize();
                var broadside = direction.cross(origin.subtract(camera));
                if (broadside.lengthSqr() > 1e-6) {
                    broadside = broadside.normalize();
                    int glow = LegacyMdRenderer.drawBeamGlow(pose, clipped, profile,
                            new org.joml.Vector3f((float) direction.x, (float) direction.y, (float) direction.z),
                            new org.joml.Vector3f((float) broadside.x, (float) broadside.y, (float) broadside.z));
                    lastFrameQuads += glow; lastFrameBatches += glow;
                }
                pose.mulPose(Axis.YP.rotationDegrees(-(orientation.yawDegrees() + 90)));
                pose.mulPose(Axis.ZP.rotationDegrees(-orientation.pitchDegrees()));
                int drawn = LegacyMdRenderer.drawBeam(pose, clipped, profile, MAX_LENGTH);
                lastFrameQuads += drawn + ClientMdEffects.drawSubArcs(pose, beam.arcs);
                // 円柱は内と外の2本で、それぞれ1回確定する。
                if (drawn > 0) lastFrameBatches += 2;
            } finally { pose.popPose(); }
        }
        totalQuads += lastFrameQuads;
        lastFrameNanos = System.nanoTime() - started;
    }

    @Mod.EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ExternalBeamReloadRegistration {
        @SubscribeEvent public static void registerExternalBeamReload(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener) manager -> BOOK.cancelAll());
        }
    }

    @SubscribeEvent public static void externalBeamLogout(ClientPlayerNetworkEvent.LoggingOut event) { BOOK.cancelAll(); world = null; }

    /** 試験用の入口: 帳簿の数も含めて捨てる。 */
    public static void resetForTest() { BOOK.reset(); world = null; lastFrameQuads = lastFrameBatches = 0; lastFrameNanos = totalQuads = 0; }
    /** 試験用の入口: 持ち主の最新の光線のアニメーションの値（撮影の前に、伸び切るまで待つため）。 */
    public static @Nullable LegacyMdRayAnimation.Snapshot newest(String ownerModId) {
        var all = BOOK.all();
        for (int i = all.size() - 1; i >= 0; i--) if (all.get(i).request.ownerModId().equals(ownerModId)) return all.get(i).animation.snapshot();
        return null;
    }
    /** 試験用の入口: 描いているすべての外部の光線の長さ（足した順）。 */
    public static java.util.List<Double> lengths() {
        return BOOK.all().stream().map(beam -> beam.request.from().distanceTo(beam.request.to())).toList();
    }
}
