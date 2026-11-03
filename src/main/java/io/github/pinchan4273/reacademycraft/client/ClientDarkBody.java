package io.github.pinchan4273.reacademycraft.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities;
import io.github.pinchan4273.reacademycraft.network.DarkWingAction;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * v1.5.0 §14: 未元物質人間のHUD。生命力はCPだけなので、ハート・食料・酸素のゲージを描かない。CPのゲージは{@link CpBarHud}が能力OFFでも出す。
 * 形態はサーバーの同期（{@code network/AbilitySnapshot}）で届き、通常人間へ戻れば次の同期で元の表示になる。
 * <p>
 * 外観（COMMAND-RE-150-DARK-BODY-VISUAL-R1-20261104の初稿）: 本人と見ている人のクライアントで、未元物質人間の頭上の輪（{@link DarkHaloLayer}）と、
 * 変化の瞬間の白いシルエットの状態を持つ。形態の有無と変化の合図は{@link DarkWingAction#BODY}・{@link DarkWingAction#BODY_FLASH}で届く。
 */
@Mod.EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class ClientDarkBody {
    private ClientDarkBody() { }

    /** 今のプレイヤーが未元物質人間か。 */
    public static boolean active() {
        var player = Minecraft.getInstance().player;
        var data = player == null ? null : player.getCapability(AbilityCapabilities.PLAYER_ABILITY).orElse(null);
        return data != null && data.isDarkBody();
    }
    /**
     * 最も高い優先度で取り消す（2026-11-03のユーザーの指摘: 他のMODがハートを自前で描くと、ハートだけ残った）。描画の合図を受けてゲージを
     * 置き換えるMODは、取り消し済みの合図では描かないことが多いので、それより先に取り消す。特定のMODは見ない（汎用の対応）。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void overlay(RenderGuiOverlayEvent.Pre event) {
        var id = event.getOverlay().id();
        if ((id.equals(VanillaGuiOverlay.PLAYER_HEALTH.id()) || id.equals(VanillaGuiOverlay.FOOD_LEVEL.id()) || id.equals(VanillaGuiOverlay.AIR_LEVEL.id())) && active())
            event.setCanceled(true);
    }

    // ---- 頭上の輪と変化の白いシルエット ----

    /** 白いシルエットの時間（tick）: 0.25秒で白くなり、1.5秒保ち、0.75秒で戻る。 */
    static final int FLASH_RISE = 5, FLASH_HOLD = 30, FLASH_DECAY = 15;
    /** 輪の揺れの周期の速さ（ラジアン/tick）: 静止で約3秒、移動中で約1.2秒。 */
    static final float BOB_IDLE_SPEED = (float) (Math.PI * 2 / 60), BOB_MOVE_SPEED = (float) (Math.PI * 2 / 24);

    /** 1人分の外観の状態。 */
    public static final class Mark {
        boolean body;
        int flashTicks = -1;
        /** 動きの度合い（0〜1、滑らかにした値）と、揺れの位相。 */
        float moveO, move, phaseO, phase;
        /** 白さ（0〜1）。 */
        public float flash(float partial) {
            if (flashTicks < 0) return 0;
            float t = flashTicks + partial;
            if (t < FLASH_RISE) return t / FLASH_RISE;
            if (t < FLASH_RISE + FLASH_HOLD) return 1;
            return Math.max(0, 1 - (t - FLASH_RISE - FLASH_HOLD) / FLASH_DECAY);
        }
        public boolean body() { return body; }
        public float move(float partial) { return previewMove != null ? previewMove : Mth.lerp(partial, moveO, move); }
        public float phase(float partial) { return previewPhase != null ? previewPhase : Mth.lerp(partial, phaseO, phase); }
    }
    private static final Map<Integer, Mark> MARKS = new HashMap<>();
    @Nullable private static ClientLevel world;
    /** 撮影用: 揺れの位相と動きの度合いを固定する（nullで解除）。 */
    @Nullable private static Float previewPhase, previewMove;
    public static void previewBob(@Nullable Float phase, @Nullable Float move) { previewPhase = phase; previewMove = move; }

    /** サーバーからの形態と変化の合図（{@link ClientDarkWings}が受けて渡す）。 */
    public static void receive(DarkWingAction packet) {
        var level = Minecraft.getInstance().level;
        if (level != world) { MARKS.clear(); world = level; }
        var mark = MARKS.computeIfAbsent(packet.entityId(), id -> new Mark());
        if (packet.kind() == DarkWingAction.BODY_FLASH) { mark.body = true; mark.flashTicks = 0; }
        else mark.body = packet.value() != 0;
    }
    @Nullable public static Mark mark(int entityId) { return MARKS.get(entityId); }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var client = Minecraft.getInstance(); var level = client.level;
        if (level != world) { MARKS.clear(); world = level; return; }
        if (level == null || client.isPaused()) return;
        MARKS.entrySet().removeIf(entry -> {
            var mark = entry.getValue();
            Entity entity = level.getEntity(entry.getKey());
            if (entity == null || entity.isRemoved()) return true;
            if (mark.flashTicks >= 0 && ++mark.flashTicks > FLASH_RISE + FLASH_HOLD + FLASH_DECAY) mark.flashTicks = -1;
            // 歩く・飛ぶほど大きく速く揺れる（翼を閉じて歩くときも揺れる）。
            var wings = ClientDarkWings.state(entity.getId());
            double speed = Math.hypot(entity.getX() - entity.xo, entity.getZ() - entity.zo) + Math.abs(entity.getY() - entity.yo) * .5;
            float target = wings != null && wings.flight != 0 ? 1 : (float) Mth.clamp(speed / .2, 0, 1);
            mark.moveO = mark.move;
            mark.move += (target - mark.move) * .15f;
            mark.phaseO = mark.phase;
            mark.phase += Mth.lerp(mark.move, BOB_IDLE_SPEED, BOB_MOVE_SPEED);
            return !mark.body && mark.flashTicks < 0;
        });
    }
    @SubscribeEvent public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) { MARKS.clear(); world = null; }

    /**
     * 変化の瞬間の白いシルエット: 体・装備・手の持ち物・翼を、同じ描画経路でもう一度描き直し、そのとき頂点の重ね色（オーバーレイ）を白へ、
     * 明るさを最大へ寄せる。描き直しは元の描画を取り消して1回だけ行う（二重には描かない）。被弾の赤い点滅（重ね色の赤の段）はそのまま残す。
     * 普段から体を淡く白く光らせる案は試したが、2026-11-04のユーザーの判断で採らない（白くするのは変化の瞬間だけ）。
     */
    private static boolean whitening;
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void renderPre(RenderPlayerEvent.Pre event) {
        if (whitening) return;
        var mark = MARKS.get(event.getEntity().getId());
        float partial = event.getPartialTick(), level = mark == null ? 0 : mark.flash(partial);
        if (level <= 0) return;
        event.setCanceled(true);
        var player = event.getEntity();
        var source = event.getMultiBufferSource();
        int overlayU = Math.round(level * 15);
        MultiBufferSource whitened = type -> new Whiten(source.getBuffer(type), overlayU, level);
        whitening = true;
        try {
            event.getRenderer().render((net.minecraft.client.player.AbstractClientPlayer) player, Mth.lerp(partial, player.yRotO, player.getYRot()), partial,
                    event.getPoseStack(), whitened, event.getPackedLight());
        } finally { whitening = false; }
    }

    /** 頂点の重ね色を白へ、明るさを最大へ寄せる。被弾の赤（重ね色の縦の段が8未満）はそのまま。他の値はそのまま渡す。 */
    private record Whiten(VertexConsumer out, int whiteU, float level) implements VertexConsumer {
        private int light(int value) { return Math.round(Mth.lerp(level, value, 240)); }
        private int packed(int light) { return LightTexture.pack(Math.round(Mth.lerp(level, LightTexture.block(light), 15)), Math.round(Mth.lerp(level, LightTexture.sky(light), 15))); }
        private int overlay(int u, int v) { return v < 8 ? OverlayTexture.pack(u, v) : OverlayTexture.pack(whiteU, 10); }
        @Override public VertexConsumer vertex(double x, double y, double z) { out.vertex(x, y, z); return this; }
        @Override public VertexConsumer color(int r, int g, int b, int a) { out.color(r, g, b, a); return this; }
        @Override public VertexConsumer uv(float u, float v) { out.uv(u, v); return this; }
        @Override public VertexConsumer overlayCoords(int u, int v) { int o = overlay(u, v); out.overlayCoords(o & 0xFFFF, o >> 16 & 0xFFFF); return this; }
        @Override public VertexConsumer uv2(int u, int v) { out.uv2(light(u), light(v)); return this; }
        @Override public VertexConsumer normal(float x, float y, float z) { out.normal(x, y, z); return this; }
        @Override public void endVertex() { out.endVertex(); }
        @Override public void defaultColor(int r, int g, int b, int a) { out.defaultColor(r, g, b, a); }
        @Override public void unsetDefaultColor() { out.unsetDefaultColor(); }
        @Override public void vertex(float x, float y, float z, float r, float g, float b, float a, float u, float v, int overlayValue, int lightValue, float nx, float ny, float nz) {
            out.vertex(x, y, z, r, g, b, a, u, v, overlay(overlayValue & 0xFFFF, overlayValue >> 16 & 0xFFFF), packed(lightValue), nx, ny, nz);
        }
    }
}
