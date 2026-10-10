package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.visual.LegacyArcGeometry;
import io.github.pinchan4273.reacademycraft.visual.LegacyArcMesh;
import io.github.pinchan4273.reacademycraft.visual.LegacySurroundAnimation;
import io.github.pinchan4273.reacademycraft.world.entity.MagneticBlockEntity;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 原作MagManipEntityBlock.startClient: 持ち上げたブロックが見えるすべてのclientが、その周りにTHINの
 * EntitySurroundArc(block)をブロックの1.3倍の大きさで出し、ブロックがある間保つ。
 */
@Mod.EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class ClientMagneticBlockArcs {
    private record Template(double length, LegacyArcMesh.Mesh mesh) { }
    private static final List<Template> THIN = templates();
    private static final Map<MagneticBlockEntity, LegacySurroundAnimation> ARCS = new WeakHashMap<>();
    private static final Random RANDOM = new Random();
    private static ClientLevel world;
    private static long renderedQuads;
    private ClientMagneticBlockArcs() { }

    private static List<Template> templates() {
        var random = new Random(); var result = new ArrayList<Template>(10);
        for (int i = 0; i < 10; i++) {
            var pattern = LegacyArcGeometry.generate(LegacyArcGeometry.Profile.SURROUND_THIN, random);
            result.add(new Template(pattern.length(), LegacyArcMesh.bake(pattern, pattern.length(), random)));
        }
        return List.copyOf(result);
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var client = Minecraft.getInstance(); var level = client.level;
        if (level != world) { ARCS.clear(); world = level; }
        if (level == null || client.isPaused()) return;
        ARCS.keySet().removeIf(block -> block.isRemoved() || block.level() != level);
        for (var entity : level.entitiesForRendering())
            if (entity instanceof MagneticBlockEntity block && !block.isRemoved())
                ARCS.computeIfAbsent(block, ignored -> new LegacySurroundAnimation(LegacySurroundAnimation.Kind.THIN,
                        new Vec3(block.getBbWidth() * 1.3, block.getBbHeight() * 1.3, block.getBbWidth() * 1.3)));
        for (var animation : ARCS.values()) animation.tick(RANDOM);
    }

    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || ARCS.isEmpty()) return;
        var client = Minecraft.getInstance();
        if (client.level != world || client.getOverlay() != null) return;
        var camera = event.getCamera().getPosition(); var pose = event.getPoseStack(); float partial = event.getPartialTick();
        for (var entry : ARCS.entrySet()) {
            var block = entry.getKey();
            if (block.isRemoved()) continue;
            var position = block.getPosition(partial).subtract(camera);
            pose.pushPose();
            try {
                // 原作EntityPosは、ブロック自身のrotationYawに付いて回る。ブロックの回転はこの値を変えない。
                pose.translate(position.x, position.y, position.z);
                pose.mulPose(Axis.YP.rotationDegrees(-block.getYRot()));
                for (var frame : entry.getValue().snapshot()) {
                    if (frame.dead() || !frame.visible()) continue;
                    var template = THIN.get(frame.template());
                    pose.pushPose();
                    try {
                        pose.translate(frame.position().x, frame.position().y, frame.position().z);
                        pose.mulPose(Axis.ZP.rotationDegrees((float) frame.rotZ()));
                        pose.mulPose(Axis.YP.rotationDegrees((float) frame.rotY()));
                        pose.mulPose(Axis.XP.rotationDegrees((float) frame.rotX()));
                        pose.scale(.3f, .3f, .3f); pose.translate(-template.length / 2, 0, 0);
                        LegacyArcRenderer.draw(pose, template.mesh, 1);
                        renderedQuads += template.mesh.quads().size();
                    } finally { pose.popPose(); }
                }
            } finally { pose.popPose(); }
        }
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { ARCS.clear(); world = null; }
    /** テスト用の、読み取り専用の観測点。 */
    public static int active() { return (int) ARCS.keySet().stream().filter(block -> !block.isRemoved()).count(); }
    public static long renderedQuads() { return renderedQuads; }
}
