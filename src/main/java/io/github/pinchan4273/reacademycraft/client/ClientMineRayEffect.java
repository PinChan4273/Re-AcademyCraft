package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.network.SkillVisual;
import io.github.pinchan4273.reacademycraft.network.SkillVisualMove;
import io.github.pinchan4273.reacademycraft.skill.MineRay;
import io.github.pinchan4273.reacademycraft.visual.LegacyChargingArcPose;
import io.github.pinchan4273.reacademycraft.visual.LegacyMdRayAnimation;
import io.github.pinchan4273.reacademycraft.world.AcademyParticles;
import com.mojang.math.Axis;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.ResourceLocation;
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
 * contextを持つすべてのclientでの原作MRContextC: EntityMineRayBasic・Expert・Luck。術者の目（Basic）または足元から1.55上
 * （Expert・Luck）から、視線の方向に15ブロック先まで、技能が続く間光線を保つ。光線ごとの幅と色で、RendererRayCompositeが描く。
 * 毎tick、時間の半分（Basic）または60%で、最初の10ブロックのどこかにmdの粒子を出す。また削っている間は毎tick、
 * 削っているブロックからmdの粒子を3つ落とす。光線は技能が終わると同時に消える（原作setDead、フェードなし）。
 */
@Mod.EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class ClientMineRayEffect {
    static final class Ray {
        final int entityId; final MineRay.Variant variant; final LegacyMdRayAnimation animation;
        Ray(int entityId, MineRay.Variant variant) {
            this.entityId = entityId; this.variant = variant;
            animation = new LegacyMdRayAnimation(profile(variant), 15);
        }
    }
    private static final Map<Long, Ray> RAYS = new LinkedHashMap<>();
    private static final RandomSource RANDOM = RandomSource.create();
    @Nullable private static ClientLevel world;
    private static long drawnQuads, particles;
    private ClientMineRayEffect() { }

    static LegacyMdRayAnimation.Profile profile(MineRay.Variant variant) {
        return variant == MineRay.BASIC ? LegacyMdRayAnimation.Profile.MINE_BASIC
                : variant == MineRay.EXPERT ? LegacyMdRayAnimation.Profile.MINE_EXPERT : LegacyMdRayAnimation.Profile.MINE_LUCK;
    }
    static boolean luck(Ray ray) { return ray.variant == MineRay.LUCK; }

    public static void receive(SkillVisual packet) {
        var level = Minecraft.getInstance().level;
        if (level != world) { RAYS.clear(); world = level; }
        if (level == null || !level.dimension().location().equals(packet.dimension())) return;
        if (!packet.starts()) { RAYS.remove(packet.token()); return; }
        var variant = MineRay.of(packet.kind());
        if (variant != null) RAYS.put(packet.token(), new Ray(packet.entityId(), variant));
    }
    /** 原作c_spawnParticles: rangei(2, 3)は常に2で、0から2まで（両端を含む）なので3つ。 */
    public static void move(SkillVisualMove packet) {
        var ray = RAYS.get(packet.token());
        var level = Minecraft.getInstance().level;
        if (ray == null || level == null) return;
        SimpleParticleType type = luck(ray) ? AcademyParticles.MD_LUCK_BLOCK.get() : AcademyParticles.MD_BLOCK.get();
        var at = packet.position();
        for (int i = 0; i <= 2; i++) {
            level.addParticle(type, at.x + range(-.2, 1.2), at.y + range(-.2, 1.2), at.z + range(-.2, 1.2),
                    range(-.06, .06), range(-.06, .06), range(-.06, .06));
            particles++;
        }
    }
    static double range(double min, double max) { return min + RANDOM.nextDouble() * (max - min); }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var client = Minecraft.getInstance(); var level = client.level;
        if (level != world) { RAYS.clear(); world = level; return; }
        if (level == null || client.isPaused()) return;
        RAYS.values().removeIf(ray -> {
            var entity = level.getEntity(ray.entityId);
            if (entity == null || entity.isRemoved()) return true;
            ray.animation.tick(RANDOM);
            // 原作onUpdate: 時間の半分（ExpertとLuckは60%）で、視線に沿ってmdの粒子を出す。
            if (RANDOM.nextDouble() < (ray.variant == MineRay.BASIC ? .5 : .6)) {
                var at = entity.getEyePosition().add(entity.getLookAngle().scale(range(0, 10)));
                level.addParticle(luck(ray) ? AcademyParticles.MD_LUCK.get() : AcademyParticles.MD.get(), at.x, at.y, at.z,
                        range(-.03, .03), range(-.03, .03), range(-.03, .03));
                particles++;
            }
            return false;
        });
    }

    /** 原作updatePos: 目（Basic）または足元から1.55上から、目から視線の方向に15先まで。 */
    static Vec3[] ends(Entity entity, MineRay.Variant variant, float partial) {
        var feet = entity.getPosition(partial);
        var eyes = entity.getEyePosition(partial);
        var from = variant == MineRay.BASIC ? eyes : feet.add(0, 1.55, 0);
        return new Vec3[] {from, eyes.add(entity.getViewVector(partial).scale(15))};
    }

    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || RAYS.isEmpty()) return;
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        var camera = event.getCamera().getPosition(); var pose = event.getPoseStack();
        float partial = event.getPartialTick();
        for (var ray : RAYS.values()) {
            var entity = level.getEntity(ray.entityId);
            if (entity == null) continue;
            var ends = ends(entity, ray.variant, partial);
            // 原作の採掘の光線はEntityRayBaseのviewOptimizeを保つ: 始点は手元へずらし、終点はそのまま。
            var client = Minecraft.getInstance();
            var hand = LegacyChargingArcPose.handOffset(client.options.getCameraType().isFirstPerson(), entity == client.player);
            var unshifted = ends[1].subtract(ends[0]);
            var shift = LegacyChargingArcPose.rayStartShift(hand, LegacyChargingArcPose.rayOrientation(unshifted.x, unshifted.y, unshifted.z).yawDegrees());
            ends[0] = ends[0].add(shift[0], shift[1], shift[2]);
            var delta = ends[1].subtract(ends[0]);
            // 充電の弧ではなく光線なので、弧のmeshの32ブロックの上限は当てはまらない。
            var orientation = LegacyChargingArcPose.rayOrientation(delta.x, delta.y, delta.z);
            var sample = ray.animation.snapshot();
            var clipped = new LegacyMdRayAnimation.Snapshot(Math.min(sample.length(), orientation.length()), sample.width(), sample.alpha(), sample.glow(), sample.finished());
            var profile = ray.animation.profile();
            var at = ends[0].subtract(camera);
            pose.pushPose();
            try {
                pose.translate(at.x, at.y, at.z);
                var direction = delta.normalize();
                var broadside = direction.cross(at);
                if (broadside.lengthSqr() > 1e-6) {
                    broadside = broadside.normalize();
                    drawnQuads += LegacyMdRenderer.drawBeamGlow(pose, clipped, profile,
                            new org.joml.Vector3f((float) direction.x, (float) direction.y, (float) direction.z),
                            new org.joml.Vector3f((float) broadside.x, (float) broadside.y, (float) broadside.z));
                }
                pose.mulPose(Axis.YP.rotationDegrees(-(orientation.yawDegrees() + 90)));
                pose.mulPose(Axis.ZP.rotationDegrees(-orientation.pitchDegrees()));
                drawnQuads += LegacyMdRenderer.drawBeam(pose, clipped, profile);
            } finally { pose.popPose(); }
        }
    }
    @SubscribeEvent public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) { RAYS.clear(); world = null; }
    /** テスト用の入口。 */
    public static long drawnQuads() { return drawnQuads; }
    public static long particles() { return particles; }
    public static boolean showing(int entityId) { return RAYS.values().stream().anyMatch(r -> r.entityId == entityId); }
    @Nullable public static ResourceLocation kindOf(int entityId) {
        return RAYS.values().stream().filter(r -> r.entityId == entityId).map(r -> r.variant.id()).findFirst().orElse(null);
    }
}
