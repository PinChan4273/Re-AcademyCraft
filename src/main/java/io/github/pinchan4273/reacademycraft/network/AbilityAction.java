package io.github.pinchan4273.reacademycraft.network;

import io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import java.util.Map;
import java.util.WeakHashMap;

/** クライアントの意図だけを送る: 能力の値・対象・方向・コストはクライアントから受け取らない。 */
public record AbilityAction(int action, int slot) {
    public static final int TOGGLE = 0, CAST = 1, NEXT_PRESET = 2, HELD = 3, RELEASE = 4, CANCEL = 5;
    private static final Map<ServerPlayer, long[]> LAST_REQUEST = new WeakHashMap<>();
    /**
     * 各スロットの心拍を最後に受け取ったtick。心拍はtickに印を付けるだけなので、同じtickの2つ目以降は、押し続け技能を
     * すべてもう一度たどらずに捨てる。
     */
    private static final Map<ServerPlayer, long[]> LAST_HELD = new WeakHashMap<>();
    /**
     * キーを押し続けて使う技能のすべて。キーが押されている間クライアントはHELDの心拍を送り、離すとRELEASEかCANCELを送る。
     * ここに無い押し続け技能はCASTで始まり、待っているキーアップが来ないまま、約0.5秒後に自身の心拍のタイムアウトで止まる。
     * クライアントも同じ集合を読むので、両者が再びずれることはない。
     */
    // 1.4.0のS2から、登録簿の発動の種類（HELD）の技能の集まり（読み取りの窓）。本体の21技能は1.3.0と同じ（CatalogGoldenGameTests）。
    public static final java.util.Set<net.minecraft.resources.ResourceLocation> HELD_SKILLS =
            io.github.pinchan4273.reacademycraft.ability.RegistryViews.skillsOfKind(io.github.pinchan4273.reacademycraft.api.ActivationKind.HELD);
    public static boolean held(@javax.annotation.Nullable net.minecraft.resources.ResourceLocation skill) {
        return skill != null && HELD_SKILLS.contains(skill);
    }
    /**
     * 1回押すとオンになり、次に押すとオフになる技能（Electron Missile、Jet Engine、Body Intensify、Light Shield。原作ではいずれも
     * 押し続け型だが、切り替え型へ変更している。Flashingは元から切り替え型）。クライアントはこれらのCASTを新しい押下でだけ送り、キーリピートでは
     * 送らない。サーバーは、新しい発動が受ける拒否より先に、動作中のものを終える。
     */
    // 1.4.0のS2から、登録簿の発動の種類（TOGGLE）の技能の集まり（読み取りの窓）。本体の5技能は1.3.0と同じ（CatalogGoldenGameTests）。
    public static final java.util.Set<net.minecraft.resources.ResourceLocation> TOGGLED_SKILLS =
            io.github.pinchan4273.reacademycraft.ability.RegistryViews.skillsOfKind(io.github.pinchan4273.reacademycraft.api.ActivationKind.TOGGLE);
    public static boolean toggled(@javax.annotation.Nullable net.minecraft.resources.ResourceLocation skill) {
        return skill != null && TOGGLED_SKILLS.contains(skill);
    }
    /**
     * 動作中の切り替え型技能は、自身のキーで終わる。この押下がそのキーだったらtrue（終えた、または直前にオンにした押下の
     * リピートとして飲み込んだ）。
     */
    private static boolean switchOff(ServerPlayer player, io.github.pinchan4273.reacademycraft.skill.PlayerAbilityData data, int slot) {
        // 1.4.0のS3から、振り分けの表を引く（切り替え型の5技能: Electron Missile、Jet Engine、Flashing、Body Intensify、Light Shield）。
        return io.github.pinchan4273.reacademycraft.ability.SkillDispatch.switchOff(player, data.getSlot(data.getCurrentPreset(), slot), slot, MIN_ON_TICKS);
    }
    /** 切り替え型技能をオンにした押下の直後の2回目の押下は、同じ押下とみなす。 */
    public static final int MIN_ON_TICKS = 4;
    public AbilityAction {
        if (action < 0 || action > 5 || slot < 0 || slot >= 4) throw new IllegalArgumentException("Invalid ability input");
    }
    public void encode(FriendlyByteBuf buffer) { buffer.writeByte(action); buffer.writeByte(slot); }
    public static AbilityAction decode(FriendlyByteBuf buffer) { return new AbilityAction(buffer.readUnsignedByte(), buffer.readUnsignedByte()); }
    public void handle(ServerPlayer player) {
        if (player == null) return;
        if (action == RELEASE || action == CANCEL) {
            // 1.4.0のS3から、振り分けの表（SkillDispatch）を引く。呼ぶもの・順は1.3.0のまま（SkillDispatchGameTestsが1.3.0のソースの表と照らす）。
            // 切り替え型（Body Intensify、Jet Engine、Light Shield、Electron Missile）は、キーアップでは何もせず、CANCEL（クライアントの安全な停止。
            // ウィンドウのフォーカスが外れた。TOGGLE-GUI-001: 画面を開いただけでは送らない）だけが、そのスロットのものを終える。
            // ScatterBomb/MineRayは、離したキーと中断したキーを区別しない。原作はどちらでも同じように発射または終了するので、
            // どちらでも同じ1回の呼び出しで扱う。これが無いと、通常のキーアップがここに届かず、tick()内のサーバー自身の約10tickの
            // 心拍の古さによるタイムアウトでしか止まらない（ScatterBombの一斉射撃は、キーアップと同じtickではなく最大0.5秒遅れて発射される）。
            // 終えるのは、このスロットのキーが始めたセッションだけ。原作はキーのアップや中断を、そのキー自身のコンテキストへ渡すため。
            if (action == RELEASE) io.github.pinchan4273.reacademycraft.ability.SkillDispatch.release(player, slot);
            else io.github.pinchan4273.reacademycraft.ability.SkillDispatch.cancel(player, slot);
            return;
        }
        if (!player.isAlive()) return;
        if (action == HELD) {
            long tick = player.serverLevel().getGameTime();
            long[] held = LAST_HELD.computeIfAbsent(player, ignored -> new long[] {Long.MIN_VALUE, Long.MIN_VALUE, Long.MIN_VALUE, Long.MIN_VALUE});
            if (held[slot] == tick) return;
            held[slot] = tick;
        }
        if (action == HELD) { io.github.pinchan4273.reacademycraft.ability.SkillDispatch.renew(player, slot); return; }
        long now = player.serverLevel().getGameTime();
        long[] previous = LAST_REQUEST.computeIfAbsent(player,
                ignored -> { var ticks = new long[NEXT_PRESET + 1 + 4]; java.util.Arrays.fill(ticks, Long.MIN_VALUE); return ticks; });
        // 1つのサーバーtick内での切り替えと発動の組み合わせは正当。アクションごと、また発動はスロットごとに重複を除く:
        // 1tickに2つの技能キーを押したら、それは2回の発動。
        int key = action == CAST ? NEXT_PRESET + 1 + slot : action;
        if (previous[key] == now) return;
        previous[key] = now;
        player.getCapability(AbilityCapabilities.PLAYER_ABILITY).ifPresent(data -> {
            if (data.isReadOnly() || !data.hasAbility()) return;
            // 動作中の切り替え型技能は、新しい発動が拒否されうるどの理由より先に、自身のキーで終わる。
            if (action == CAST && switchOff(player, data, slot)) return;
            // 原作canUseAbility: 妨害されているプレイヤーは一切発動できない。ただし技能に触れない能力のオフやプリセットの変更はできる。
            // 他の拒否と同じく、原作は何も言わない: キーの案内が灰色になる。
            if (data.isInterfering() && action != TOGGLE && action != NEXT_PRESET) return;
            if (action == TOGGLE) { data.setActive(!data.isActive()); io.github.pinchan4273.reacademycraft.skill.SkillSessions.stopAll(player); }
            // 原作ClientHandler.keySwitchPreset: 能力が有効な間だけ。
            else if (action == NEXT_PRESET) { if (!data.isActive()) return; data.setCurrentPreset((data.getCurrentPreset() + 1) % 4); io.github.pinchan4273.reacademycraft.ability.SkillDispatch.stopAll(player); }
            else {
                String failure = io.github.pinchan4273.reacademycraft.ability.SkillDispatch.start(player, data, data.getSlot(data.getCurrentPreset(), slot), slot);
                // 原作ClientRuntimeは拒否されたキーを実行せず、文字も出さず、キーの案内を灰色にするだけ。そのためここでも拒否は
                // 無言にする。理由はテストのために残す。
            }
            AbilitySyncEvents.sync(player, true);
        });
    }
}
