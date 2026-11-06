package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.network.DirectEffectSync;
import io.github.pinchan4273.reacademycraft.skill.DirectEffects;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;

/**
 * 自分に掛かっている技能由来の直接効果のうち、クライアントが行う物（v1.5.0）: 跳躍の上向きの速さ、盲目の霧、吐き気の画面の歪み。
 * 一覧はサーバーが送る（{@link DirectEffectSync}）。時間はサーバーが決め、ここでは表示のために減らすだけで、終わりもサーバーが伝える。
 * バニラの状態効果を付けないので、牛乳などで消えない（サーバーの{@link DirectEffects}）。
 * <p>
 * NeoForge版の差: Forge 1.20.1版はネザーポータルの紫の重ね絵を独立したGUIの重ね（{@code VanillaGuiOverlay.PORTAL}）として取り消した。
 * NeoForge ではその絵が「camera_overlays」の層（かぼちゃ・粉雪などと同じ層）の中にあり、層ごとは取り消せないので、
 * {@code mixin/client/HudPortalOverlayMixin}がその絵の呼び出しだけを{@link #hidesPortalOverlay}で止める（26.2 は {@code Hud}）。
 * <p>
 * 26.2 の差: 歪みの強さは、ポータルの強さ（{@code LocalPlayer.portalEffectIntensity}）とバニラの吐き気の効果の強さに分かれた。
 * 1.21.1 の {@code spinningEffectIntensity}（ポータルとバニラの吐き気が共有した値）に当たるのはポータルの強さなので、Forge 版・1.21.1 版と
 * 同じ歪み（速さもポータルと同じ）になるよう、そちらを下から支える。霧は 26.2 の {@code FogData} の値を直に変える（取り消しの仕組みは無い）。
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class ClientDirectEffects {
    private static final List<int[]> EFFECTS = new ArrayList<>(); // {kind, amplifier, remaining}
    private static ClientLevel world;
    /** 吐き気の歪みの強さ（Forge 1.20.1版のバニラと同じ0.0125/tickで上がり、終わると0.05/tickで下がる）。 */
    private static float spin;
    private ClientDirectEffects() { }

    public static void receive(DirectEffectSync packet) {
        world = Minecraft.getInstance().level;
        EFFECTS.clear();
        for (var effect : packet.effects()) EFFECTS.add(new int[] {effect.kind(), effect.amplifier(), effect.remaining()});
    }
    private static void current() { if (Minecraft.getInstance().level != world) { EFFECTS.clear(); world = null; } }
    /** その種類の最も強い段（無ければ-1）。 */
    public static int amplifier(DirectEffects.Kind kind) {
        current();
        int best = -1;
        for (var effect : EFFECTS) if (effect[0] == kind.ordinal()) best = Math.max(best, effect[1]);
        return best;
    }
    /** その種類の最も長い残tick（トグル中なら-1、無ければ0）。 */
    private static int remaining(DirectEffects.Kind kind) {
        int best = 0;
        for (var effect : EFFECTS) if (effect[0] == kind.ordinal()) { if (effect[2] < 0) return -1; best = Math.max(best, effect[2]); }
        return best;
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var client = Minecraft.getInstance();
        current();
        if (client.player == null || client.isPaused()) return;
        for (var effect : EFFECTS) if (effect[2] > 1) effect[2]--;
        // 吐き気: Forge版と同じ増え方で、LocalPlayerの歪みの強さを下から支える（バニラ自身の処理はそのまま）。
        if (amplifier(DirectEffects.Kind.NAUSEA) >= 0) spin = Math.min(1, spin + .0125f);
        else spin = Math.max(0, spin - .05f);
        if (spin > 0 && client.player.portalEffectIntensity < spin) client.player.portalEffectIntensity = spin;
    }
    /**
     * 吐き気の歪みだけを出し、ネザーポータルの紫の重ね絵は出さないか（バニラの吐き気と同じ見え方。ポータルの中なら通常どおり出す）。
     * {@code mixin/client/HudPortalOverlayMixin}が描画の直前に聞く。
     */
    public static boolean hidesPortalOverlay() {
        var player = Minecraft.getInstance().player;
        return player != null && spin > 0 && !player.level().getBlockState(player.blockPosition()).is(Blocks.NETHER_PORTAL);
    }
    /** 自分の跳躍（バニラのJump Boostと同じ+0.1/段）。サーバー側は{@link DirectEffects#jump}。 */
    @SubscribeEvent public static void jump(LivingEvent.LivingJumpEvent event) {
        var player = Minecraft.getInstance().player;
        if (player == null || event.getEntity() != player) return;
        int amplifier = amplifier(DirectEffects.Kind.JUMP);
        if (amplifier >= 0) player.setDeltaMovement(DirectEffects.jumped(player.getDeltaMovement(), amplifier));
    }
    /**
     * 盲目の霧（バニラの盲目の霧と同じ距離）。26.2 の霧は1回の計算で地形・空・雲の距離を全部持つ（{@code FogData}）ので、
     * バニラの {@code BlindnessFogEnvironment} と同じ値を直に書く。元の距離は描画距離（{@code renderDistanceEnd}。バニラの盲目と同じ）。
     * Forge 版と同じく、溶岩・粉雪の中でも盲目の霧にする。
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void fog(ViewportEvent.RenderFog event) {
        if (amplifier(DirectEffects.Kind.BLINDNESS) < 0) return;
        int left = remaining(DirectEffects.Kind.BLINDNESS);
        var fog = event.getFogData();
        float distance = Mth.lerp(left < 0 ? 1 : Math.min(1, left / 20f), fog.renderDistanceEnd, 5f);
        fog.environmentalStart = distance * .25f;
        fog.environmentalEnd = distance;
        fog.skyEnd = distance * .8f;
        fog.cloudEnd = distance * .8f;
    }
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void fogColor(ViewportEvent.ComputeFogColor event) {
        if (amplifier(DirectEffects.Kind.BLINDNESS) < 0) return;
        event.setRed(0); event.setGreen(0); event.setBlue(0);
    }
    public static void reset() { EFFECTS.clear(); world = null; spin = 0; }
    @SubscribeEvent public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) { reset(); }
}
