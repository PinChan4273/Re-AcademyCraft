package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.network.LightShieldState;
import io.github.pinchan4273.reacademycraft.skill.LightShieldGeometry;
import io.github.pinchan4273.reacademycraft.world.AcademyParticles;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

/**
 * Light Shieldの盾を、術者と追跡している各clientで描く（原作EntityMdShieldとRenderMdShield）。工程2から、盾の向きと大きさは
 * サーバーが送る盾面の状態（{@link LightShieldState}）そのもので、clientは自分では標的も向きも選ばない。置く位置は、術者の今の
 * （補間した）足元から{@link LightShieldGeometry}の式で計算するので、サーバーの判定と同じ面になる。
 * <p>
 * 見た目は1.2.0のまま: mdshield.pngの正方形（一辺はサーバーの半径の2倍。起動から15tickで0.2倍から1倍へ育つのも、サーバーの値に
 * 含まれる）、6tickのフェードイン、自身の軸の周りのlerp(0.8, 2, ticks / 30)の回転、30%のtickで盾の中心から半ブロック以内のmdの粒子。
 * 向きは、受け取った前後2つの法線の間をtickの間で補間する。
 * <p>
 * 古い状態は捨てる: 同じtokenの古いsequence、終わったtoken（最近の{@link #ENDED_MEMORY}件）、今のdimensionと違う状態。
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class ClientLightShieldEffect {
    static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("academy", "textures/effects/mdshield.png");
    private static final int ENDED_MEMORY = 64;
    static final class Shield {
        final int entityId; int ticks; float rotation; double lastRender; int sequence;
        /** 受け取った最新の法線・半径（target）と、描画の補間に使う前のtickと今のtickの値。 */
        Vec3 target; float targetRadius;
        @Nullable Vec3 shown, previous; float shownRadius, previousRadius;
        Shield(int entityId) { this.entityId = entityId; }
    }
    private static final Map<Long, Shield> SHIELDS = new LinkedHashMap<>();
    private static final Set<Long> ENDED = new LinkedHashSet<>();
    private static final RandomSource RANDOM = RandomSource.create();
    @Nullable private static ClientLevel world;
    private static long drawn, stale;
    private ClientLightShieldEffect() { }

    public static void receive(LightShieldState packet) {
        var level = Minecraft.getInstance().level;
        if (level != world) { clear(); world = level; }
        if (!packet.alive()) {
            SHIELDS.remove(packet.token());
            ENDED.add(packet.token());
            if (ENDED.size() > ENDED_MEMORY) ENDED.remove(ENDED.iterator().next());
            return;
        }
        if (level == null || !level.dimension().location().equals(packet.dimension()) || ENDED.contains(packet.token())) { stale++; return; }
        var shield = SHIELDS.get(packet.token());
        if (shield == null) { shield = new Shield(packet.entityId()); shield.sequence = Integer.MIN_VALUE; SHIELDS.put(packet.token(), shield); }
        if (packet.sequence() <= shield.sequence) { stale++; return; }
        shield.sequence = packet.sequence();
        shield.target = new Vec3(packet.nx(), packet.ny(), packet.nz());
        shield.targetRadius = packet.radius();
        if (shield.shown == null) { shield.shown = shield.previous = shield.target; shield.shownRadius = shield.previousRadius = shield.targetRadius; }
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var client = Minecraft.getInstance(); var level = client.level;
        if (level != world) { clear(); world = level; return; }
        if (level == null || client.isPaused()) return;
        SHIELDS.values().removeIf(shield -> {
            var entity = level.getEntity(shield.entityId);
            if (entity == null || entity.isRemoved()) return true;
            shield.ticks++;
            shield.previous = shield.shown; shield.previousRadius = shield.shownRadius;
            shield.shown = shield.target; shield.shownRadius = shield.targetRadius;
            // 原作c_update: 30%のtickで、盾の中心の周りにmdの粒子を出す（工程2から、視線の前でなく盾の中心）。
            if (shield.shown != null && RANDOM.nextFloat() < .3f) {
                var at = LightShieldGeometry.center(entity.position(), shield.shown);
                level.addParticle(AcademyParticles.MD.get(), at.x + range(-.5, .5), at.y + range(-.5, .5), at.z + range(-.5, .5),
                        range(-.02, .02), range(-.01, .05), range(-.02, .02));
            }
            return false;
        });
    }
    static double range(double min, double max) { return min + RANDOM.nextDouble() * (max - min); }

    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || SHIELDS.isEmpty()) return;
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        var camera = event.getCamera().getPosition(); var pose = event.getPoseStack();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double time = Util.getMillis() / 1000.0;
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, TEXTURE);
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); RenderSystem.disableCull();
        try {
            for (var shield : SHIELDS.values()) {
                if (shield.shown == null) continue;
                var entity = level.getEntity(shield.entityId);
                if (entity == null) continue;
                // 原作: 回転は、速さに前のフレームからの秒数を掛けて進める。
                double dt = shield.lastRender == 0 ? 0 : time - shield.lastRender;
                float speed = Mth.lerp(Math.min(shield.ticks / 30f, 1f), .8f, 2f);
                shield.rotation += speed * dt;
                if (shield.rotation >= 360) shield.rotation -= 360;
                shield.lastRender = time;
                Vec3 normal = LightShieldGeometry.unit(shield.previous.lerp(shield.shown, partial), shield.shown);
                var at = LightShieldGeometry.center(entity.getPosition(partial), normal).subtract(camera);
                float size = 2 * Mth.lerp(partial, shield.previousRadius, shield.shownRadius);
                float alpha = Math.min(shield.ticks / 6f, 1f);
                if (alpha <= 0 || size <= 0) continue;
                pose.pushPose();
                pose.translate(at.x, at.y, at.z);
                pose.mulPose(Axis.YP.rotationDegrees(-LightShieldGeometry.yaw(normal)));
                pose.mulPose(Axis.XP.rotationDegrees(LightShieldGeometry.pitch(normal)));
                pose.mulPose(Axis.ZP.rotationDegrees(shield.rotation));
                pose.scale(size, size, 1);
                var m = pose.last().pose();
                var buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
                buffer.addVertex(m, -.5f, -.5f, 0).setUv(0, 0).setColor(1, 1, 1, alpha);
                buffer.addVertex(m, .5f, -.5f, 0).setUv(1, 0).setColor(1, 1, 1, alpha);
                buffer.addVertex(m, .5f, .5f, 0).setUv(1, 1).setColor(1, 1, 1, alpha);
                buffer.addVertex(m, -.5f, .5f, 0).setUv(0, 1).setColor(1, 1, 1, alpha);
                ClientDraw.drawWithShader(buffer);
                pose.popPose();
                drawn++;
            }
        } finally { RenderSystem.enableCull(); }
    }
    private static void clear() { SHIELDS.clear(); ENDED.clear(); }
    @SubscribeEvent public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) { clear(); world = null; }
    /** テスト用の入口。 */
    public static long drawn() { return drawn; }
    public static long stale() { return stale; }
    public static boolean showing(int entityId) { return SHIELDS.values().stream().anyMatch(s -> s.entityId == entityId); }
    /** この術者の盾の今の法線（受け取った最新）。無ければnull。 */
    public static @Nullable Vec3 normal(int entityId) {
        return SHIELDS.values().stream().filter(s -> s.entityId == entityId).map(s -> s.target).findFirst().orElse(null);
    }
}
