package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities;
import io.github.pinchan4273.reacademycraft.network.BloodRetroEffect;
import io.github.pinchan4273.reacademycraft.skill.BloodRetrograde;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 原作BloodRetrogradeのclient側。
 * - キーを押している間（l_tick）、術者の歩く速さを20tickかけて0.1から0.007まで下げる（視界も狭くなる）。
 *   終わると0.1へ戻す（l_terminate）。
 * - MSG_PERFORMを受けたとき（c_perform）: 対象の周りに、大きさ1.4〜1.8の原作のEntityBloodSplashを6〜9個、
 *   術者の視線の方向へ0.2ずらして出す。さらに対象の高さの0.6から、ピッチ0・±30・±45・±60・±80と、
 *   術者の頭のyaw±20の方向へ、5ブロック先まで9本の光線を飛ばし、当たったブロックごとに
 *   原作のBloodSprayEffectを2つ出す。
 *
 * BloodSprayEffectは原作の記述どおり: 面から0.01離して面に平らに置き、大きさは1.1〜1.4
 * （側面ではその0.8倍）。向きはランダムで、面の上をガウス分布0.15でずらす。3枚の絵のうち1枚を使い、
 * 上下の面では"wall"、側面では"grnd"の絵を使う（原作のisWallはUPとDOWNでtrue）。
 * 1200tick経つか、そのブロックが無くなると消える。
 */
@Mod.EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class ClientBloodRetro {
    private static final ResourceLocation[] GROUND = new ResourceLocation[3], WALL = new ResourceLocation[3];
    static {
        for (int i = 0; i < 3; i++) {
            GROUND[i] = ResourceLocation.fromNamespaceAndPath("academy", "textures/effects/blood_spray/grnd/" + i + ".png");
            WALL[i] = ResourceLocation.fromNamespaceAndPath("academy", "textures/effects/blood_spray/wall/" + i + ".png");
        }
    }
    static final float WALK = .1f, SLOWEST = .007f;
    static final int LIFE = 1200, MAX_SPRAYS = 256;
    private static final int[] PITCHES = {0, 30, 45, 60, 80, -30, -45, -60, -80};
    private record Spray(BlockPos pos, Direction face, Vec3 centre, int picture, float size, float rotation, float u, float v) { }
    private static final List<Spray> SPRAYS = new ArrayList<>();
    private static final List<Integer> AGES = new ArrayList<>();
    private static final RandomSource RANDOM = RandomSource.create();
    @Nullable private static ClientLevel world;
    private static int heldTicks;
    private static boolean slowed;
    private static long received, sprayed, drawn;
    private ClientBloodRetro() { }

    /** 原作l_tickの、押し始めからのtickごとの歩く速さ。 */
    static float walkSpeed(int tick) { return Mth.lerp(Mth.clamp(tick / 20f, 0, 1), WALK, SLOWEST); }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var client = Minecraft.getInstance();
        var player = client.player;
        if (client.level != world) { SPRAYS.clear(); AGES.clear(); world = client.level; }
        if (player == null) { heldTicks = 0; slowed = false; return; }
        boolean held = false;
        var data = player.getCapability(AbilityCapabilities.PLAYER_ABILITY).orElse(null);
        if (data != null) for (int i = 0; i < 4; i++)
            if (BloodRetrograde.ID.equals(data.getSlot(data.getCurrentPreset(), i)) && AbilityControls.held(i)) held = true;
        if (held) { player.getAbilities().setWalkingSpeed(walkSpeed(++heldTicks)); slowed = true; }
        else {
            heldTicks = 0;
            if (slowed) { player.getAbilities().setWalkingSpeed(WALK); slowed = false; }
        }
        if (client.isPaused() || client.level == null) return;
        for (int i = SPRAYS.size() - 1; i >= 0; i--) {
            int age = AGES.get(i) + 1;
            if (age > LIFE || client.level.getBlockState(SPRAYS.get(i).pos).isAir()) { SPRAYS.remove(i); AGES.remove(i); }
            else AGES.set(i, age);
        }
    }

    public static void receive(BloodRetroEffect effect) {
        var client = Minecraft.getInstance();
        var level = client.level;
        if (level == null) return;
        if (level != world) { SPRAYS.clear(); AGES.clear(); world = level; }
        var caster = level.getEntity(effect.caster());
        var target = level.getEntity(effect.target());
        if (caster == null || target == null) return;
        received++;
        var look = caster.getLookAngle();
        double width = target.getBbWidth(), height = target.getBbHeight();
        for (int i = 0, n = 6 + RANDOM.nextInt(4); i < n; i++) {
            var at = new Vec3(target.getX() + (RANDOM.nextDouble() * 2 - 1) * width + look.x * .2,
                    target.getY() + RANDOM.nextDouble() * height + look.y * .2,
                    target.getZ() + (RANDOM.nextDouble() * 2 - 1) * width + look.z * .2);
            ClientTeleporterBursts.splash(at, 1.4f + RANDOM.nextFloat() * .4f);
        }
        var head = target.position().add(0, height * .6, 0);
        for (int pitch : PITCHES) {
            var ray = Vec3.directionFromRotation(pitch, caster.getYHeadRot() + (RANDOM.nextFloat() * 40 - 20));
            var hit = level.clip(new ClipContext(head.subtract(ray.scale(.5)), head.add(ray.scale(5)),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, null));
            if (hit.getType() != HitResult.Type.BLOCK) continue;
            for (int i = 0; i < 2; i++) spray(level, hit);
        }
    }
    private static void spray(ClientLevel level, BlockHitResult hit) {
        if (SPRAYS.size() >= MAX_SPRAYS) { SPRAYS.remove(0); AGES.remove(0); }
        var pos = hit.getBlockPos(); var face = hit.getDirection();
        var shape = level.getBlockState(pos).getShape(level, pos);
        var box = shape.isEmpty() ? new net.minecraft.world.phys.AABB(0, 0, 0, 1, 1, 1) : shape.bounds();
        var centre = new Vec3(pos.getX() + (box.minX + box.maxX) / 2 + face.getStepX() * .51 * (box.maxX - box.minX),
                pos.getY() + (box.minY + box.maxY) / 2 + face.getStepY() * .51 * (box.maxY - box.minY),
                pos.getZ() + (box.minZ + box.maxZ) / 2 + face.getStepZ() * .51 * (box.maxZ - box.minZ));
        boolean flat = face.getAxis() == Direction.Axis.Y;
        float size = (1.1f + RANDOM.nextFloat() * .3f) * (flat ? 1 : .8f);
        SPRAYS.add(new Spray(pos, face, centre, RANDOM.nextInt(10), size, RANDOM.nextFloat() * 360,
                (float) (RANDOM.nextGaussian() * .15), (float) (RANDOM.nextGaussian() * .15)));
        AGES.add(0);
        sprayed++;
    }

    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || SPRAYS.isEmpty()) return;
        var camera = event.getCamera().getPosition(); var pose = event.getPoseStack();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); RenderSystem.disableCull();
        RenderSystem.enableDepthTest(); RenderSystem.depthMask(false);
        try {
            for (var spray : SPRAYS) {
                // 原作のisWall: 上下の面は"wall"の絵を使う。
                boolean flat = spray.face.getAxis() == Direction.Axis.Y;
                RenderSystem.setShaderTexture(0, (flat ? WALL : GROUND)[spray.picture % 3]);
                pose.pushPose();
                var at = spray.centre.subtract(camera);
                pose.translate(at.x, at.y, at.z);
                // 四角の面は、ブロックの面と同じ。法線は面の方向。
                pose.mulPose(spray.face.getRotation());
                pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-90));
                pose.translate(spray.u, spray.v, 0);
                pose.scale(spray.size, spray.size, spray.size);
                pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(spray.rotation));
                var m = pose.last().pose(); var buffer = Tesselator.getInstance().getBuilder();
                buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
                buffer.vertex(m, -.5f, -.5f, 0).uv(0, 1).color(1f, 1f, 1f, 1f).endVertex();
                buffer.vertex(m, .5f, -.5f, 0).uv(1, 1).color(1f, 1f, 1f, 1f).endVertex();
                buffer.vertex(m, .5f, .5f, 0).uv(1, 0).color(1f, 1f, 1f, 1f).endVertex();
                buffer.vertex(m, -.5f, .5f, 0).uv(0, 0).color(1f, 1f, 1f, 1f).endVertex();
                BufferUploader.drawWithShader(buffer.end());
                pose.popPose();
                drawn++;
            }
        } finally { RenderSystem.depthMask(true); RenderSystem.enableCull(); }
    }
    @SubscribeEvent public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) { SPRAYS.clear(); AGES.clear(); world = null; }

    // テスト用。
    public static long received() { return received; }
    public static long sprayed() { return sprayed; }
    public static long drawn() { return drawn; }
    public static int sprays() { return SPRAYS.size(); }
    public static int heldTicks() { return heldTicks; }
}
