package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.network.IntensifyBurstEffect;
import io.github.pinchan4273.reacademycraft.network.ChargingLoopLedger;
import io.github.pinchan4273.reacademycraft.visual.LegacyArcGeometry;
import io.github.pinchan4273.reacademycraft.visual.LegacyArcMesh;
import io.github.pinchan4273.reacademycraft.visual.LegacyIntensifyBurstAnimation;
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
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** サーバーが受理したBody Intensifyの完了時の、原作の短いTHINの弾け。音とゲームの処理は含まない。 */
@Mod.EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class ClientIntensifyBurst {
    private static final ChargingLoopLedger LEDGER = new ChargingLoopLedger();
    private static final Map<UUID, Effect> EFFECTS = new HashMap<>();
    private static ClientLevel world;
    private static long ticks, renderedQuads, acceptedCount;
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
        final long session;
        final LegacyIntensifyBurstAnimation animation = new LegacyIntensifyBurstAnimation();
        final Random random = new Random();
        int age;
        Effect(Player source, long session) { this.source=source; this.session=session; }
        boolean valid() {
            return source.isAlive() && !source.isRemoved()
                    && source.level() == Minecraft.getInstance().level && source.level().getEntity(source.getId()) == source;
        }
    }
    private ClientIntensifyBurst() { }
    private static void currentWorld() {
        var level = Minecraft.getInstance().level;
        if (world != level) { reset(); world = level; }
    }
    public static void receive(IntensifyBurstEffect packet) {
        var client = Minecraft.getInstance(); currentWorld();
        if (client.level == null || client.player == null || !client.level.dimension().location().equals(packet.dimension())) return;
        var entity = client.level.getEntity(packet.entityId());
        if (!(entity instanceof Player source) || !source.getUUID().equals(packet.player()) || !source.isAlive()
                || source.isRemoved()) return;
        var decision = LEDGER.accept(packet.grant(), ticks);
        // 重複したパケットは、経過時間を戻したり、弧を作り直したり、短い弾けを延ばしたりしない。
        if (decision != ChargingLoopLedger.Decision.START) return;
        if (client.getOverlay() != null) { remove(packet.player()); return; }
        EFFECTS.put(packet.player(), new Effect(source, packet.session())); acceptedCount++;
    }
    private static void remove(UUID id) { EFFECTS.remove(id); LEDGER.cancel(id); }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        currentWorld(); var client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;
        if (client.getOverlay() != null) { cancelEffects(); return; }
        if (client.isPaused()) return; // 原作のentityの経過時間は、ワールドと一緒に止まる。
        ticks++;
        for (var id : LEDGER.expire(ticks)) remove(id);
        for (var id : List.copyOf(EFFECTS.keySet())) {
            var effect = EFFECTS.get(id);
            if (!effect.valid()) remove(id);
            else {
                effect.animation.update(++effect.age, effect.random);
                if (effect.animation.finished()) remove(id);
            }
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
                        pose.translate(frame.x(), frame.y(), frame.z());
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
    public static final class IntensifyBurstReloadRegistration {
        @SubscribeEvent public static void register(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener) manager -> { cancelEffects(); reloadGeneration++; });
        }
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { reset(); }
    /** resourceの再読込でも、同じワールドの終了の順序を保ち、遅れて届いたイベントで復活させない。 */
    private static void cancelEffects() {
        for (var id : List.copyOf(EFFECTS.keySet())) remove(id);
        lastFrameQuads=0;
    }
    public static void reset() { EFFECTS.clear(); LEDGER.clear(); world=null; ticks=0; renderedQuads=0; lastFrameQuads=0; acceptedCount=0; }
    public static boolean active(UUID player) { return EFFECTS.containsKey(player); }
    public static int activeEffects() { return EFFECTS.size(); }
    public static int reloadGeneration() { return reloadGeneration; }
    /** 読み取り専用の観測点。効果を開始したり延ばしたりしない。 */
    public static long acceptedCount() { return acceptedCount; }
    public static long renderedQuads() { return renderedQuads; }
    public static int lastFrameQuads() { return lastFrameQuads; }
    public static int age(UUID player) { var effect=EFFECTS.get(player);return effect==null?-1:effect.age; }
    public static long session(UUID player) { var effect=EFFECTS.get(player);return effect==null?0:effect.session; }
    public static List<LegacyIntensifyBurstAnimation.ArcFrame> frames(UUID player) {
        var effect=EFFECTS.get(player);return effect==null?List.of():effect.animation.snapshot();
    }
}
