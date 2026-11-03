package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.network.DirectEffectSync;
import io.github.pinchan4273.reacademycraft.skill.DirectEffects;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 自分に掛かっている技能由来の直接効果のうち、クライアントが行う物（v1.5.0）: 跳躍の上向きの速さ、盲目の霧、吐き気の画面の歪み。
 * 一覧はサーバーが送る（{@link DirectEffectSync}）。時間はサーバーが決め、ここでは表示のために減らすだけで、終わりもサーバーが伝える。
 * バニラの状態効果を付けないので、牛乳などで消えない（サーバーの{@link DirectEffects}）。
 */
@Mod.EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class ClientDirectEffects {
    private static final List<int[]> EFFECTS = new ArrayList<>(); // {kind, amplifier, remaining}
    private static ClientLevel world;
    /** 吐き気の歪みの強さ（バニラと同じ0.0125/tickで上がり、終わると0.05/tickで下がる）。 */
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

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var client = Minecraft.getInstance();
        current();
        if (client.player == null || client.isPaused()) return;
        for (var effect : EFFECTS) if (effect[2] > 1) effect[2]--;
        // 吐き気: バニラの吐き気と同じ増え方で、LocalPlayerの歪みの強さを下から支える（バニラ自身の処理はそのまま）。
        if (amplifier(DirectEffects.Kind.NAUSEA) >= 0) spin = Math.min(1, spin + .0125f);
        else spin = Math.max(0, spin - .05f);
        if (spin > 0 && client.player.spinningEffectIntensity < spin) client.player.spinningEffectIntensity = spin;
    }
    /** 吐き気の歪みだけを出し、ネザーポータルの紫の重ね絵は出さない（バニラの吐き気と同じ見え方。ポータルの中なら通常どおり）。 */
    @SubscribeEvent public static void overlay(RenderGuiOverlayEvent.Pre event) {
        var player = Minecraft.getInstance().player;
        if (player == null || spin <= 0 || !event.getOverlay().id().equals(VanillaGuiOverlay.PORTAL.id())) return;
        if (!player.level().getBlockState(player.blockPosition()).is(Blocks.NETHER_PORTAL)) event.setCanceled(true);
    }
    /** 自分の跳躍（バニラのJump Boostと同じ+0.1/段）。サーバー側は{@link DirectEffects#jump}。 */
    @SubscribeEvent public static void jump(LivingEvent.LivingJumpEvent event) {
        var player = Minecraft.getInstance().player;
        if (player == null || event.getEntity() != player) return;
        int amplifier = amplifier(DirectEffects.Kind.JUMP);
        if (amplifier >= 0) player.setDeltaMovement(DirectEffects.jumped(player.getDeltaMovement(), amplifier));
    }
    /** 盲目の霧（バニラの盲目の霧と同じ距離）。 */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void fog(ViewportEvent.RenderFog event) {
        if (amplifier(DirectEffects.Kind.BLINDNESS) < 0) return;
        int left = remaining(DirectEffects.Kind.BLINDNESS);
        float distance = Mth.lerp(left < 0 ? 1 : Math.min(1, left / 20f), event.getFarPlaneDistance(), 5f);
        if (event.getMode() == FogRenderer.FogMode.FOG_SKY) { event.setNearPlaneDistance(0); event.setFarPlaneDistance(distance * .8f); }
        else { event.setNearPlaneDistance(distance * .25f); event.setFarPlaneDistance(distance); }
        event.setCanceled(true);
    }
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void fogColor(ViewportEvent.ComputeFogColor event) {
        if (amplifier(DirectEffects.Kind.BLINDNESS) < 0) return;
        event.setRed(0); event.setGreen(0); event.setBlue(0);
    }
    public static void reset() { EFFECTS.clear(); world = null; spin = 0; }
    @SubscribeEvent public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) { reset(); }
}
