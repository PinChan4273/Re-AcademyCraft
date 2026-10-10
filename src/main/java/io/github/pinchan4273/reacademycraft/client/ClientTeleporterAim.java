package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities;
import io.github.pinchan4273.reacademycraft.network.AimState;
import io.github.pinchan4273.reacademycraft.skill.FleshRipping;
import io.github.pinchan4273.reacademycraft.skill.Flashing;
import io.github.pinchan4273.reacademycraft.skill.MarkTeleport;
import io.github.pinchan4273.reacademycraft.skill.PenetrateTeleport;
import io.github.pinchan4273.reacademycraft.skill.PlayerAbilityData;
import io.github.pinchan4273.reacademycraft.skill.ShiftTeleport;
import io.github.pinchan4273.reacademycraft.skill.TPSkillHelper;
import io.github.pinchan4273.reacademycraft.skill.ThreateningTeleport;
import io.github.pinchan4273.reacademycraft.world.AcademyParticles;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * テレポートの狙いを、術者自身のclientで表示する（原作もそこだけで描いていた。isLocal）:
 * Penetrate Teleport・Mark Teleport・Flashingで術者が移る場所にEntityTPMarking（ちらつくtp_markの人型と、その周りを
 * 昇るTPの粒子）を、Threatening Teleport・Flesh Ripping・Shift Teleportの狙う相手にEntityMarkerの角の括弧を出す。
 * どれも、サーバーが技能が続いていると伝えている間、サーバーと同じ狙いの計算を毎tick行う。
 */
@Mod.EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class ClientTeleporterAim {
    /** 原作の色（RGBA）。 */
    static final int[] THREATENING_NORMAL = {0xba, 0xba, 0xba, 0xba}, THREATENING_TARGET = {0xba, 0xb2, 0x23, 0x2a},
            FLESH_NONE = {74, 74, 74, 160}, FLESH_TARGET = {185, 25, 25, 180},
            SHIFT_BLOCK = {139, 139, 139, 180}, SHIFT_TARGET = {235, 81, 81, 180};
    static final int MARK_FRAMES = 7;
    static final ResourceLocation[] MARK_TEXTURES = new ResourceLocation[MARK_FRAMES];
    static {
        for (int i = 0; i < MARK_FRAMES; i++)
            MARK_TEXTURES[i] = ResourceLocation.fromNamespaceAndPath("academy", "textures/effects/tp_mark/" + i + ".png");
    }

    /** 原作EntityMarker: ある位置（足元）の括弧の箱、または相手自身の箱の括弧。 */
    public record Marker(Vec3 position, float width, float height, int[] color, boolean ignoreDepth, @Nullable Entity target) {
        Vec3 at(float partialTick) { return target != null ? target.getPosition(partialTick) : position; }
        float w() { return target != null ? target.getBbWidth() : width; }
        float h() { return target != null ? target.getBbHeight() : height; }
    }
    /** 原作EntityTPMarking: 術者が着く場所。原作と同じく持ち上げて置く。 */
    public record Mark(Vec3 position, boolean available, float yaw, int age) { }

    private static final class Aim {
        final ResourceLocation skill; int ticks, refresh;
        /** Penetrate Teleportの距離（原作curDist）: ホイールで変えるまでは最大。 */
        double distance = Double.NaN;
        @Nullable Vec3 markPrevious; @Nullable Mark mark; int markAge;
        final List<Marker> markers = new ArrayList<>();
        final List<Marker> targets = new ArrayList<>();
        Aim(ResourceLocation skill) { this.skill = skill; }
    }
    private static final Map<Long, Aim> AIMS = new LinkedHashMap<>();
    @Nullable private static Aim flashing;
    @Nullable private static ClientLevel world;
    private static long particles, figures, bracketBoxes;

    private ClientTeleporterAim() { }

    public static void receive(AimState state) {
        if (Minecraft.getInstance().level != world) { AIMS.clear(); world = Minecraft.getInstance().level; }
        if (state.alive()) AIMS.put(state.token(), new Aim(state.skill()));
        else AIMS.remove(state.token());
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var client = Minecraft.getInstance();
        if (client.level != world) { AIMS.clear(); flashing = null; world = client.level; }
        var player = client.player;
        if (player == null || client.isPaused()) return;
        var data = player.getCapability(AbilityCapabilities.PLAYER_ABILITY).orElse(null);
        if (data == null) return;
        for (var aim : AIMS.values()) update(player, data, aim);
        // 原作Flashingは、方向キーを押している間に目印を出し、離すと消す。
        int direction = ClientFlashing.aiming();
        if (direction < 0) flashing = null;
        else {
            if (flashing == null) flashing = new Aim(Flashing.ID);
            var dest = Flashing.destination(player, direction, data.getProficiency(Flashing.ID));
            mark(player, flashing, dest, true);
        }
    }

    private static void update(Player player, PlayerAbilityData data, Aim aim) {
        float exp = data.getProficiency(aim.skill);
        aim.ticks++;
        aim.markers.clear();
        if (aim.skill.equals(ThreateningTeleport.ID)) {
            // 原作l_tick: 落とす位置。相手の高さの分だけ足元へ下げる。単独のときは幅0.5。
            var drop = ThreateningTeleport.aim(player, exp);
            var target = drop.target();
            aim.markers.add(target != null ? new Marker(target.position(), 0, 0, THREATENING_TARGET, false, target)
                    : new Marker(drop.position(), .5f, .5f, THREATENING_NORMAL, false, null));
        } else if (aim.skill.equals(FleshRipping.ID)) {
            // 原作l_updateEffect: 光線の終点に、幅1の灰色、または相手の1.2倍の赤。
            var trace = TPSkillHelper.traceLiving(player, FleshRipping.range(exp), TPSkillHelper::living);
            var target = trace.entity();
            aim.markers.add(target == null ? new Marker(trace.position(), 1, 1, FLESH_NONE, false, null)
                    : new Marker(trace.position(), target.getBbWidth() * 1.2f, target.getBbHeight() * 1.2f, FLESH_TARGET, false, null));
        } else if (aim.skill.equals(ShiftTeleport.ID)) {
            // 原作l_tick: 行き先のブロックの底の中心に、幅1.2で壁越しに見える目印。
            // 線上の相手は、3tickごとに付け直す。
            var shift = ShiftTeleport.aim(player, exp);
            var place = shift.place();
            aim.markers.add(new Marker(new Vec3(place.getX() + .5, place.getY(), place.getZ() + .5), 1.2f, 1.2f, SHIFT_BLOCK, true, null));
            if (++aim.refresh == 3) {
                aim.refresh = 0; aim.targets.clear();
                for (var e : ShiftTeleport.targets(player, shift)) aim.targets.add(new Marker(e.position(), .5f, .5f, SHIFT_TARGET, true, e));
            }
        } else if (aim.skill.equals(PenetrateTeleport.ID)) {
            // 原作updateEffect: 行き先を術者の目の高さだけ上げる。空きが無い所では赤。
            if (Double.isNaN(aim.distance)) aim.distance = PenetrateTeleport.maxDistance(exp);
            var dest = PenetrateTeleport.destination(player.level(), player.position(), player.getLookAngle(),
                    aim.distance, data.getCp(), exp);
            mark(player, aim, dest.position().add(0, player.getEyeHeight(), 0), dest.available());
        } else if (aim.skill.equals(MarkTeleport.ID)) {
            // 原作l_updateは、押している間に伸びる届く距離のために、1から自分でtickを数える。
            mark(player, aim, MarkTeleport.destination(player, data, aim.ticks), true);
        }
    }

    /**
     * 原作EntityTPMarking.onUpdate: 行き先が使える間だけ、40%のtickで、周り1ブロック以内・1.4下までに
     * 上へ漂うTPの粒子を出す。
     */
    private static void mark(Player player, Aim aim, Vec3 at, boolean available) {
        aim.markAge++;
        aim.markPrevious = aim.mark == null ? at : aim.mark.position();
        aim.mark = new Mark(at, available, player.getYRot(), aim.markAge);
        RandomSource r = player.getRandom();
        if (available && r.nextDouble() < .4) {
            player.level().addParticle(AcademyParticles.TP.get(),
                    at.x + range(r, -1, 1), at.y + range(r, .2, 1.6) - 1.6, at.z + range(r, -1, 1),
                    range(r, -.03, .03), range(r, 0, .05), range(r, -.03, .03));
            particles++;
        }
    }
    static double range(RandomSource r, double min, double max) { return min + r.nextDouble() * (max - min); }

    private static final class Types extends RenderType {
        private Types() { super("academy_unused_tp_aim", DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES, 256, false, false, () -> { }, () -> { }); }
        /** 原作RenderMarker: 3pxの線を、光の影響なしで、深度テストの有無を選んで描く。 */
        static RenderType lines(boolean ignoreDepth) {
            return create(ignoreDepth ? "academy_tp_marker_through" : "academy_tp_marker", DefaultVertexFormat.POSITION_COLOR_NORMAL,
                    VertexFormat.Mode.LINES, 256, false, false,
                    CompositeState.builder().setShaderState(RENDERTYPE_LINES_SHADER)
                            .setLineState(new LineStateShard(OptionalDouble.of(3)))
                            .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setCullState(NO_CULL)
                            .setDepthTestState(ignoreDepth ? NO_DEPTH_TEST : LEQUAL_DEPTH_TEST)
                            .setWriteMaskState(COLOR_WRITE).createCompositeState(false));
        }
        static final RenderType LINES = lines(false), LINES_THROUGH = lines(true);
        /**
         * ShaderSimpleを使う原作MarkRender: テクスチャ×色、光の影響なし、合成あり、両面、すべての上に描く。
         * 位置・色・テクスチャだけを使い、ModelPartの他の頂点要素はbufferが読み飛ばす。
         */
        static final RenderType[] FIGURE = new RenderType[MARK_FRAMES];
        static {
            for (int i = 0; i < MARK_FRAMES; i++)
                FIGURE[i] = create("academy_tp_mark_" + i, DefaultVertexFormat.POSITION_COLOR_TEX, VertexFormat.Mode.QUADS, 256, false, true,
                        CompositeState.builder().setShaderState(POSITION_COLOR_TEX_SHADER)
                                .setTextureState(new TextureStateShard(MARK_TEXTURES[i], false, false))
                                .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setCullState(NO_CULL)
                                .setDepthTestState(NO_DEPTH_TEST).setWriteMaskState(COLOR_WRITE).createCompositeState(false));
        }
    }
    private static final MultiBufferSource.BufferSource BUFFERS = MultiBufferSource.immediate(new BufferBuilder(2048));
    @Nullable private static HumanoidModel<?> figure;
    /** 原作SimpleModelBiped: ModelBiped(0)の7つの部位をすべて静止の姿勢で。 */
    private static HumanoidModel<?> figure() {
        if (figure == null) {
            figure = new HumanoidModel<>(LayerDefinition.create(HumanoidModel.createMesh(CubeDeformation.NONE, 0), 64, 32).bakeRoot());
            // EntityModelは子供（young）として始まり、そのままだと子供の体（半分の大きさで1.5ブロック下）で描く。
            figure.young = false;
        }
        return figure;
    }

    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        var client = Minecraft.getInstance();
        if (client.level != world || client.player == null || (AIMS.isEmpty() && flashing == null)) return;
        var camera = event.getCamera().getPosition(); var pose = event.getPoseStack();
        float partial = event.getPartialTick();
        var all = new ArrayList<Aim>(AIMS.values());
        if (flashing != null) all.add(flashing);
        for (var aim : all) {
            for (var marker : aim.markers) brackets(pose, camera, marker, partial);
            for (var marker : aim.targets) if (!marker.target().isRemoved()) brackets(pose, camera, marker, partial);
            if (aim.mark != null && aim.markPrevious != null) figure(pose, camera, aim.mark, aim.markPrevious, partial);
        }
    }

    private static void brackets(PoseStack pose, Vec3 camera, Marker marker, float partial) {
        float w = marker.w(), h = marker.h();
        var at = marker.at(partial).subtract(camera);
        // 原作: 0.05 * sin(GameTimer.getAbsTime() / 400)。getAbsTimeは、この式が想定したミリ秒ではなく秒を数えるので、
        // 上下の揺れは約42分周期になる。原作のまま残している。
        double bob = .05 * Math.sin(net.minecraft.Util.getMillis() / 1000.0 / 400.0);
        var type = marker.ignoreDepth() ? Types.LINES_THROUGH : Types.LINES;
        var buffer = BUFFERS.getBuffer(type);
        int[] c = marker.color();
        pose.pushPose();
        try {
            pose.translate(at.x - w / 2, at.y + bob, at.z - w / 2);
            double[][] corners = {{0, 0, 0}, {1, 0, 0}, {1, 0, 1}, {0, 0, 1}, {0, 1, 0}, {1, 1, 0}, {1, 1, 1}, {0, 1, 1}};
            float[] turns = {0, -90, -180, -270, 0, -90, -180, -270};
            float len = .2f * w;
            for (int i = 0; i < 8; i++) {
                pose.pushPose();
                pose.translate(w * corners[i][0], h * corners[i][1], w * corners[i][2]);
                pose.mulPose(Axis.YP.rotationDegrees(turns[i]));
                var m = pose.last().pose(); var n = pose.last().normal();
                line(buffer, m, n, 0, i < 4 ? len : -len, 0, c);
                line(buffer, m, n, len, 0, 0, c);
                line(buffer, m, n, 0, 0, len, c);
                pose.popPose();
            }
        } finally { pose.popPose(); }
        endBatch(type, marker.ignoreDepth());
        bracketBoxes++;
    }
    /**
     * NO_DEPTH_TEST（深度関数ALWAYS）は何も設定しない。深度テストが既に切れている所でしか効かず、
     * 粒子の後のワールドの描画では深度テストが有効。そのため壁越しのbatchは自分で深度テストを切って戻す。
     * そうしないと、他の物と同じく地形の裏に隠れる。
     */
    private static void endBatch(RenderType type, boolean throughWalls) {
        boolean depth = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);
        if (throughWalls) RenderSystem.disableDepthTest();
        try { BUFFERS.endBatch(type); }
        finally { if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest(); }
    }
    private static void line(com.mojang.blaze3d.vertex.VertexConsumer buffer, org.joml.Matrix4f m, org.joml.Matrix3f n,
                             float x, float y, float z, int[] c) {
        float length = (float) Math.sqrt(x * x + y * y + z * z);
        buffer.vertex(m, 0, 0, 0).color(c[0], c[1], c[2], c[3]).normal(n, x / length, y / length, z / length).endVertex();
        buffer.vertex(m, x, y, z).color(c[0], c[1], c[2], c[3]).normal(n, x / length, y / length, z / length).endVertex();
    }

    private static void figure(PoseStack pose, Vec3 camera, Mark mark, Vec3 previous, float partial) {
        // 原作MarkRender: tp_markの(ticksExisted / 2.5) % 7番目の絵を、白、空きが無い所では赤で描く。
        int frame = (int) ((mark.age() / 2.5) % MARK_FRAMES);
        var at = previous.lerp(mark.position(), partial).subtract(camera);
        var type = Types.FIGURE[frame];
        pose.pushPose();
        try {
            pose.translate(at.x, at.y, at.z);
            pose.mulPose(Axis.YP.rotationDegrees(-mark.yaw()));
            pose.scale(-1, -1, 1);
            float g = mark.available() ? 1 : .2f;
            figure().renderToBuffer(pose, BUFFERS.getBuffer(type), 0xF000F0, OverlayTexture.NO_OVERLAY, 1, g, g, 1);
            endBatch(type, true);
            figures++;
        } finally { pose.popPose(); }
    }

    /** テスト用の入口: 今の狙いの表示と、そのうちどれだけを描いた・出したか。 */
    public static List<Marker> markers() {
        var all = new ArrayList<Marker>();
        for (var aim : AIMS.values()) { all.addAll(aim.markers); all.addAll(aim.targets); }
        return all;
    }
    @Nullable public static Mark mark() {
        if (flashing != null && flashing.mark != null) return flashing.mark;
        for (var aim : AIMS.values()) if (aim.mark != null) return aim.mark;
        return null;
    }
    public static boolean aiming(ResourceLocation skill) { return AIMS.values().stream().anyMatch(a -> a.skill.equals(skill)); }
    /** 狙う技能を押し続けているclientのtick数（原作のclient側のtickの数え方）。押していないときは-1。 */
    public static int heldTicks(ResourceLocation skill) {
        for (var aim : AIMS.values()) if (aim.skill.equals(skill)) return aim.ticks;
        return -1;
    }
    public static long particles() { return particles; }
    public static long figures() { return figures; }
    public static long bracketBoxes() { return bracketBoxes; }

    /**
     * 原作PTContext.onPlayerUseWheelとupdateDistance: useMouseWheelがONのとき、ホイール1段ごとに距離を原作のmwSpd（1）だけ
     * 動かす。ただし0.5から最大の範囲に収まる場合だけ。原作と同じく、ホイールはホットバーもスクロールする。
     */
    @SubscribeEvent public static void wheel(net.minecraftforge.client.event.InputEvent.MouseScrollingEvent event) {
        if (Minecraft.getInstance().screen == null) scroll(event.getScrollDelta());
    }
    public static void scroll(double notches) {
        if (!ClientSettings.get(ClientSettings.USE_MOUSE_WHEEL)) return;
        for (var aim : AIMS.values()) {
            if (!aim.skill.equals(PenetrateTeleport.ID) || Double.isNaN(aim.distance)) continue;
            var player = Minecraft.getInstance().player;
            var data = player == null ? null : player.getCapability(io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities.PLAYER_ABILITY).orElse(null);
            if (data == null) return;
            double next = aim.distance + notches;
            if (next >= PenetrateTeleport.MIN_DISTANCE && next <= PenetrateTeleport.maxDistance(data.getProficiency(PenetrateTeleport.ID))) {
                aim.distance = next;
                io.github.pinchan4273.reacademycraft.network.AcademyNetwork.CHANNEL.sendToServer(new io.github.pinchan4273.reacademycraft.network.PenetrateDistance((float) next));
            }
        }
    }
    /** テスト用の入口: Penetrate Teleportの狙いの距離。狙っていないときはNaN。 */
    public static double penetrateDistance() {
        for (var aim : AIMS.values()) if (aim.skill.equals(PenetrateTeleport.ID)) return aim.distance;
        return Double.NaN;
    }
    @SubscribeEvent public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) { AIMS.clear(); flashing = null; world = null; }
}
