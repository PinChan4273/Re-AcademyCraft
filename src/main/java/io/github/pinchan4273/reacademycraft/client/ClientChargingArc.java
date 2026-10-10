package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.network.ChargingArcLedger;
import io.github.pinchan4273.reacademycraft.network.ChargingArcTarget;
import io.github.pinchan4273.reacademycraft.network.ChargingLoopEffect;
import io.github.pinchan4273.reacademycraft.network.ChargingLoopLedger;
import io.github.pinchan4273.reacademycraft.visual.LegacyArcGeometry;
import io.github.pinchan4273.reacademycraft.visual.LegacyArcMesh;
import io.github.pinchan4273.reacademycraft.visual.LegacyChargingArcAnimation;
import io.github.pinchan4273.reacademycraft.visual.LegacyChargingArcPose;
import io.github.pinchan4273.reacademycraft.visual.LegacySurroundAnimation;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** 原作のブロック向けの主の弧と、NORMALの対象の囲み。サーバーが受理した判定だけで動く。 */
@Mod.EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class ClientChargingArc {
    private static final ChargingArcLedger LEDGER = new ChargingArcLedger();
    private static final Map<UUID, Effect> EFFECTS = new HashMap<>();
    private static ClientLevel world;
    private static long ticks, mainQuads, targetQuads;
    private static int lastMainQuads, lastTargetQuads, reloadGeneration;
    private record Template(double length, LegacyArcMesh.Mesh mesh) { }
    private static final class Templates {
        static final List<LegacyArcGeometry.Pattern> MAIN = main();
        static final List<Template> NORMAL = normal();
        private static List<LegacyArcGeometry.Pattern> main() {
            var random = new Random(); var result = new ArrayList<LegacyArcGeometry.Pattern>(20);
            for (int i = 0; i < 20; i++) result.add(LegacyArcGeometry.generate(LegacyArcGeometry.Profile.CHARGING, random));
            return List.copyOf(result);
        }
        private static List<Template> normal() {
            var random = new Random(); var result = new ArrayList<Template>(10);
            for (int i = 0; i < 10; i++) {
                var pattern = LegacyArcGeometry.generate(LegacyArcGeometry.Profile.SURROUND_NORMAL, random);
                result.add(new Template(pattern.length(), LegacyArcMesh.bake(pattern, pattern.length(), random)));
            }
            return List.copyOf(result);
        }
    }
    private static final class Effect {
        final Player source;
        final Random random = new Random();
        final LegacyChargingArcAnimation main = new LegacyChargingArcAnimation();
        final LegacySurroundAnimation surround = new LegacySurroundAnimation(LegacySurroundAnimation.Kind.NORMAL, new Vec3(1, 1, 1));
        LegacyArcMesh.Mesh mesh;
        int bakedTemplate = -1;
        double bakedLength = -1;
        Effect(Player source) { this.source = source; }
        boolean valid() {
            return source.isAlive() && !source.isRemoved()
                    && source.level() == Minecraft.getInstance().level && source.level().getEntity(source.getId()) == source;
        }
    }
    private ClientChargingArc() { }
    private static void currentWorld() {
        var level = Minecraft.getInstance().level;
        if (world != level) { reset(); world = level; }
    }
    public static void receive(ChargingLoopEffect packet) {
        currentWorld(); var client = Minecraft.getInstance();
        if (client.level == null || client.player == null || !client.level.dimension().location().equals(packet.dimension())) return;
        var decision = LEDGER.accept(packet, ticks);
        if (decision == ChargingLoopLedger.Decision.IGNORE) return;
        if (decision == ChargingLoopLedger.Decision.STOP || packet.itemMode()) { EFFECTS.remove(packet.player()); return; }
        var entity = client.level.getEntity(packet.entityId());
        if (!(entity instanceof Player source) || !source.getUUID().equals(packet.player()) || !source.isAlive()) {
            EFFECTS.remove(packet.player()); return;
        }
        if (source == client.player && !ClientChargingLoop.localHeld()) { LEDGER.cancel(packet.player()); EFFECTS.remove(packet.player()); return; }
        if (client.getOverlay() != null) { EFFECTS.remove(packet.player()); return; }
        var old = EFFECTS.get(packet.player());
        if (decision == ChargingLoopLedger.Decision.START || old == null || old.source != source) EFFECTS.put(packet.player(), new Effect(source));
    }
    public static void receive(ChargingArcTarget packet) {
        currentWorld(); var client = Minecraft.getInstance();
        if (client.level == null || !client.level.dimension().location().equals(packet.dimension())) return;
        var effect = EFFECTS.get(packet.player());
        if (effect == null || !effect.valid() || effect.source.getId() != packet.entityId()) return;
        LEDGER.accept(packet, ticks);
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        currentWorld(); var client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;
        if (client.isPaused()) { LEDGER.cancel(client.player.getUUID()); EFFECTS.remove(client.player.getUUID()); return; }
        ticks++; LEDGER.expire(ticks);
        for (var id : List.copyOf(EFFECTS.keySet())) {
            var effect = EFFECTS.get(id);
            if (!LEDGER.active(id, ticks) || !effect.valid() || client.getOverlay() != null) { EFFECTS.remove(id); continue; }
            if (effect.source == client.player && !ClientChargingLoop.localHeld()) { LEDGER.cancel(id); EFFECTS.remove(id); continue; }
            effect.main.tick(effect.random); effect.surround.tick(effect.random); // 対象の囲みが隠れている間も進める。
            var target = LEDGER.target(id, ticks);
            var orientation = orientation(effect.source.position(), target);
            if (orientation == null) { effect.mesh = null; effect.bakedTemplate = -1; continue; }
            var frame = effect.main.snapshot();
            if (effect.bakedTemplate != frame.template() || effect.bakedLength != orientation.length()) {
                effect.mesh = LegacyArcMesh.bake(Templates.MAIN.get(frame.template()), orientation.length(), effect.random);
                effect.bakedTemplate = frame.template(); effect.bakedLength = orientation.length();
            }
        }
    }
    private static LegacyChargingArcPose.Orientation orientation(Vec3 feet, ChargingArcTarget target) {
        if (target == null || !target.traced()) return null;
        // 原作ACRenderingHelperの定数1.6を、サーバーの目の高さの光線の判定とは独立に保つ。
        var delta = target.endpoint().subtract(feet.add(0, 1.6, 0));
        if (!Double.isFinite(delta.lengthSqr()) || delta.lengthSqr() > 32 * 32) return null;
        return LegacyChargingArcPose.orientation(delta.x, delta.y, delta.z);
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        lastMainQuads = 0; lastTargetQuads = 0;
        var client = Minecraft.getInstance();
        if (client.level != world || client.player == null || client.getOverlay() != null) return;
        var camera = event.getCamera().getPosition(); var pose = event.getPoseStack();
        for (var entry : EFFECTS.entrySet()) {
            var effect = entry.getValue(); if (!effect.valid()) continue;
            var target = LEDGER.target(entry.getKey(), ticks); var feet = effect.source.getPosition(event.getPartialTick());
            var orientation = orientation(feet, target); if (orientation == null) continue;
            if (effect.main.snapshot().visible() && effect.mesh != null) {
                var origin = feet.add(0, 1.6, 0).subtract(camera);
                var offset = LegacyChargingArcPose.handOffset(client.options.getCameraType().isFirstPerson(), effect.source == client.player);
                pose.pushPose();
                try {
                    pose.translate(origin.x, origin.y, origin.z);
                    pose.mulPose(Axis.YP.rotationDegrees(-(orientation.yawDegrees() + 90)));
                    pose.mulPose(Axis.ZP.rotationDegrees(-orientation.pitchDegrees()));
                    pose.translate(offset.x(), offset.y(), offset.z());
                    LegacyArcRenderer.draw(pose, effect.mesh, 1);
                    int count = effect.mesh.quads().size(); lastMainQuads += count; mainQuads += count;
                } finally { pose.popPose(); }
            }
            if (target.supportedBlock() == null) continue;
            var origin = Vec3.atBottomCenterOf(target.supportedBlock()).subtract(camera);
            pose.pushPose();
            try {
                pose.translate(origin.x, origin.y, origin.z);
                for (var frame : effect.surround.snapshot()) {
                    if (frame.dead() || !frame.visible()) continue;
                    var template = Templates.NORMAL.get(frame.template());
                    pose.pushPose();
                    try {
                        pose.translate(frame.position().x, frame.position().y, frame.position().z);
                        pose.mulPose(Axis.ZP.rotationDegrees((float) frame.rotZ()));
                        pose.mulPose(Axis.YP.rotationDegrees((float) frame.rotY()));
                        pose.mulPose(Axis.XP.rotationDegrees((float) frame.rotX()));
                        pose.scale(.3f, .3f, .3f); pose.translate(-template.length / 2, 0, 0);
                        LegacyArcRenderer.draw(pose, template.mesh, 1);
                        int count = template.mesh.quads().size(); lastTargetQuads += count; targetQuads += count;
                    } finally { pose.popPose(); }
                }
            } finally { pose.popPose(); }
        }
    }
    @Mod.EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ChargingArcReloadRegistration {
        @SubscribeEvent public static void register(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener) manager -> { reset(); reloadGeneration++; });
        }
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { reset(); }
    public static void reset() { EFFECTS.clear(); LEDGER.clear(); world = null; ticks = 0; mainQuads = 0; targetQuads = 0; lastMainQuads = 0; lastTargetQuads = 0; }
    public static int activeEffects() { return EFFECTS.size(); }
    public static boolean active(UUID source) { return EFFECTS.containsKey(source); }
    public static int reloadGeneration() { return reloadGeneration; }
    public static long mainQuads() { return mainQuads; }
    public static long targetQuads() { return targetQuads; }
    public static int lastMainQuads() { return lastMainQuads; }
    public static int lastTargetQuads() { return lastTargetQuads; }
    public static ChargingArcTarget target(UUID source) { return LEDGER.target(source, ticks); }
    public static List<LegacySurroundAnimation.Frame> frames(UUID source) { var effect = EFFECTS.get(source); return effect == null ? List.of() : effect.surround.snapshot(); }
}
