package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities;
import io.github.pinchan4273.reacademycraft.network.AbilityAction;
import io.github.pinchan4273.reacademycraft.network.AcademyNetwork;
import io.github.pinchan4273.reacademycraft.skill.Railgun;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/** 原作の能力のキー（ACKeyManager・ClientHandler）。HUDと端末の描画は、CpBarHud・KeyHintHud・TerminalHudが行う。 */
@Mod.EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class AbilityControls {
    // 原作の既定値（ClientHandler・TerminalUI）: 能力はV、プリセットの切り替えはC、編集はN、データ端末は左Alt、
    // 4つの技能は左右のマウスボタン・R・F。
    public static final KeyMapping TOGGLE = key("toggle", GLFW.GLFW_KEY_V);
    public static final KeyMapping PRESET = key("preset", GLFW.GLFW_KEY_C);
    public static final KeyMapping EDITOR = key("editor", GLFW.GLFW_KEY_N);
    /** 原作は、データ端末を専用のキーで開く。 */
    public static final KeyMapping TERMINAL = key("terminal", GLFW.GLFW_KEY_LEFT_ALT);
    public static final KeyMapping[] SLOTS = {mouse("slot1", GLFW.GLFW_MOUSE_BUTTON_LEFT), mouse("slot2", GLFW.GLFW_MOUSE_BUTTON_RIGHT),
            key("slot3", GLFW.GLFW_KEY_R), key("slot4", GLFW.GLFW_KEY_F)};
    private static final boolean[] HELD = new boolean[4];
    /** 原作keyActivate.lastKeyDown: 能力キーを押した時刻。離されるのを待っていないときは-1。 */
    private static long toggleDownNanos = -1;
    /** 原作の「軽く押す」操作: 0.3秒以内に離すと動作する。それより長く押すと、CPの数値を表示するだけ。 */
    static final long TAP_NANOS = 300_000_000L;
    /** 押し続け型の技能が中断された、または開始できない状態で押された: キーを離すまで何もしない。 */
    private static final boolean[] ABORTED = new boolean[4];
    /** この枠の押し続け型の技能が押されているか（キーの案内の表示用）。 */
    static boolean held(int slot) { return slot >= 0 && slot < 4 && HELD[slot]; }
    private static int heartbeatTicks;
    private AbilityControls() {}
    private static KeyMapping key(String name, int code) {
        return new KeyMapping("key.academy." + name, KeyConflictContext.IN_GAME,
                InputConstants.Type.KEYSYM, code, "key.categories.academy");
    }
    private static KeyMapping mouse(String name, int button) {
        return new KeyMapping("key.academy." + name, KeyConflictContext.IN_GAME,
                InputConstants.Type.MOUSE, button, "key.categories.academy");
    }
    /**
     * 原作ClientRuntime.rebuildOverrides: 能力がONの間、現在のプリセットに技能が割り当てられたキーは
     * その技能専用になり（ControlOverrider）、ゲーム本来の操作は待たされる。
     */
    public static java.util.Set<InputConstants.Key> overridden(Minecraft client) {
        var data = client.player == null ? null : client.player.getCapability(AbilityCapabilities.PLAYER_ABILITY).orElse(null);
        if (data == null || !data.isActive()) return java.util.Set.of();
        var keys = new java.util.HashSet<InputConstants.Key>();
        for (int i = 0; i < 4; i++) if (data.getSlot(data.getCurrentPreset(), i) != null) keys.add(SLOTS[i].getKey());
        return keys;
    }
    static boolean ours(KeyMapping mapping) {
        if (mapping == TOGGLE || mapping == PRESET || mapping == EDITOR || mapping == TERMINAL) return true;
        for (var key : SLOTS) if (mapping == key) return true;
        return false;
    }
    /** 技能が割り当てられたマウスボタンでは、ゲーム本来の攻撃・使用・ブロック選択を行わず、腕も振らない。 */
    @SubscribeEvent public static void interaction(net.minecraftforge.client.event.InputEvent.InteractionKeyMappingTriggered event) {
        if (overridden(Minecraft.getInstance()).contains(event.getKeyMapping().getKey())) {
            event.setCanceled(true); event.setSwingHand(false);
        }
    }
    /** ゲームがこのtickのキーを処理する前に、技能が割り当てられたキーにあるゲーム本来の割り当ての押下を消す。 */
    @SubscribeEvent public static void override(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        var client = Minecraft.getInstance();
        var keys = overridden(client);
        if (keys.isEmpty()) return;
        for (var mapping : client.options.keyMappings)
            if (!ours(mapping) && keys.contains(mapping.getKey()) && mapping != client.options.keyAttack && mapping != client.options.keyUse) {
                while (mapping.consumeClick()) { }
                mapping.setDown(false);
            }
    }
    @Mod.EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void keys(RegisterKeyMappingsEvent event) {
            event.register(TOGGLE); event.register(PRESET); event.register(EDITOR); event.register(TERMINAL);
            for (var key : SLOTS) event.register(key);
        }
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var client = Minecraft.getInstance();
        boolean canSend = client.player != null && client.screen == null && !client.isPaused() && client.isWindowActive();
        boolean open = false;
        while (EDITOR.consumeClick()) open = true;
        // 原作keyEditPresetは、能力系統を持つプレイヤーだけに編集画面を開く。
        var holder = client.player == null ? null : client.player.getCapability(AbilityCapabilities.PLAYER_ABILITY).orElse(null);
        if (open && canSend && client.player.isAlive() && holder != null && holder.hasAbility()) {
            client.setScreen(new PresetEditorScreen()); canSend = false;
        }
        boolean terminal = false;
        while (TERMINAL.consumeClick()) terminal = true;
        if (terminal && canSend && client.player.isAlive()) { TerminalHud.toggle(); canSend = false; }
        // 原作のactivateの処理（Flashing・Vector Deviation・Storm Wing）: 技能のモードがONの間、能力キーは
        // 能力をOFFにせず、そのモードを終える（その技能のキーで切り替えるのと同じ）。
        int mode = modeSlot(client);
        // 原作ClientRuntime自身のactivateの処理: 技能のキーが押されている間、能力キーはそれらの技能を中断し、
        // 能力はONのまま残す（"endskill"）。押されている技能が無いときだけ能力を切り替える。
        // 原作keyActivate: 押した時点では数値の表示を始めるだけ（CpBarHud）。キーは離したときに、
        // しかも0.3秒以内のときだけ動作する。そのため、押し続けるとCPと過負荷を表示するだけで何も切り替えない。
        // 合成のクリック（KeyMapping.click）は押し続けられないので、すぐ離した軽い押下として扱われる。
        while (TOGGLE.consumeClick()) if (toggleDownNanos < 0) toggleDownNanos = System.nanoTime();
        boolean tapped = false;
        if (toggleDownNanos >= 0 && !TOGGLE.isDown()) {
            tapped = System.nanoTime() - toggleDownNanos < TAP_NANOS;
            toggleDownNanos = -1;
        }
        if (mode < 0 && anyHeld()) {
            if (tapped && canSend) abortHeld();
        } else if (tapped && canSend) AcademyNetwork.CHANNEL.sendToServer(mode >= 0 ? new AbilityAction(AbilityAction.CAST, mode)
                : new AbilityAction(AbilityAction.TOGGLE, 0));
        // 原作keySwitchPresetは、能力がOFFの間は何もしない。サーバー側でも確かめる。
        var owner = client.player == null ? null : client.player.getCapability(AbilityCapabilities.PLAYER_ABILITY).orElse(null);
        consume(PRESET, new AbilityAction(AbilityAction.NEXT_PRESET, 0), canSend && owner != null && owner.isActive());
        heartbeatTicks++;
        for (int i = 0; i < 4; i++) {
            var data = client.player == null ? null : client.player.getCapability(AbilityCapabilities.PLAYER_ABILITY).orElse(null);
            boolean wasDown = WAS_DOWN[i]; WAS_DOWN[i] = SLOTS[i].isDown();
            // 原作のLocation Teleportのキーは、技能を発動せずに一覧を開く。他の技能のキーと同じく、
            // canUseAbilityが成り立つ間（ON、過負荷でない、干渉されていない）だけ動作する。
            if (data != null && io.github.pinchan4273.reacademycraft.skill.LocationTeleport.ID.equals(data.getSlot(data.getCurrentPreset(), i))) {
                boolean pressed = false;
                while (SLOTS[i].consumeClick()) pressed = true;
                if (pressed && canSend && data.canUseAbility()) { client.setScreen(new LocationTeleportScreen()); canSend = false; }
                HELD[i] = false;
                continue;
            }
            var skill = data == null ? null : data.getSlot(data.getCurrentPreset(), i);
            // 原作ClientRuntime.tickのshouldAbort: 能力を使えない間（OFF・過負荷・干渉中）、技能のクールダウン中、
            // 端末を開いている間は、どの技能のキーも動作しない。押し続けている技能は、そうなったtickで中断する。
            boolean blocked = data == null || !data.canUseAbility() || TerminalHud.isOpen()
                    || (skill != null && data.getCooldown(skill) > 0);
            // 切り替え型の技能は、新しく押されたときだけ受け付ける。押し続けるとクリックが繰り返され、
            // OFFとONを繰り返してしまうため。
            boolean fresh = !AbilityAction.toggled(skill) || !wasDown;
            boolean clicked = consume(SLOTS[i], new AbilityAction(AbilityAction.CAST, i), canSend && !(TerminalHud.isOpen()) && fresh);
            boolean heldSkill = data != null && AbilityAction.held(skill);
            // 中断されたキーは、離されるまで何もしない（原作realState）。
            if (!SLOTS[i].isDown()) ABORTED[i] = false;
            boolean down = canSend && SLOTS[i].isDown() && heldSkill && data.isActive() && !ABORTED[i];
            if (down && blocked) {
                // 押している間に使えなくなった: 中断する。既に使えない状態で押された: 開始しない。
                // 原作でも、キーを押すにはまず離されている必要がある。
                if (HELD[i]) AcademyNetwork.CHANNEL.sendToServer(new AbilityAction(AbilityAction.CANCEL, i));
                ABORTED[i] = true; HELD[i] = false; continue;
            }
            if (down && (!HELD[i] || heartbeatTicks % 5 == 0))
                AcademyNetwork.CHANNEL.sendToServer(new AbilityAction(AbilityAction.HELD, i));
            else if (!down && HELD[i] && client.player != null)
                AcademyNetwork.CHANNEL.sendToServer(new AbilityAction(canSend && data != null && data.isActive()
                        ? AbilityAction.RELEASE : AbilityAction.CANCEL, i));
            // 実際の押下と離しが、クライアントのtickの間に起こることがある。キューに入ったクリックは、
            // 押し続けている技能をすべて離す必要がある（leaseの期限まで有効のままにしない）。
            // コインのQTEはCASTの時点で判定済みなので、ここで離しても影響しない。
            if (clicked && !down && !HELD[i] && heldSkill)
                AcademyNetwork.CHANNEL.sendToServer(new AbilityAction(AbilityAction.RELEASE, i));
            HELD[i] = down;
        }
        safeStopThrust(client);
        safeStopIntensify(client);
    }
    private static final boolean[] WAS_DOWN = new boolean[4];
    private static boolean thrustStopSent;
    /**
     * Jet Engineは術者の視線の方向へ推進する。そのため、術者が操作できない間はONにしない。
     * 画面・端末・ポーズ・ウィンドウのフォーカスが外れたときに一度だけ終え、キーでまた開始する。
     */
    private static void safeStopThrust(Minecraft client) {
        if (!ClientSkillModes.active(io.github.pinchan4273.reacademycraft.skill.JetEngine.ID)) { thrustStopSent = false; return; }
        if (thrustStopSent || client.player == null
                || (client.screen == null && !TerminalHud.isOpen() && !client.isPaused() && client.isWindowActive())) return;
        var data = client.player.getCapability(AbilityCapabilities.PLAYER_ABILITY).orElse(null);
        if (data == null) return;
        for (int i = 0; i < 4; i++)
            if (io.github.pinchan4273.reacademycraft.skill.JetEngine.ID.equals(data.getSlot(data.getCurrentPreset(), i))) {
                AcademyNetwork.CHANNEL.sendToServer(new AbilityAction(AbilityAction.CANCEL, i));
                thrustStopSent = true;
                return;
            }
    }
    private static boolean intensifyStopSent;
    /**
     * Body Intensifyの予備動作は、画面・端末・ポーズ・ウィンドウのフォーカスが外れたときに終わる。
     * 強化の効果はどの画面の下でも続き、ウィンドウのフォーカスが外れたときだけ終わる。
     * いずれも一度だけで、キーでまた開始する。
     */
    private static void safeStopIntensify(Minecraft client) {
        boolean warming = ClientBodyIntensify.warmingUp(), running = ClientBodyIntensify.running();
        if (!warming && !running) { intensifyStopSent = false; return; }
        if (intensifyStopSent || client.player == null) return;
        boolean stop = warming ? client.screen != null || TerminalHud.isOpen() || client.isPaused() || !client.isWindowActive()
                : !client.isWindowActive();
        int slot = ClientBodyIntensify.slot();
        if (!stop || slot < 0) return;
        AcademyNetwork.CHANNEL.sendToServer(new AbilityAction(AbilityAction.CANCEL, slot));
        intensifyStopSent = true;
    }
    /**
     * 原作IActivateHandler.getHint: activateのキーが、能力の切り替え以外に何をするか。無ければnull。
     * 特殊な技能のモードを終えるか、キーが押されている技能を中断するか。
     */
    @javax.annotation.Nullable public static String activateHint(Minecraft client) {
        if (client.player == null) return null;
        return modeSlot(client) >= 0 ? "endspecial" : anyHeld() ? "endskill" : null;
    }
    private static boolean anyHeld() { for (boolean held : HELD) if (held) return true; return false; }
    /** 原作abortDelegates: 押し続けている技能をすべて中断し、そのキーは離されるまで何もしない。 */
    private static void abortHeld() {
        for (int i = 0; i < 4; i++) if (HELD[i]) {
            AcademyNetwork.CHANNEL.sendToServer(new AbilityAction(AbilityAction.CANCEL, i));
            HELD[i] = false; ABORTED[i] = true;
        }
    }
    private static int modeSlot(Minecraft client) {
        var data = client.player == null ? null : client.player.getCapability(AbilityCapabilities.PLAYER_ABILITY).orElse(null);
        if (data != null) for (int i = 0; i < 4; i++) {
            var skill = data.getSlot(data.getCurrentPreset(), i);
            if ((io.github.pinchan4273.reacademycraft.skill.Flashing.ID.equals(skill) && ClientFlashing.active())
                    || (io.github.pinchan4273.reacademycraft.skill.StormWing.ID.equals(skill) && ClientStormWing.state() >= 0)
                    || ClientSkillModes.active(skill)) return i;
        }
        return -1;
    }
    private static boolean consume(KeyMapping key, AbilityAction action, boolean canSend) {
        boolean clicked = false;
        while (key.consumeClick()) clicked = true;
        if (clicked && canSend) AcademyNetwork.CHANNEL.sendToServer(action);
        return clicked && canSend;
    }
    static void discardClicks() {
        var client = Minecraft.getInstance();
        for (int i = 0; i < 4; i++) {
            if (HELD[i] && client.player != null) AcademyNetwork.CHANNEL.sendToServer(new AbilityAction(AbilityAction.CANCEL, i));
            HELD[i] = false;
        }
        while (TOGGLE.consumeClick()) { }
        toggleDownNanos = -1;
        while (PRESET.consumeClick()) { }
        while (EDITOR.consumeClick()) { }
        for (var key : SLOTS) while (key.consumeClick()) { }
    }
    @SubscribeEvent public static void hud(RenderGuiEvent.Post event) {
        var client = Minecraft.getInstance();
        // 原作のHUDは、どの画面の下でもゲームのHUDと一緒に描かれ、半透明の開発機の画面から透けて見える。
        if (client.player == null || client.options.hideGui) return;
        client.player.getCapability(AbilityCapabilities.PLAYER_ABILITY).ifPresent(data -> {
            if (!data.hasAbility()) return;
            // 原作のCPと過負荷はCpBarHud、キーとクールダウンはKeyHintHudが描く。以前これらの代わりに置いていた
            // この移植の文字のパネルは無くした。残るのは、この移植独自のRailgunのコインの案内だけ。
            var graphics = event.getGuiGraphics();
            boolean railgunEquipped = java.util.stream.IntStream.range(0, 4).anyMatch(i -> Railgun.ID.equals(data.getSlot(data.getCurrentPreset(), i)));
            if (data.isActive() && railgunEquipped) {
                var coins = client.level.getEntitiesOfClass(io.github.pinchan4273.reacademycraft.world.entity.CoinEntity.class,
                        client.player.getBoundingBox().inflate(12), e -> e.owner().filter(client.player.getUUID()::equals).isPresent());
                if (!coins.isEmpty()) {
                    var coin = coins.get(0);
                    var text = Component.translatable(coin.railgunAttempted() ? "academy.hud.railgun_missed"
                            : coin.progress() > .7f ? "academy.hud.railgun_fire" : coin.progress() >= .6f ? "academy.hud.railgun_ready" : "academy.hud.railgun_wait");
                    int color = coin.railgunAttempted() ? 0xff7777 : coin.progress() > .7f ? 0x88ff99 : coin.progress() >= .6f ? 0xffdd66 : 0xffffff;
                    int textWidth = client.font.width(text);
                    graphics.fill(5, 5, 15 + textWidth, 23, 0xa0000000);
                    graphics.drawString(client.font, text, 10, 10, color);
                }
            }
        });
    }
}
