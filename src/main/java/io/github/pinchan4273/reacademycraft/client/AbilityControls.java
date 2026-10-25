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
    /**
     * 能力設定の画面を直接開く（1.4.0のS1、原作に無い）。既定は割り当てなし（司令室11のD6）。割り当てた後の扱いは、編集のキーと同じ
     * （画面・ポーズ・フォーカスの喪失を跨いで押していた押下は、離されるまで使わない）。データ端末の「能力設定」アプリからも同じ画面を開く。
     */
    public static final KeyMapping SETTINGS = key("ability_settings", GLFW.GLFW_KEY_UNKNOWN);
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
        if (mapping == TOGGLE || mapping == PRESET || mapping == EDITOR || mapping == TERMINAL || mapping == SETTINGS) return true;
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
            event.register(TOGGLE); event.register(PRESET); event.register(EDITOR); event.register(TERMINAL); event.register(SETTINGS);
            for (var key : SLOTS) event.register(key);
        }
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var client = Minecraft.getInstance();
        boolean canSend = client.player != null && client.screen == null && !client.isPaused() && client.isWindowActive();
        // TOGGLE-GUI-001: 操作できない間（画面・ポーズ・フォーカスの喪失。技能の枠はデータ端末の表示中も）を見たキーは、
        // 物理的なキーが離されるまで新しい発動に使わない（SkillKeyGate）。閉じた直後のキーリピートのクリックもここで捨てる。
        // F03（P-C′＋M2）: フォーカスが戻った直後は、離されて見えるキーもリピートを待っているだけかもしれない。フォーカスの喪失の印の
        // あるキーは、本当の離しを見るまで使わない（SkillKeyGate.Latch・SteeringGate.FocusMark。時間では解かない）。
        // QA 3回目（TOGGLE-QA3-F02）: データ端末は画面ではないので、端末の中の能力・プリセット・編集のキーは従来どおり働く。
        // ただし端末の開閉を跨いで押していたキーは、閉じた（開いた）後の操作に持ち越さない（開閉の境目を、操作できない間と同じに扱う）。
        // 端末のキー自身は対象にしない（押したままのリピートで開閉を繰り返すのは、従来の動き）。
        // QA 4回目（TOGGLE-QA4-F02-R1）: 境目は、気づいた時点（キーのイベントの前、またはこのtickの初め）で一度だけ適用する。
        // tickの初めに気づく場合も、その前に気づいたイベントの後の入力（閉じた後の新しい押下）は消さない。
        terminalBoundary(client, null, -1);
        boolean terminalOpen = TerminalHud.isOpen();
        EDITOR_LATCH.update(!canSend, physical(client, EDITOR));
        SETTINGS_LATCH.update(!canSend, physical(client, SETTINGS));
        TERMINAL_LATCH.update(!canSend, physical(client, TERMINAL));
        TOGGLE_LATCH.update(!canSend, physical(client, TOGGLE));
        PRESET_LATCH.update(!canSend, physical(client, PRESET));
        for (int i = 0; i < 4; i++) SLOT_LATCHES[i].update(!canSend || terminalOpen, physical(client, SLOTS[i]));
        // F03: このtickのフォーカスを印へ知らせる（キーボードのキーは、フォーカスの無いtickで印を付ける。マウスのボタンは付けない）。
        boolean windowActive = client.isWindowActive();
        EDITOR_LATCH.focusTick(windowActive, keyboard(EDITOR));
        SETTINGS_LATCH.focusTick(windowActive, keyboard(SETTINGS));
        TERMINAL_LATCH.focusTick(windowActive, keyboard(TERMINAL));
        TOGGLE_LATCH.focusTick(windowActive, keyboard(TOGGLE));
        PRESET_LATCH.focusTick(windowActive, keyboard(PRESET));
        for (int i = 0; i < 4; i++) SLOT_LATCHES[i].focusTick(windowActive, keyboard(SLOTS[i]));
        boolean open = false;
        while (EDITOR.consumeClick()) open = true;
        // 原作keyEditPresetは、能力系統を持つプレイヤーだけに編集画面を開く。
        var holder = client.player == null ? null : client.player.getCapability(AbilityCapabilities.PLAYER_ABILITY).orElse(null);
        if (open && canSend && EDITOR_LATCH.usable() && client.player.isAlive() && holder != null && holder.hasAbility()) {
            client.setScreen(new PresetEditorScreen()); canSend = false;
        }
        boolean settings = false;
        while (SETTINGS.consumeClick()) settings = true;
        // 能力設定の画面（1.4.0のS1）。能力系統が無くても開ける（設定はサーバーに保存され、習得した後に効く。コマンドと同じ）。
        if (settings && canSend && SETTINGS_LATCH.usable() && client.player.isAlive()) {
            client.setScreen(new AbilitySettingsScreen(null)); canSend = false;
        }
        boolean terminal = false;
        while (TERMINAL.consumeClick()) terminal = true;
        if (terminal && canSend && TERMINAL_LATCH.usable() && client.player.isAlive()) {
            TerminalHud.toggle(); canSend = false;
            // このtickに開閉した: このtickの後のキーの処理から、境目を跨いだキーとして扱う。
            terminalBoundary(client, null, -1);
        }
        // 原作のactivateの処理（Flashing・Vector Deviation・Storm Wing）: 技能のモードがONの間、能力キーは
        // 能力をOFFにせず、そのモードを終える（その技能のキーで切り替えるのと同じ）。
        int mode = modeSlot(client);
        // 原作ClientRuntime自身のactivateの処理: 技能のキーが押されている間、能力キーはそれらの技能を中断し、
        // 能力はONのまま残す（"endskill"）。押されている技能が無いときだけ能力を切り替える。
        // 原作keyActivate: 押した時点では数値の表示を始めるだけ（CpBarHud）。キーは離したときに、
        // しかも0.3秒以内のときだけ動作する。そのため、押し続けるとCPと過負荷を表示するだけで何も切り替えない。
        // 合成のクリック（KeyMapping.click）は押し続けられないので、すぐ離した軽い押下として扱われる。
        while (TOGGLE.consumeClick()) if (toggleDownNanos < 0 && TOGGLE_LATCH.usable()) toggleDownNanos = System.nanoTime();
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
        consume(PRESET, new AbilityAction(AbilityAction.NEXT_PRESET, 0), canSend && PRESET_LATCH.usable() && owner != null && owner.isActive());
        heartbeatTicks++;
        for (int i = 0; i < 4; i++) {
            var data = client.player == null ? null : client.player.getCapability(AbilityCapabilities.PLAYER_ABILITY).orElse(null);
            boolean wasDown = WAS_DOWN[i]; WAS_DOWN[i] = SLOTS[i].isDown();
            // 原作のLocation Teleportのキーは、技能を発動せずに一覧を開く。他の技能のキーと同じく、
            // canUseAbilityが成り立つ間（ON、過負荷でない、干渉されていない）だけ動作する。
            if (data != null && io.github.pinchan4273.reacademycraft.skill.LocationTeleport.ID.equals(data.getSlot(data.getCurrentPreset(), i))) {
                boolean pressed = false;
                while (SLOTS[i].consumeClick()) pressed = true;
                if (pressed && canSend && SLOT_LATCHES[i].usable() && data.canUseAbility()) { client.setScreen(new LocationTeleportScreen()); canSend = false; }
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
            boolean clicked = false;
            while (SLOTS[i].consumeClick()) clicked = true;
            // 切り替え型の新しい押下の扱い、中断されたキー（原作realState）、押し続けの心拍・離し・中断、キューに入った
            // クリックの離しは、SkillKeyGate.slotが従来と同じ順序と条件で決める。古いキーはCASTも押し続けの開始も送らない。
            // コインのQTEはCASTの時点で判定済みなので、クリックの後の離しは影響しない。
            // 古いキーが離されたtickのクリックは使わない。離してから押し直したクリックは、新しい押下として扱う（SkillKeyGate.Latch）。
            // 新しい押下は、前のtickの押下の状態も中断の印（離されるまで何もしない）も引き継がない（離されたことをイベントで見ている）。
            var latch = SLOT_LATCHES[i];
            var result = SkillKeyGate.slot(new SkillKeyGate.Input(canSend, TerminalHud.isOpen(), SLOTS[i].isDown(), wasDown && !latch.newPress(), clicked,
                    !latch.usable(), AbilityAction.toggled(skill), data != null && AbilityAction.held(skill), data != null && data.isActive(),
                    blocked, client.player != null, heartbeatTicks % 5 == 0, HELD[i], ABORTED[i] && !latch.newPress()));
            for (var send : result.sends()) AcademyNetwork.CHANNEL.sendToServer(new AbilityAction(switch (send) {
                case CAST -> AbilityAction.CAST; case HELD -> AbilityAction.HELD;
                case RELEASE -> AbilityAction.RELEASE; case CANCEL -> AbilityAction.CANCEL;
            }, i));
            HELD[i] = result.held(); ABORTED[i] = result.aborted();
        }
        safeStopThrust(client);
        safeStopIntensify(client);
    }
    private static final boolean[] WAS_DOWN = new boolean[4];
    private static final SkillKeyGate.Latch EDITOR_LATCH = new SkillKeyGate.Latch(), SETTINGS_LATCH = new SkillKeyGate.Latch(),
            TERMINAL_LATCH = new SkillKeyGate.Latch(),
            TOGGLE_LATCH = new SkillKeyGate.Latch(), PRESET_LATCH = new SkillKeyGate.Latch();
    private static final SkillKeyGate.Latch[] SLOT_LATCHES = { new SkillKeyGate.Latch(), new SkillKeyGate.Latch(),
            new SkillKeyGate.Latch(), new SkillKeyGate.Latch() };
    /** 最後に境目を適用したときに、データ端末が開いていたか（TOGGLE-QA3-F02・QA4-F02-R1の開閉の境目）。 */
    private static boolean lastTerminalOpen;
    /** F03: キーボードのキーか（P-C′の印を付ける）。マウスのボタン（M2）はfalse。 */
    private static boolean keyboard(KeyMapping mapping) { return mapping.getKey().getType() != InputConstants.Type.MOUSE; }
    /**
     * QA 3回目（TOGGLE-QA3-F01）: GLFWのキーのイベント（押下・リピート・離し）を、能力のキーのラッチへ知らせる。
     * Forgeは、画面が受け取らなかったキーのイベントを、ゲームの処理（{@code KeyMapping.set}・{@code click}）の後にここへ届ける。
     * QA 5回目（TOGGLE-QA5-F01-B）: 優先度HIGHESTは、このイベントの購読者の中の順番にすぎない。画面のキーの処理
     * （{@code Screen.keyPressed}・{@code keyReleased}）はこのイベントより前に終わっていて、その中で画面が閉じることもある。
     * そのため、画面が開いている間のキーは{@link #screenKeyPressed}・{@link #screenKeyReleased}（画面の処理の前）で見て、
     * 同じイベントがここへも届いたとき（画面が受け取らなかった）は二度数えない（{@link #keyEvent}）。
     */
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void keyInput(net.minecraftforge.client.event.InputEvent.Key event) {
        keyEvent(InputConstants.getKey(event.getKey(), event.getScanCode()), event.getAction());
    }
    /**
     * QA 5回目（TOGGLE-QA5-F01-B）: 画面が開いている間のキーの押下・リピート。Forgeは、画面が受け取るかどうかに
     * かかわらず、画面の処理より前にここへ届ける（{@code KeyboardHandler.keyPress}の{@code onScreenKeyPressedPre}）。
     * このイベントは押下とリピートを区別しないが、どちらも「キーが押されている」ことなので、押下として知らせる。
     */
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST, receiveCanceled = true)
    public static void screenKeyPressed(net.minecraftforge.client.event.ScreenEvent.KeyPressed.Pre event) {
        screenInput(InputConstants.getKey(event.getKeyCode(), event.getScanCode()), SkillKeyGate.PRESS);
    }
    /** 画面が開いている間のキーの離し。画面が受け取るかどうかにかかわらず、画面の処理より前に届く（{@code onScreenKeyReleasedPre}）。 */
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST, receiveCanceled = true)
    public static void screenKeyReleased(net.minecraftforge.client.event.ScreenEvent.KeyReleased.Pre event) {
        screenInput(InputConstants.getKey(event.getKeyCode(), event.getScanCode()), SkillKeyGate.RELEASE);
    }
    /**
     * QA 5回目（TOGGLE-QA5-F01-A）: 画面が開く直前（取り消されなかったもの。{@code Minecraft.setScreen}）に、全てのラッチを
     * その時点から操作できない間にする（次のtickを待たない）。編集画面・Location Teleportの一覧・ポーズ画面等、どの画面も通る。
     */
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void screenOpening(net.minecraftforge.client.event.ScreenEvent.Opening event) {
        opening(event.getNewScreen());
    }
    /**
     * QA 7回目（TOGGLE-QA7-F01-F2）: 他の購読者が取り消さずに新しい画面をnullにした（{@code setNewScreen(null)}）ときは、
     * 画面は開かないので止めない（止めると、溜まっていた正常な軽い押下を1回失う）。この購読者より後の同じ優先度の購読者が
     * 変えた場合までは確かめられない。
     */
    static void opening(net.minecraft.client.gui.screens.Screen newScreen) {
        if (newScreen != null) suspendAll(Minecraft.getInstance());
    }
    /** 画面の経路で最後に見たキーと、押下（true）か離し（false）か。直後に同じイベントがInputEvent.Keyへ届いたら数えない。 */
    private static InputConstants.Key screenSeen;
    private static boolean screenSeenDown;
    static void screenInput(InputConstants.Key key, int action) {
        screenSeen = key; screenSeenDown = action != SkillKeyGate.RELEASE;
        input(key, action);
    }
    /**
     * InputEvent.Keyで届いたイベント。直前に画面の経路で見た同じイベント（同じキー・同じ押下か離し）なら数えない。
     * 画面が受け取ったイベントにはInputEvent.Keyが来ないので、取り違えるのは、その後の同じキーのリピート（ラッチは数えない）だけ。
     */
    static void keyEvent(InputConstants.Key key, int action) {
        boolean seen = key.equals(screenSeen) && screenSeenDown == (action != SkillKeyGate.RELEASE);
        screenSeen = null;
        if (!seen) input(key, action);
    }
    /** 全てのラッチを、今の物理的な状態で、操作できない間にする。能力キーの0.3秒の計時も消す。 */
    static void suspendAll(Minecraft client) {
        EDITOR_LATCH.update(true, physical(client, EDITOR));
        SETTINGS_LATCH.update(true, physical(client, SETTINGS));
        TERMINAL_LATCH.update(true, physical(client, TERMINAL));
        TOGGLE_LATCH.update(true, physical(client, TOGGLE));
        PRESET_LATCH.update(true, physical(client, PRESET));
        for (int i = 0; i < 4; i++) SLOT_LATCHES[i].update(true, physical(client, SLOTS[i]));
        toggleDownNanos = -1;
    }
    /**
     * マウスのボタンのイベント。Forgeは、画面の有無にかかわらず、ゲームの処理の前にここへ届ける（取り消されたものも見る）。
     * QA 4回目（TOGGLE-QA4-F01-R1）: データ端末は左ボタンの離しでアプリを起動して閉じる（周波数送信機）ので、その前の状態で見る
     * （優先度HIGHEST）。
     */
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST, receiveCanceled = true)
    public static void mouseInput(net.minecraftforge.client.event.InputEvent.MouseButton.Pre event) {
        input(InputConstants.Type.MOUSE.getOrCreate(event.getButton()), event.getAction());
    }
    /**
     * 割り当てが同じキーなら、すべてのラッチへ知らせる。フォーカスの有無は、合成の離しを見分けるために渡す。
     * QA 4回目（TOGGLE-QA4-F01-R1・F02-R1）: 前に境目を適用した後にデータ端末が開閉していれば（Altによらない閉鎖も）、
     * このイベントより前に境目を適用する。イベントが届いた時点で操作できたか（画面・ポーズ・フォーカス、技能の枠は端末も）を渡し、
     * 操作できない間の押下を、閉じた後に新しい押下として使わない。
     */
    static void input(InputConstants.Key key, int action) {
        var client = Minecraft.getInstance();
        boolean active = client.isWindowActive();
        // F03: Storm Wing・Flashingが読む方向入力（前後左右）の印にも、同じイベントを知らせる。
        ClientFocusMarks.event(key, action, active);
        terminalBoundary(client, key, action);
        boolean operable = client.player != null && client.screen == null && !client.isPaused() && active;
        boolean slotOperable = operable && !TerminalHud.isOpen();
        if (key.equals(EDITOR.getKey())) latchEvent(client, EDITOR_LATCH, EDITOR, key, action, active, operable);
        if (key.equals(SETTINGS.getKey())) latchEvent(client, SETTINGS_LATCH, SETTINGS, key, action, active, operable);
        if (key.equals(TERMINAL.getKey())) latchEvent(client, TERMINAL_LATCH, TERMINAL, key, action, active, operable);
        if (key.equals(TOGGLE.getKey())) latchEvent(client, TOGGLE_LATCH, TOGGLE, key, action, active, operable);
        if (key.equals(PRESET.getKey())) latchEvent(client, PRESET_LATCH, PRESET, key, action, active, operable);
        for (int i = 0; i < 4; i++) if (key.equals(SLOTS[i].getKey())) latchEvent(client, SLOT_LATCHES[i], SLOTS[i], key, action, active, slotOperable);
    }
    /**
     * QA 5回目（TOGGLE-QA5-F01-A）: まだ古くないラッチに、操作できない間のイベントが届いた（画面・端末が開いた後、次のtickの前）
     * なら、そのイベントの前の状態で、その時点から操作できない間にしてから数える。そうしないと、このイベントの押下を古い押下として
     * 記録できず、次のtickの前に閉じたときに、閉じる前の押下が新しい押下として使われる。
     */
    private static void latchEvent(Minecraft client, SkillKeyGate.Latch latch, KeyMapping mapping, InputConstants.Key key, int action,
                                   boolean active, boolean operable) {
        if (!latch.stale() && !operable && active) latch.update(true, downBefore(client, mapping, key, action));
        latch.event(action, active, operable);
    }
    /**
     * データ端末の開閉の境目を、能力・プリセット・編集のキーへ一度だけ適用する（前に適用した時から開閉の状態が変わっていれば）。
     * 境目の前に押していたキーは離されるまで使わず、能力キーの0.3秒の計時も消す。境目の後に届いたイベントは、そのまま数える。
     * {@code key}・{@code action}は、今届いているイベント（tickから呼ぶときはnull）。そのキーの境目の時点の状態は、
     * イベントの前の状態（PRESSなら離れていた、RELEASE・REPEATなら押していた）。他のキーはGLFWの状態で読む。
     */
    private static void terminalBoundary(Minecraft client, InputConstants.Key key, int action) {
        boolean now = TerminalHud.isOpen();
        if (now == lastTerminalOpen) return;
        lastTerminalOpen = now;
        EDITOR_LATCH.update(true, downBefore(client, EDITOR, key, action));
        SETTINGS_LATCH.update(true, downBefore(client, SETTINGS, key, action));
        TOGGLE_LATCH.update(true, downBefore(client, TOGGLE, key, action));
        PRESET_LATCH.update(true, downBefore(client, PRESET, key, action));
        toggleDownNanos = -1;
        // QA 5回目（TOGGLE-QA5-F01-A）: 端末が開いた: 技能の枠も、その時点から操作できない間にする（次のtickを待たない）。
        // 閉じたときは何もしない（技能の枠は、イベントの時点の端末の状態で、閉じる前の押下と後の押下を分ける）。
        if (now) for (int i = 0; i < 4; i++) SLOT_LATCHES[i].update(true, downBefore(client, SLOTS[i], key, action));
    }
    private static boolean downBefore(Minecraft client, KeyMapping mapping, InputConstants.Key key, int action) {
        return key != null && key.equals(mapping.getKey()) ? action != SkillKeyGate.PRESS : physical(client, mapping);
    }
    /**
     * TOGGLE-GUI-001: GLFWが報告する物理的なキー・ボタンの状態。{@code KeyMapping.isDown}と違い、画面の中でも、
     * 合成の押下（試験の{@code setDown}）にも左右されない。画面を閉じたときの{@code KeyMapping.setAll}もこれを読む。
     */
    private static boolean physical(Minecraft client, KeyMapping mapping) {
        var key = mapping.getKey();
        long window = client.getWindow().getWindow();
        return switch (key.getType()) {
            case KEYSYM -> key.getValue() != InputConstants.UNKNOWN.getValue() && InputConstants.isKeyDown(window, key.getValue());
            case MOUSE -> key.getValue() >= 0 && GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
            default -> mapping.isDown();
        };
    }
    private static boolean thrustStopSent;
    /**
     * Jet Engineは術者の視線の方向へ推進する。TOGGLE-GUI-001: 画面・端末・ポーズを開いただけでは終えず、元の視線の方向への
     * 推進を続ける（GUIの間は視線が変わらない。視線を作り出すことはしない）。ウィンドウのフォーカスが外れたとき
     * （GUIの表示とは別のできごと）だけ、従来どおり一度だけ終え、キーでまた開始する。
     */
    private static void safeStopThrust(Minecraft client) {
        if (!ClientSkillModes.active(io.github.pinchan4273.reacademycraft.skill.JetEngine.ID)) { thrustStopSent = false; return; }
        if (thrustStopSent || client.player == null || client.isWindowActive()) return;
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
     * TOGGLE-GUI-001: Body Intensifyの予備動作も強化の効果も、画面・端末・ポーズを開いただけでは終わらない。
     * ウィンドウのフォーカスが外れたとき（GUIの表示とは別のできごと）だけ、従来どおり一度だけ終え、キーでまた開始する。
     */
    private static void safeStopIntensify(Minecraft client) {
        boolean warming = ClientBodyIntensify.warmingUp(), running = ClientBodyIntensify.running();
        if (!warming && !running) { intensifyStopSent = false; return; }
        if (intensifyStopSent || client.player == null) return;
        boolean stop = !client.isWindowActive();
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
        while (SETTINGS.consumeClick()) { }
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
