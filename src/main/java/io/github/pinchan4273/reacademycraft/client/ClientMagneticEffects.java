package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.network.SkillVisual;
import io.github.pinchan4273.reacademycraft.network.SkillVisualMove;
import io.github.pinchan4273.reacademycraft.skill.MagneticManipulation;
import io.github.pinchan4273.reacademycraft.skill.MagneticMovement;
import io.github.pinchan4273.reacademycraft.visual.LegacyArcGeometry;
import io.github.pinchan4273.reacademycraft.visual.LegacyArcMesh;
import io.github.pinchan4273.reacademycraft.visual.LegacyChargingArcAnimation;
import io.github.pinchan4273.reacademycraft.visual.LegacyChargingArcPose;
import io.github.pinchan4273.reacademycraft.world.AcademySounds;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
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
 * 原作の電撃使いの演出のうち、術者の近くのすべてのclientでcontextに付いて回るもの。
 * - Magnetic Movement（MovementContextC）: 術者から引き寄せる先までの、ArcPatterns.thinContiniousArcの
 *   EntityArc（長さは固定でない。揺れはtexWiggle 1、showWiggle 0.1、hideWiggle 0.6）と、術者に付いて回る
 *   em.move_loop（FollowEntitySound、ループ）。どちらもcontextと一緒に終わる。
 * - Magnetic Manipulation（MagManipContextC）: ブロックを持っている間、術者に付いて回るem.lf_loop。
 * 弧は、Current Chargingの主の弧と同じく、足元から1.6上から対象へ向け、一人称では手元へずらして描く
 * （どちらもプレイヤーから出るEntityArc）。
 */
@Mod.EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class ClientMagneticEffects {
    private static final class Templates {
        static final List<LegacyArcGeometry.Pattern> THIN = thin();
        private static List<LegacyArcGeometry.Pattern> thin() {
            var random = new Random(); var result = new ArrayList<LegacyArcGeometry.Pattern>(LegacyChargingArcAnimation.TEMPLATES);
            for (int i = 0; i < LegacyChargingArcAnimation.TEMPLATES; i++) result.add(LegacyArcGeometry.generate(LegacyArcGeometry.Profile.THIN_CONTINUOUS, random));
            return List.copyOf(result);
        }
    }
    private static final class Movement {
        final int entityId;
        final Random random = new Random();
        final LegacyChargingArcAnimation animation = new LegacyChargingArcAnimation(1, .1, .6);
        @Nullable Vec3 target;
        @Nullable LegacyArcMesh.Mesh mesh;
        int bakedTemplate = -1; double bakedLength = -1;
        Follow sound;
        Movement(int entityId) { this.entityId = entityId; }
    }
    private static final Map<Long, Movement> MOVEMENTS = new LinkedHashMap<>();
    private static final Map<Long, Follow> HOLDS = new LinkedHashMap<>();
    @Nullable private static ClientLevel world;
    private static long drawnQuads;
    private ClientMagneticEffects() { }

    /** 原作FollowEntitySound: 止められるまでentityの位置で鳴り続けるループ音。 */
    static final class Follow extends AbstractTickableSoundInstance {
        private final Entity entity;
        Follow(SoundEvent event, Entity entity) {
            super(event, SoundSource.AMBIENT, RandomSource.create());
            this.entity = entity; looping = true; delay = 0;
            x = entity.getX(); y = entity.getY(); z = entity.getZ();
        }
        void end() { stop(); }
        @Override public void tick() {
            if (entity.isRemoved()) { stop(); return; }
            x = entity.getX(); y = entity.getY(); z = entity.getZ();
        }
    }
    private static void clear() {
        MOVEMENTS.values().forEach(m -> { if (m.sound != null) m.sound.end(); });
        HOLDS.values().forEach(Follow::end);
        MOVEMENTS.clear(); HOLDS.clear();
    }
    public static void receive(SkillVisual packet) {
        var level = Minecraft.getInstance().level;
        if (level != world) { clear(); world = level; }
        if (level == null || !level.dimension().location().equals(packet.dimension())) return;
        if (!packet.starts()) {
            var movement = MOVEMENTS.remove(packet.token());
            if (movement != null && movement.sound != null) movement.sound.end();
            var hold = HOLDS.remove(packet.token());
            if (hold != null) hold.end();
            return;
        }
        var entity = level.getEntity(packet.entityId());
        if (entity == null) return;
        if (MagneticMovement.ID.equals(packet.kind())) {
            var movement = new Movement(packet.entityId());
            movement.sound = new Follow(AcademySounds.EM_MOVE_LOOP.get(), entity);
            Minecraft.getInstance().getSoundManager().play(movement.sound);
            MOVEMENTS.put(packet.token(), movement);
        } else if (MagneticManipulation.ID.equals(packet.kind())) {
            var hold = new Follow(AcademySounds.EM_LF_LOOP.get(), entity);
            Minecraft.getInstance().getSoundManager().play(hold);
            HOLDS.put(packet.token(), hold);
        }
    }
    /** 原作MSG_EFFECT_UPDATE: 弧が今どこまで届いているか。 */
    public static void move(SkillVisualMove packet) {
        var movement = MOVEMENTS.get(packet.token());
        if (movement != null) movement.target = packet.position();
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var client = Minecraft.getInstance();
        if (client.level != world) { clear(); world = client.level; }
        if (client.level == null || client.isPaused()) return;
        for (var movement : MOVEMENTS.values()) {
            movement.animation.tick(movement.random);
            var source = client.level.getEntity(movement.entityId);
            var orientation = source == null ? null : orientation(source.position(), movement.target);
            if (orientation == null) { movement.mesh = null; movement.bakedTemplate = -1; continue; }
            var frame = movement.animation.snapshot();
            if (movement.bakedTemplate != frame.template() || movement.bakedLength != orientation.length()) {
                movement.mesh = LegacyArcMesh.bake(Templates.THIN.get(frame.template()), orientation.length(), movement.random);
                movement.bakedTemplate = frame.template(); movement.bakedLength = orientation.length();
            }
        }
    }
    @Nullable private static LegacyChargingArcPose.Orientation orientation(Vec3 feet, @Nullable Vec3 target) {
        if (target == null) return null;
        var delta = target.subtract(feet.add(0, 1.6, 0));
        if (!Double.isFinite(delta.lengthSqr()) || delta.lengthSqr() < 1e-6 || delta.lengthSqr() > 64 * 64) return null;
        return LegacyChargingArcPose.orientation(delta.x, delta.y, delta.z);
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || MOVEMENTS.isEmpty()) return;
        var client = Minecraft.getInstance();
        if (client.level == null || client.player == null) return;
        var camera = event.getCamera().getPosition(); var pose = event.getPoseStack();
        for (var movement : MOVEMENTS.values()) {
            var source = client.level.getEntity(movement.entityId);
            if (source == null || movement.mesh == null || !movement.animation.snapshot().visible()) continue;
            var feet = source.getPosition(event.getPartialTick());
            var orientation = orientation(feet, movement.target);
            if (orientation == null) continue;
            var origin = feet.add(0, 1.6, 0).subtract(camera);
            var offset = LegacyChargingArcPose.handOffset(client.options.getCameraType().isFirstPerson(), source == client.player);
            pose.pushPose();
            try {
                pose.translate(origin.x, origin.y, origin.z);
                pose.mulPose(Axis.YP.rotationDegrees(-(orientation.yawDegrees() + 90)));
                pose.mulPose(Axis.ZP.rotationDegrees(-orientation.pitchDegrees()));
                pose.translate(offset.x(), offset.y(), offset.z());
                LegacyArcRenderer.draw(pose, movement.mesh, 1);
                drawnQuads += movement.mesh.quads().size();
            } finally { pose.popPose(); }
        }
    }
    @SubscribeEvent public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) { clear(); world = null; }

    // テスト用。
    public static long drawnQuads() { return drawnQuads; }
    public static int movements() { return MOVEMENTS.size(); }
    public static boolean moveLoopPlaying() {
        return MOVEMENTS.values().stream().anyMatch(m -> m.sound != null && Minecraft.getInstance().getSoundManager().isActive(m.sound));
    }
    public static boolean holdLoopPlaying() {
        return HOLDS.values().stream().anyMatch(h -> Minecraft.getInstance().getSoundManager().isActive(h));
    }
}
