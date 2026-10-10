package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.network.ChargingLoopEffect;
import io.github.pinchan4273.reacademycraft.network.ChargingLoopLedger;
import io.github.pinchan4273.reacademycraft.visual.LegacyArcGeometry;
import io.github.pinchan4273.reacademycraft.visual.LegacyArcMesh;
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

/** 原作のアイテム向けのTHINの囲み。ブロック向けの主の弧と対象の囲みはClientChargingArc。 */
@Mod.EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class ClientChargingSurround {
    private static final ChargingLoopLedger LEDGER = new ChargingLoopLedger();
    private static final Map<UUID, Effect> EFFECTS = new HashMap<>();
    private static ClientLevel world;
    private static long ticks, renderedQuads;
    private static int lastFrameQuads;
    private static int reloadGeneration;
    private record Template(double length, LegacyArcMesh.Mesh mesh) { }
    private static final class Templates {
        static final List<Template> THIN = create();
        static List<Template> create() {
            var random = new Random(); var result = new ArrayList<Template>(10);
            for (int i = 0; i < 10; i++) {
                var pattern = LegacyArcGeometry.generate(LegacyArcGeometry.Profile.SURROUND_THIN, random);
                result.add(new Template(pattern.length(), LegacyArcMesh.bake(pattern, pattern.length(), random)));
            }
            return List.copyOf(result);
        }
    }
    private static final class Effect {
        final Player source;
        final LegacySurroundAnimation animation;
        final Random random = new Random();
        Effect(Player source, Vec3 size) {
            this.source = source; animation = new LegacySurroundAnimation(LegacySurroundAnimation.Kind.THIN, size);
        }
        boolean valid() {
            return source.isAlive() && !source.isRemoved()
                    && source.level() == Minecraft.getInstance().level && source.level().getEntity(source.getId()) == source;
        }
    }
    private ClientChargingSurround() { }
    private static void currentWorld() {
        var level = Minecraft.getInstance().level;
        if (world != level) { reset(); world = level; }
    }
    public static void receive(ChargingLoopEffect packet) {
        var client = Minecraft.getInstance(); currentWorld();
        if (client.level == null || client.player == null || !client.level.dimension().location().equals(packet.dimension())) return;
        var decision = LEDGER.accept(packet, ticks);
        if (decision == ChargingLoopLedger.Decision.IGNORE) return;
        if (decision == ChargingLoopLedger.Decision.STOP || !packet.itemMode()) { EFFECTS.remove(packet.player()); return; }
        var entity = client.level.getEntity(packet.entityId());
        if (!(entity instanceof Player source) || !source.getUUID().equals(packet.player()) || !source.isAlive()) {
            EFFECTS.remove(packet.player()); return;
        }
        if (source == client.player && !ClientChargingLoop.localHeld()) {
            LEDGER.cancel(packet.player()); EFFECTS.remove(packet.player()); return;
        }
        if (client.getOverlay() != null) { EFFECTS.remove(packet.player()); return; }
        var old = EFFECTS.get(packet.player());
        if (decision == ChargingLoopLedger.Decision.START || old == null || old.source != source) {
            double width = source.getBbWidth() * 1.3, height = source.getBbHeight() * 1.3;
            if (!Double.isFinite(width) || !Double.isFinite(height) || width <= 0 || height <= 0 || width > 32 || height > 32) {
                EFFECTS.remove(packet.player()); return;
            }
            EFFECTS.put(packet.player(), new Effect(source, new Vec3(width, height, width)));
        }
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        currentWorld(); var client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;
        if (client.isPaused()) {
            LEDGER.cancel(client.player.getUUID()); EFFECTS.remove(client.player.getUUID()); return;
        }
        ticks++;
        for (var id : LEDGER.expire(ticks)) EFFECTS.remove(id);
        for (var id : List.copyOf(EFFECTS.keySet())) {
            var effect = EFFECTS.get(id);
            if (!effect.valid() || client.getOverlay() != null) EFFECTS.remove(id);
            else if (effect.source == client.player && !ClientChargingLoop.localHeld()) {
                LEDGER.cancel(id); EFFECTS.remove(id);
            } else effect.animation.tick(effect.random);
        }
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        lastFrameQuads = 0;
        var client = Minecraft.getInstance();
        if (client.level != world || client.player == null || client.getOverlay() != null || EFFECTS.isEmpty()) return;
        var camera = event.getCamera().getPosition(); var pose = event.getPoseStack(); float partial = event.getPartialTick();
        for (var effect : EFFECTS.values()) {
            if (!effect.valid()) continue;
            var position = effect.source.getPosition(partial).subtract(camera);
            pose.pushPose();
            try {
                pose.translate(position.x, position.y, position.z);
                pose.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(partial, effect.source.yHeadRotO, effect.source.yHeadRot)));
                for (var frame : effect.animation.snapshot()) {
                    if (frame.dead() || !frame.visible()) continue;
                    var template = Templates.THIN.get(frame.template());
                    pose.pushPose();
                    try {
                        pose.translate(frame.position().x, frame.position().y, frame.position().z);
                        pose.mulPose(Axis.ZP.rotationDegrees((float) frame.rotZ()));
                        pose.mulPose(Axis.YP.rotationDegrees((float) frame.rotY()));
                        pose.mulPose(Axis.XP.rotationDegrees((float) frame.rotX()));
                        pose.scale(.3f, .3f, .3f); pose.translate(-template.length / 2, 0, 0);
                        LegacyArcRenderer.draw(pose, template.mesh, 1);
                        int count = template.mesh.quads().size(); lastFrameQuads += count; renderedQuads += count;
                    } finally { pose.popPose(); }
                }
            } finally { pose.popPose(); }
        }
    }
    @Mod.EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    // EventBus6は、入れ子のクラスの単純名からwrapperの名前を作る。package内で重ならない名前にしておく。
    public static final class ChargingSurroundReloadRegistration {
        @SubscribeEvent public static void register(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener) manager -> { reset(); reloadGeneration++; });
        }
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { reset(); }
    public static void reset() { EFFECTS.clear(); LEDGER.clear(); world = null; ticks = 0; renderedQuads = 0; lastFrameQuads = 0; }
    public static boolean active(UUID player) { return EFFECTS.containsKey(player); }
    public static int activeEffects() { return EFFECTS.size(); }
    public static int reloadGeneration() { return reloadGeneration; }
    /** 実際の描画の読み取り専用の観測点。効果を開始することは無い。 */
    public static long renderedQuads() { return renderedQuads; }
    public static int lastFrameQuads() { return lastFrameQuads; }
    public static List<LegacySurroundAnimation.Frame> frames(UUID player) {
        var effect = EFFECTS.get(player); return effect == null ? List.of() : effect.animation.snapshot();
    }
}
