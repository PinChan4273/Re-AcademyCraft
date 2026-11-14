package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities;
import io.github.pinchan4273.reacademycraft.network.AcademyNetwork;
import io.github.pinchan4273.reacademycraft.network.PassiveFlightInput;
import io.github.pinchan4273.reacademycraft.skill.AbilitySettings;
import io.github.pinchan4273.reacademycraft.skill.AbilitySettingsVisibility;
import io.github.pinchan4273.reacademycraft.skill.JetEngine;
import io.github.pinchan4273.reacademycraft.skill.PassiveSkills;
import io.github.pinchan4273.reacademycraft.skill.PlayerAbilityData;
import io.github.pinchan4273.reacademycraft.skill.StormWing;
import io.github.pinchan4273.reacademycraft.skill.WhiteWingFlight;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 1.5.2: パッシブにした飛行（ストームウィング・ジェット・エンジン）の術者側の操作。白翼飛行（{@link ClientWhiteWingFlight}）と同じ考え:
 * <ul>
 * <li>飛んでいない間: 能力使用モードで、空中でジャンプを{@link ClientWhiteWingFlight#HOLD_TICKS}押し続けたら（能力設定の「離陸・停止操作」が
 *     2回押しなら、空中でジャンプを2回押したら）、サーバーへ離陸を頼む。地上・エリトラ・クリエイティブの飛行・騎乗・観戦では頼まない（水中・溶岩の中からは頼む）。</li>
 * <li>飛んでいる間: 長押しの設定ならスニークの2回押し、2回押しの設定ならジャンプの2回押しで停止を頼む。下向きに動いて地面にぶつかった（着地）ときも
 *     停止を頼む。ジェット・エンジンは、ウィンドウのフォーカスが外れたときも停止を頼む（キーで始めたときの安全な停止と同じ）。</li>
 * </ul>
 * どの技能をパッシブにしているかは、サーバーから届いた能力設定の値（{@link ClientSkillSettings}）で知る。可否・費用・状態はサーバーが決め、
 * 飛んでいるかはそれぞれの技能の状態（ストームウィングは{@link ClientStormWing#state()}、ジェット・エンジンは{@link ClientSkillModes}）で見る。
 */
@Mod.EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class ClientPassiveFlight {
    private static int jumpHeld, flightAge;
    private static boolean takeoffSent, stopSent, wasFlying, wasJump, wasSneak, jumpDouble, sneakDouble;
    private static int sinceJump = 100, sinceSneak = 100;
    private ClientPassiveFlight() { }

    /** その技能を、サーバーの値でパッシブにしているか。 */
    public static boolean passive(ResourceLocation skill) {
        return PassiveSkills.PASSIVE.equals(ClientSkillSettings.value(PassiveSkills.setting(skill)));
    }
    /**
     * 1.5.2: サーバーが伝えた枠の番号が、その技能を指すか。{@link PassiveSkills#SLOT}はパッシブの自動の起動で、その技能をパッシブにしていて
     * 習得済みのとき。それ以外は、そのプリセットのその枠にその技能があるとき。
     */
    public static boolean holds(@Nullable PlayerAbilityData data, ResourceLocation skill, int preset, int slot) {
        if (data == null) return false;
        if (slot == PassiveSkills.SLOT) return passive(skill) && data.hasLearned(skill);
        return slot >= 0 && slot < PlayerAbilityData.SLOT_COUNT && skill.equals(data.getSlot(preset, slot));
    }
    /** 今のプレイヤーがパッシブにしている飛行の技能（今の系統で習得済みのもの。無ければnull）。 */
    public static @Nullable ResourceLocation skill(@Nullable PlayerAbilityData data) {
        if (data == null || !data.hasAbility()) return null;
        for (var skill : new ResourceLocation[] {StormWing.ID, JetEngine.ID})
            if (passive(skill) && data.hasLearned(skill) && data.getAbility().equals(AbilitySettingsVisibility.categoryOf(skill))) return skill;
        return null;
    }
    /** その飛行の技能で、今飛んでいるか（ストームウィングは溜めを含む）。 */
    public static boolean flying(ResourceLocation skill) {
        return StormWing.ID.equals(skill) ? ClientStormWing.state() >= 0 : ClientSkillModes.active(skill);
    }
    private static boolean doubleTapMode() {
        return AbilitySettings.TAKEOFF_DOUBLE.equals(ClientSkillSettings.value(AbilitySettings.FLIGHT_TAKEOFF));
    }
    /** 押し始めを数え、このtickでジャンプ・スニークが2回押しになったかを決める（{@link ClientWhiteWingFlight}と同じ）。 */
    private static void watchTaps(boolean jump, boolean sneak) {
        jumpDouble = false; sneakDouble = false;
        if (jump && !wasJump) { jumpDouble = sinceJump <= ClientWhiteWingFlight.DOUBLE_TAP_TICKS; sinceJump = jumpDouble ? 100 : 0; }
        else if (sinceJump < 100) sinceJump++;
        if (sneak && !wasSneak) { sneakDouble = sinceSneak <= ClientWhiteWingFlight.DOUBLE_TAP_TICKS; sinceSneak = sneakDouble ? 100 : 0; }
        else if (sinceSneak < 100) sinceSneak++;
        wasJump = jump; wasSneak = sneak;
    }
    private static void send(boolean takeoff) {
        if (Minecraft.getInstance().getConnection() != null) AcademyNetwork.CHANNEL.sendToServer(new PassiveFlightInput(takeoff));
    }

    @SubscribeEvent public static void passiveFlightOnClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        var client = Minecraft.getInstance(); var player = client.player;
        if (player == null) return;
        var data = player.getCapability(AbilityCapabilities.PLAYER_ABILITY).orElse(null);
        var skill = skill(data);
        if (skill == null) { reset(); return; }
        boolean control = !SteeringGate.suspended(client.screen != null, TerminalHud.isOpen(), client.isWindowActive());
        boolean jump = control && client.options.keyJump.isDown();
        watchTaps(jump, control && client.options.keyShift.isDown());
        boolean flying = flying(skill);
        if (flying != wasFlying) {
            // 飛行の始まりと終わり: それまでの押下を連打に数えず、終わったときにジャンプを押したままなら、一度離すまで次の離陸を頼まない。
            wasFlying = flying; flightAge = 0; stopSent = false; jumpHeld = 0; takeoffSent = jump;
            sinceJump = sinceSneak = 100;
        }
        boolean doubleTap = doubleTapMode();
        if (flying) {
            flightAge++;
            if (stopSent) return;
            boolean stop = doubleTap ? jumpDouble : sneakDouble;
            boolean landed = flightAge > WhiteWingFlight.LAND_GRACE_TICKS && player.verticalCollisionBelow;
            boolean unfocused = JetEngine.ID.equals(skill) && !client.isWindowActive();
            if (stop || landed || unfocused) { stopSent = true; send(false); }
            return;
        }
        if (!data.isActive()) { jumpHeld = 0; return; }
        if (doubleTap) {
            jumpHeld = 0;
            if (!jumpDouble) return;
        } else {
            if (!jump) { jumpHeld = 0; takeoffSent = false; return; }
            jumpHeld++;
            if (takeoffSent || jumpHeld < ClientWhiteWingFlight.HOLD_TICKS) return;
        }
        // 1.5.2: 水中・溶岩の中からは離陸できる（地上・エリトラ・クリエイティブの飛行・騎乗・観戦では頼まない）。
        if (player.onGround() || player.isFallFlying() || player.getAbilities().flying
                || player.isPassenger() || player.isSpectator()) return;
        takeoffSent = true;
        send(true);
    }
    private static void reset() {
        jumpHeld = 0; flightAge = 0; takeoffSent = stopSent = wasFlying = false;
    }
    @SubscribeEvent public static void passiveFlightOnLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) { reset(); }
}
