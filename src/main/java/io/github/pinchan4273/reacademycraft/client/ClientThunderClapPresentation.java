package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.network.ThunderClapPresentationEffect;
import io.github.pinchan4273.reacademycraft.network.ThunderClapPresentationLedger;
import io.github.pinchan4273.reacademycraft.visual.LegacyArcGeometry;
import io.github.pinchan4273.reacademycraft.visual.LegacyArcMesh;
import io.github.pinchan4273.reacademycraft.visual.LegacyRippleAnimation;
import io.github.pinchan4273.reacademycraft.visual.LegacySurroundAnimation;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** サーバーが承認したThunder ClapのBOLDの囲みと、術者だけに見える対象の波紋。 */
@Mod.EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class ClientThunderClapPresentation {
    private static final ThunderClapPresentationLedger LEDGER = new ThunderClapPresentationLedger();
    private static final Map<UUID, Effect> EFFECTS = new HashMap<>();
    private static ClientLevel world;
    private static long ticks, acceptedCount, surroundQuads, rippleQuads;
    private static int lastSurroundQuads, lastRippleQuads, reloadGeneration;

    private record Template(double length, LegacyArcMesh.Mesh mesh) { }
    private static final class Templates {
        static final List<Template> BOLD = create();
        private static List<Template> create() {
            var random = new Random(); var result = new ArrayList<Template>(10);
            for (int i = 0; i < 10; i++) {
                var pattern = LegacyArcGeometry.generate(LegacyArcGeometry.Profile.SURROUND_BOLD, random);
                result.add(new Template(pattern.length(), LegacyArcMesh.bake(pattern, pattern.length(), random)));
            }
            return List.copyOf(result);
        }
    }
    private static final class Effect {
        final Player source; final LegacySurroundAnimation surround; final Random random = new Random();
        final long rippleStartMillis = Util.getMillis(); Vec3 target; boolean stopping; int stopTicks;
        Effect(Player source, Vec3 target) {
            this.source = source; this.target = target;
            double width = source.getBbWidth() * 1.3, height = source.getBbHeight() * 1.3;
            surround = new LegacySurroundAnimation(LegacySurroundAnimation.Kind.BOLD, new Vec3(width, height, width));
        }
        boolean valid() {
            return source.isAlive() && !source.isRemoved()
                    && source.level() == Minecraft.getInstance().level && source.level().getEntity(source.getId()) == source;
        }
        List<LegacyRippleAnimation.Ring> ripple() {
            return LegacyRippleAnimation.sample(Math.max(0, (Util.getMillis() - rippleStartMillis) / 1000.0));
        }
    }

    private ClientThunderClapPresentation() { }
    private static void currentWorld() {
        var level = Minecraft.getInstance().level;
        if (world != level) { reset(); world = level; }
    }
    public static void receive(ThunderClapPresentationEffect packet) {
        currentWorld(); var client = Minecraft.getInstance();
        if (client.level == null || client.player == null || !client.level.dimension().location().equals(packet.dimension())) return;
        Player source = null;
        if (packet.action() == ThunderClapPresentationEffect.Action.START) {
            var entity = client.level.getEntity(packet.entityId());
            if (!(entity instanceof Player player) || !player.getUUID().equals(packet.player())
                    || !player.isAlive()) return;
            double width = player.getBbWidth() * 1.3, height = player.getBbHeight() * 1.3;
            if (!Double.isFinite(width) || !Double.isFinite(height) || width <= 0 || height <= 0
                    || width > 32 || height > 32) return;
            source = player;
        }
        var decision = LEDGER.accept(packet, ticks);
        if (decision == ThunderClapPresentationLedger.Decision.IGNORE) return;
        if (decision == ThunderClapPresentationLedger.Decision.START) {
            if (client.getOverlay() != null) { LEDGER.cancel(packet.player()); return; }
            EFFECTS.put(packet.player(), new Effect(source, packet.target())); acceptedCount++;
        } else if (decision == ThunderClapPresentationLedger.Decision.UPDATE) {
            var effect = EFFECTS.get(packet.player()); if (effect != null && !effect.stopping) effect.target = packet.target();
        } else {
            var effect = EFFECTS.get(packet.player());
            if (effect != null) { effect.stopping = true; effect.stopTicks = 10; }
        }
    }
    private static void remove(UUID player) { EFFECTS.remove(player); LEDGER.cancel(player); }
    private static void cancelEffects() {
        for (var id : List.copyOf(EFFECTS.keySet())) remove(id);
        lastSurroundQuads = lastRippleQuads = 0;
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        currentWorld(); var client = Minecraft.getInstance();
        if (client.level == null || client.player == null) return;
        if (client.getOverlay() != null) { cancelEffects(); return; }
        if (client.isPaused()) return;
        ticks++;
        for (var id : LEDGER.expire(ticks)) remove(id);
        for (var id : List.copyOf(EFFECTS.keySet())) {
            var effect = EFFECTS.get(id);
            if (!effect.valid()) remove(id);
            else if (effect.stopping && --effect.stopTicks <= 0) remove(id);
            else effect.surround.tick(effect.random);
        }
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        lastSurroundQuads = lastRippleQuads = 0;
        var client = Minecraft.getInstance();
        if (client.level != world || client.player == null || client.getOverlay() != null) return;
        var camera = event.getCamera().getPosition(); var pose = event.getPoseStack(); float partial = event.getPartialTick();
        for (var effect : EFFECTS.values()) {
            if (!effect.valid()) continue;
            var position = effect.source.getPosition(partial).subtract(camera);
            pose.pushPose();
            try {
                pose.translate(position.x, position.y, position.z);
                pose.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(partial, effect.source.yHeadRotO, effect.source.yHeadRot)));
                for (var frame : effect.surround.snapshot()) {
                    if (frame.dead() || !frame.visible()) continue;
                    var template = Templates.BOLD.get(frame.template()); pose.pushPose();
                    try {
                        pose.translate(frame.position().x, frame.position().y, frame.position().z);
                        pose.mulPose(Axis.ZP.rotationDegrees((float) frame.rotZ()));
                        pose.mulPose(Axis.YP.rotationDegrees((float) frame.rotY()));
                        pose.mulPose(Axis.XP.rotationDegrees((float) frame.rotX()));
                        pose.scale(.3f, .3f, .3f); pose.translate(-template.length / 2, 0, 0);
                        LegacyArcRenderer.draw(pose, template.mesh, 1);
                        int quads = template.mesh.quads().size(); lastSurroundQuads += quads; surroundQuads += quads;
                    } finally { pose.popPose(); }
                }
            } finally { pose.popPose(); }
            if (effect.source == client.player && !effect.stopping) {
                var target = effect.target.subtract(camera); pose.pushPose();
                try {
                    pose.translate(target.x, target.y, target.z);
                    int quads = LegacyRippleRenderer.draw(pose, effect.ripple());
                    lastRippleQuads += quads; rippleQuads += quads;
                } finally { pose.popPose(); }
            }
        }
    }
    @Mod.EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ThunderClapPresentationReloadRegistration {
        @SubscribeEvent public static void register(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener) manager -> { cancelEffects(); reloadGeneration++; });
        }
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { reset(); }
    public static void reset() {
        EFFECTS.clear(); LEDGER.clear(); world = null; ticks = acceptedCount = surroundQuads = rippleQuads = 0;
        lastSurroundQuads = lastRippleQuads = 0;
    }
    public static boolean active(UUID player) { return EFFECTS.containsKey(player); }
    public static int activeEffects() { return EFFECTS.size(); }
    public static long acceptedCount() { return acceptedCount; }
    public static long surroundQuads() { return surroundQuads; }
    public static long rippleQuads() { return rippleQuads; }
    public static int lastSurroundQuads() { return lastSurroundQuads; }
    public static int lastRippleQuads() { return lastRippleQuads; }
    public static int reloadGeneration() { return reloadGeneration; }
    public static Vec3 target(UUID player) { var effect = EFFECTS.get(player); return effect == null ? null : effect.target; }
    public static boolean stopping(UUID player) { var effect = EFFECTS.get(player); return effect != null && effect.stopping; }
    public static List<LegacySurroundAnimation.Frame> frames(UUID player) {
        var effect = EFFECTS.get(player); return effect == null ? List.of() : effect.surround.snapshot();
    }
}
