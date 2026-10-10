package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.network.SkillBurst;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 原作EntityBloodSplashとCriticalHitEffect: テレポートの技能が当てた相手に出す2つの演出。
 *
 * 血: Flesh Rippingが相手を放したとき、相手の箱のどこかに、0.8〜1.3の大きさの血しぶきを4〜6個出す。
 * カメラへ向け、暗い赤(213, 29, 29, 200)で、10枚のblood_splashの絵を1tickに1枚進めて消える。深度マスクはOFF。
 *
 * クリティカル: 相手の周り（幅の0.5〜0.7倍離れた所、高さのどこか）に数式を5〜8個出す。白で、大きさ1〜1.7、
 * 1tickに0.03以内で漂い、2tickでフェードインし、10〜15tick後に20tickでフェードアウトする。原作は術者だけに送る。
 */
@Mod.EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class ClientTeleporterBursts {
    static final int BLOOD_FRAMES = 10, FORMULA_PICTURES = 10;
    private static final ResourceLocation[] BLOOD = new ResourceLocation[BLOOD_FRAMES];
    private static final ResourceLocation[] FORMULA = new ResourceLocation[FORMULA_PICTURES];
    static {
        for (int i = 0; i < BLOOD_FRAMES; i++)
            BLOOD[i] = ResourceLocation.fromNamespaceAndPath("academy", "textures/effects/blood_splash/" + i + ".png");
        for (int i = 0; i < FORMULA_PICTURES; i++)
            FORMULA[i] = ResourceLocation.fromNamespaceAndPath("academy", "textures/effects/formula/" + i + ".png");
    }
    /** 原作SplashRendererの色と、数式自身の白。 */
    static final int[] BLOOD_COLOUR = {213, 29, 29, 200};

    private static final class Splash {
        final Vec3 at; final float size; int frame;
        Splash(Vec3 at, float size) { this.at = at; this.size = size; }
    }
    private static final class Formula {
        Vec3 at; final Vec3 drift; final float size, alpha; final int picture, life;
        int ticks;
        Formula(Vec3 at, Vec3 drift, float size, float alpha, int picture, int life) {
            this.at = at; this.drift = drift; this.size = size; this.alpha = alpha; this.picture = picture; this.life = life;
        }
        /** 原作の粒子: 2tickで現れて保ち、自身の寿命の後に20tickで消える。 */
        float alpha() {
            if (ticks < 2) return alpha * ticks / 2f;
            if (ticks <= life) return alpha;
            return Math.max(0, alpha * (1 - (ticks - life) / 20f));
        }
        boolean finished() { return ticks > life + 20; }
    }
    private static final List<Splash> SPLASHES = new ArrayList<>();
    private static final List<Formula> FORMULAE = new ArrayList<>();
    private static final RandomSource RANDOM = RandomSource.create();
    @Nullable private static ClientLevel world;
    private static long splashesDrawn, formulaeDrawn, trailParticles;
    private ClientTeleporterBursts() { }

    public static void receive(SkillBurst packet) {
        var client = Minecraft.getInstance(); var level = client.level;
        if (level != world) { clear(); world = level; }
        if (level == null || !level.dimension().location().equals(packet.dimension())) return;
        var target = level.getEntity(packet.entityId());
        if (target == null) return;
        double width = target.getBbWidth(), height = target.getBbHeight();
        if (SkillBurst.BLOOD.equals(packet.kind())) {
            if (SPLASHES.size() > 64) return;
            for (int i = 0, max = 4 + RANDOM.nextInt(3); i < max; i++) {
                double angle = RANDOM.nextDouble() * Math.PI * 2;
                double radius = .5 * (.8 * width + RANDOM.nextDouble() * .2 * width);
                SPLASHES.add(new Splash(new Vec3(target.getX() + radius * Math.sin(angle),
                        target.getY() + RANDOM.nextDouble() * height,
                        target.getZ() + radius * Math.cos(angle)), .8f + RANDOM.nextFloat() * .5f));
            }
        } else if (SkillBurst.CRITICAL.equals(packet.kind())) {
            if (FORMULAE.size() > 64) return;
            for (int i = 0, max = 5 + RANDOM.nextInt(3); i < max; i++) {
                double angle = RANDOM.nextDouble() * Math.PI * 2;
                double radius = width * (.5 + RANDOM.nextDouble() * .2);
                // 原作のalphaは152〜384から選び、色そのもので上限を切る。
                float alpha = Math.min(255, 152 + RANDOM.nextInt(384 - 152)) / 255f;
                FORMULAE.add(new Formula(new Vec3(target.getX() + radius * Math.sin(angle),
                        target.getY() + RANDOM.nextDouble() * height,
                        target.getZ() + radius * Math.cos(angle)),
                        new Vec3(range(), range(), range()), 1 + RANDOM.nextFloat() * .7f, alpha,
                        RANDOM.nextInt(FORMULA_PICTURES), 10 + RANDOM.nextInt(6)));
            }
        }
    }
    /**
     * 両方のテレポートの原作c_end: 始点から終点までの線を1歩ずつ進み、着いた場所ごとにtpの粒子を落とす。
     * 最初の1歩は常に1ブロック。その後はThreateningが1〜2、Shiftが0.6〜1ずつ進み、粒子の漂い方も違う
     * （Threateningは横に0.02以内、Shiftは0.05以内。どちらも上へ-0.02〜0.05）。
     */
    public static void receive(io.github.pinchan4273.reacademycraft.network.SkillTrail packet) {
        var client = Minecraft.getInstance(); var level = client.level;
        if (level != world) { clear(); world = level; }
        if (level == null || !level.dimension().location().equals(packet.dimension())) return;
        boolean shift = io.github.pinchan4273.reacademycraft.network.SkillTrail.SHIFT.equals(packet.kind());
        var delta = packet.to().subtract(packet.from());
        double distance = delta.length();
        if (distance < 1e-6) return;
        var direction = delta.normalize();
        var at = packet.from();
        double step = 1, walked = step;
        int spawned = 0;
        while (walked <= distance && spawned < 128) {
            at = at.add(direction.scale(step));
            double sideways = shift ? .05 : .02;
            level.addParticle(io.github.pinchan4273.reacademycraft.world.AcademyParticles.TP.get(), at.x, at.y, at.z,
                    -sideways + RANDOM.nextDouble() * 2 * sideways,
                    -.02 + RANDOM.nextDouble() * .07,
                    -sideways + RANDOM.nextDouble() * 2 * sideways);
            spawned++; trailParticles++;
            step = shift ? .6 + RANDOM.nextDouble() * .4 : 1 + RANDOM.nextDouble();
            walked += step;
        }
    }

    /** 原作は、ランダムな単位ベクトルに0.03を掛ける。 */
    private static double range() { return (RANDOM.nextDouble() * 2 - 1) * .03; }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var client = Minecraft.getInstance(); var level = client.level;
        if (level != world) { clear(); world = level; return; }
        if (level == null || client.isPaused()) return;
        SPLASHES.removeIf(splash -> ++splash.frame >= BLOOD_FRAMES);
        FORMULAE.removeIf(formula -> {
            formula.ticks++;
            formula.at = formula.at.add(formula.drift);
            return formula.finished();
        });
    }

    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        if (SPLASHES.isEmpty() && FORMULAE.isEmpty()) return;
        var camera = event.getCamera().getPosition(); var pose = event.getPoseStack();
        var facing = event.getCamera().rotation();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        try {
            for (var splash : SPLASHES) {
                RenderSystem.setShaderTexture(0, BLOOD[Math.min(splash.frame, BLOOD_FRAMES - 1)]);
                billboard(pose, facing, splash.at.subtract(camera), splash.size, true,
                        BLOOD_COLOUR[0] / 255f, BLOOD_COLOUR[1] / 255f, BLOOD_COLOUR[2] / 255f, BLOOD_COLOUR[3] / 255f);
                splashesDrawn++;
            }
            for (var formula : FORMULAE) {
                float alpha = formula.alpha();
                if (alpha <= 0) continue;
                RenderSystem.setShaderTexture(0, FORMULA[formula.picture]);
                billboard(pose, facing, formula.at.subtract(camera), formula.size, false, FORMULA_GREY, FORMULA_GREY, FORMULA_GREY, alpha);
                formulaeDrawn++;
            }
        } finally { RenderSystem.depthMask(true); RenderSystem.enableCull(); }
    }

    /** 原作FormulaParticleFactoryのテンプレートの色(220, 220, 220): 数式は白ではなく明るい灰色。 */
    static final float FORMULA_GREY = 220 / 255f;
    /**
     * iconLift: 血しぶきはLambdaLib2のRenderIconで、四角は大きさの-0.25から0.75まで画面の上方向に伸びる
     * （置く位置から大きさの4分の1上）。数式はLambdaLib2の粒子（Sprite）で、中心に置く。
     */
    private static void billboard(com.mojang.blaze3d.vertex.PoseStack pose, org.joml.Quaternionf facing,
                                  Vec3 at, float size, boolean iconLift, float red, float green, float blue, float alpha) {
        pose.pushPose();
        try {
            pose.translate(at.x, at.y, at.z);
            pose.mulPose(facing);
            float half = size / 2, bottom = iconLift ? -size / 4 : -half, top = iconLift ? size * .75f : half;
            var m = pose.last().pose(); var buffer = Tesselator.getInstance().getBuilder();
            buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            buffer.vertex(m, -half, bottom, 0).uv(0, 1).color(red, green, blue, alpha).endVertex();
            buffer.vertex(m, half, bottom, 0).uv(1, 1).color(red, green, blue, alpha).endVertex();
            buffer.vertex(m, half, top, 0).uv(1, 0).color(red, green, blue, alpha).endVertex();
            buffer.vertex(m, -half, top, 0).uv(0, 0).color(red, green, blue, alpha).endVertex();
            BufferUploader.drawWithShader(buffer.end());
        } finally { pose.popPose(); }
    }

    /** 別の技能（Blood Retrograde）の原作EntityBloodSplash。Flesh Rippingのものと一緒に描く。 */
    static void splash(Vec3 at, float size) {
        var level = Minecraft.getInstance().level;
        if (level != world) { clear(); world = level; }
        if (SPLASHES.size() <= 64) SPLASHES.add(new Splash(at, size));
    }
    private static void clear() { SPLASHES.clear(); FORMULAE.clear(); }
    @SubscribeEvent public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) { clear(); world = null; }
    /** テスト用の入口。 */
    public static int splashes() { return SPLASHES.size(); }
    public static int formulae() { return FORMULAE.size(); }
    public static long splashesDrawn() { return splashesDrawn; }
    public static long formulaeDrawn() { return formulaeDrawn; }
    public static long trailParticles() { return trailParticles; }
}
