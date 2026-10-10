package io.github.pinchan4273.reacademycraft.network;

import io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities;
import io.github.pinchan4273.reacademycraft.skill.ArcGen;
import io.github.pinchan4273.reacademycraft.skill.CurrentCharging;
import io.github.pinchan4273.reacademycraft.skill.MagneticMovement;
import io.github.pinchan4273.reacademycraft.skill.MagneticManipulation;
import io.github.pinchan4273.reacademycraft.skill.BodyIntensify;
import io.github.pinchan4273.reacademycraft.skill.Railgun;
import io.github.pinchan4273.reacademycraft.skill.ThunderClap;
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
    public static final java.util.Set<net.minecraft.resources.ResourceLocation> HELD_SKILLS = java.util.Set.of(
            CurrentCharging.ID, MagneticMovement.ID, MagneticManipulation.ID, Railgun.ID, ThunderClap.ID,
            io.github.pinchan4273.reacademycraft.skill.ScatterBomb.ID, io.github.pinchan4273.reacademycraft.skill.LightShield.ID,
            io.github.pinchan4273.reacademycraft.skill.MineRay.BASIC.id(), io.github.pinchan4273.reacademycraft.skill.MineRay.EXPERT.id(),
            io.github.pinchan4273.reacademycraft.skill.MineRay.LUCK.id(), io.github.pinchan4273.reacademycraft.skill.Meltdowner.ID,
            io.github.pinchan4273.reacademycraft.skill.ThreateningTeleport.ID, io.github.pinchan4273.reacademycraft.skill.PenetrateTeleport.ID,
            io.github.pinchan4273.reacademycraft.skill.MarkTeleport.ID, io.github.pinchan4273.reacademycraft.skill.FleshRipping.ID,
            io.github.pinchan4273.reacademycraft.skill.ShiftTeleport.ID, io.github.pinchan4273.reacademycraft.skill.DirectedShock.ID,
            io.github.pinchan4273.reacademycraft.skill.Groundshock.ID, io.github.pinchan4273.reacademycraft.skill.VecAccel.ID,
            io.github.pinchan4273.reacademycraft.skill.DirectedBlastwave.ID, io.github.pinchan4273.reacademycraft.skill.BloodRetrograde.ID,
            io.github.pinchan4273.reacademycraft.skill.PlasmaCannon.ID);
    public static boolean held(@javax.annotation.Nullable net.minecraft.resources.ResourceLocation skill) {
        return skill != null && HELD_SKILLS.contains(skill);
    }
    /**
     * 1回押すとオンになり、次に押すとオフになる技能（Electron Missile、Jet Engine、Body Intensify。原作ではいずれも押し続け型だが、
     * 切り替え型へ変更している。Flashingは元から切り替え型）。クライアントはこれらのCASTを新しい押下でだけ送り、キーリピートでは
     * 送らない。サーバーは、新しい発動が受ける拒否より先に、動作中のものを終える。
     */
    public static final java.util.Set<net.minecraft.resources.ResourceLocation> TOGGLED_SKILLS = java.util.Set.of(
            io.github.pinchan4273.reacademycraft.skill.ElectronMissile.ID, io.github.pinchan4273.reacademycraft.skill.JetEngine.ID, io.github.pinchan4273.reacademycraft.skill.Flashing.ID,
            BodyIntensify.ID);
    public static boolean toggled(@javax.annotation.Nullable net.minecraft.resources.ResourceLocation skill) {
        return skill != null && TOGGLED_SKILLS.contains(skill);
    }
    /**
     * 動作中の切り替え型技能は、自身のキーで終わる。この押下がそのキーだったらtrue（終えた、または直前にオンにした押下の
     * リピートとして飲み込んだ）。
     */
    private static boolean switchOff(ServerPlayer player, io.github.pinchan4273.reacademycraft.skill.PlayerAbilityData data, int slot) {
        var skill = data.getSlot(data.getCurrentPreset(), slot);
        int age = io.github.pinchan4273.reacademycraft.skill.ElectronMissile.ID.equals(skill) ? io.github.pinchan4273.reacademycraft.skill.ElectronMissile.ageOn(player, slot)
                : io.github.pinchan4273.reacademycraft.skill.JetEngine.ID.equals(skill) ? io.github.pinchan4273.reacademycraft.skill.JetEngine.ageOn(player, slot)
                : io.github.pinchan4273.reacademycraft.skill.Flashing.ID.equals(skill) ? io.github.pinchan4273.reacademycraft.skill.Flashing.ageOn(player, slot)
                : BodyIntensify.ID.equals(skill) ? BodyIntensify.ageOn(player, slot) : -1;
        if (age < 0) return false;
        if (age < MIN_ON_TICKS) return true;
        if (io.github.pinchan4273.reacademycraft.skill.ElectronMissile.ID.equals(skill)) io.github.pinchan4273.reacademycraft.skill.ElectronMissile.stop(player);
        else if (io.github.pinchan4273.reacademycraft.skill.JetEngine.ID.equals(skill)) io.github.pinchan4273.reacademycraft.skill.JetEngine.stop(player);
        else if (BodyIntensify.ID.equals(skill)) BodyIntensify.stop(player);
        else io.github.pinchan4273.reacademycraft.skill.Flashing.stop(player);
        return true;
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
            ThunderClap.release(player, slot); Railgun.release(player, slot); CurrentCharging.release(player, slot); MagneticMovement.release(player, slot);
            if (action == RELEASE) MagneticManipulation.release(player, slot); else MagneticManipulation.cancel(player, slot);
            // Body Intensifyは切り替え型: キーアップは意味を持たず、CANCELはクライアントの安全な停止（準備中に画面が開いた、または
            // ウィンドウのフォーカスが外れた）。
            if (action == CANCEL) BodyIntensify.cancel(player, slot);
            // Jet Engineは切り替え型: キーアップは意味を持たず、CANCELはクライアントの安全な停止（噴射中に画面が開いた、または
            // ウィンドウのフォーカスが外れた）。
            if (action == CANCEL) io.github.pinchan4273.reacademycraft.skill.JetEngine.stop(player, slot);
            if (action == RELEASE) io.github.pinchan4273.reacademycraft.skill.Meltdowner.release(player, slot); else io.github.pinchan4273.reacademycraft.skill.Meltdowner.cancel(player, slot);
            if (action == RELEASE) io.github.pinchan4273.reacademycraft.skill.ThreateningTeleport.release(player, slot); else io.github.pinchan4273.reacademycraft.skill.ThreateningTeleport.cancel(player, slot);
            if (action == RELEASE) io.github.pinchan4273.reacademycraft.skill.PenetrateTeleport.release(player, slot); else io.github.pinchan4273.reacademycraft.skill.PenetrateTeleport.cancel(player, slot);
            if (action == RELEASE) io.github.pinchan4273.reacademycraft.skill.MarkTeleport.release(player, slot); else io.github.pinchan4273.reacademycraft.skill.MarkTeleport.cancel(player, slot);
            if (action == RELEASE) io.github.pinchan4273.reacademycraft.skill.FleshRipping.release(player, slot); else io.github.pinchan4273.reacademycraft.skill.FleshRipping.cancel(player, slot);
            if (action == RELEASE) io.github.pinchan4273.reacademycraft.skill.ShiftTeleport.release(player, slot); else io.github.pinchan4273.reacademycraft.skill.ShiftTeleport.cancel(player, slot);
            if (action == RELEASE) io.github.pinchan4273.reacademycraft.skill.DirectedShock.release(player, slot); else io.github.pinchan4273.reacademycraft.skill.DirectedShock.cancel(player, slot);
            if (action == RELEASE) io.github.pinchan4273.reacademycraft.skill.Groundshock.release(player, slot); else io.github.pinchan4273.reacademycraft.skill.Groundshock.cancel(player, slot);
            if (action == RELEASE) io.github.pinchan4273.reacademycraft.skill.VecAccel.release(player, slot); else io.github.pinchan4273.reacademycraft.skill.VecAccel.cancel(player, slot);
            if (action == RELEASE) io.github.pinchan4273.reacademycraft.skill.DirectedBlastwave.release(player, slot); else io.github.pinchan4273.reacademycraft.skill.DirectedBlastwave.cancel(player, slot);
            if (action == RELEASE) io.github.pinchan4273.reacademycraft.skill.BloodRetrograde.release(player, slot); else io.github.pinchan4273.reacademycraft.skill.BloodRetrograde.cancel(player, slot);
            if (action == RELEASE) io.github.pinchan4273.reacademycraft.skill.PlasmaCannon.release(player, slot); else io.github.pinchan4273.reacademycraft.skill.PlasmaCannon.cancel(player, slot);
            // ScatterBomb/LightShield/MineRayは、離したキーと中断したキーを区別しない。原作はどちらでも同じように発射または終了するので、
            // それぞれ1回の呼び出しで両方の動作を扱う。これが無いと、通常のキーアップがここに届かず、tick()内のサーバー自身の約10tickの
            // 心拍の古さによるタイムアウトでしか止まらない（ScatterBombの一斉射撃は、キーアップと同じtickではなく最大0.5秒遅れて発射される）。
            // 終えるのは、このスロットのキーが始めたセッションだけ。原作はキーのアップや中断を、そのキー自身のコンテキストへ渡すため。
            io.github.pinchan4273.reacademycraft.skill.ScatterBomb.stop(player, slot);
            io.github.pinchan4273.reacademycraft.skill.LightShield.stop(player, slot);
            io.github.pinchan4273.reacademycraft.skill.MineRay.stop(player, slot);
            // Electron Missileは切り替え型: ここでは安全な停止だけがこれを終える。
            if (action == CANCEL) io.github.pinchan4273.reacademycraft.skill.ElectronMissile.stop(player, slot);
            return;
        }
        if (!player.isAlive()) return;
        if (action == HELD) {
            long tick = player.serverLevel().getGameTime();
            long[] held = LAST_HELD.computeIfAbsent(player, ignored -> new long[] {Long.MIN_VALUE, Long.MIN_VALUE, Long.MIN_VALUE, Long.MIN_VALUE});
            if (held[slot] == tick) return;
            held[slot] = tick;
        }
        if (action == HELD) { CurrentCharging.renew(player, slot); MagneticMovement.renew(player, slot); MagneticManipulation.renew(player, slot); ThunderClap.renew(player, slot); Railgun.renew(player, slot); io.github.pinchan4273.reacademycraft.skill.ScatterBomb.renew(player, slot); io.github.pinchan4273.reacademycraft.skill.LightShield.renew(player, slot); io.github.pinchan4273.reacademycraft.skill.MineRay.renew(player, slot); io.github.pinchan4273.reacademycraft.skill.Meltdowner.renew(player, slot); io.github.pinchan4273.reacademycraft.skill.ThreateningTeleport.renew(player, slot); io.github.pinchan4273.reacademycraft.skill.PenetrateTeleport.renew(player, slot); io.github.pinchan4273.reacademycraft.skill.MarkTeleport.renew(player, slot); io.github.pinchan4273.reacademycraft.skill.FleshRipping.renew(player, slot); io.github.pinchan4273.reacademycraft.skill.ShiftTeleport.renew(player, slot); io.github.pinchan4273.reacademycraft.skill.DirectedShock.renew(player, slot); io.github.pinchan4273.reacademycraft.skill.Groundshock.renew(player, slot); io.github.pinchan4273.reacademycraft.skill.VecAccel.renew(player, slot); io.github.pinchan4273.reacademycraft.skill.DirectedBlastwave.renew(player, slot); io.github.pinchan4273.reacademycraft.skill.BloodRetrograde.renew(player, slot); io.github.pinchan4273.reacademycraft.skill.PlasmaCannon.renew(player, slot); return; }
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
            else if (action == NEXT_PRESET) { if (!data.isActive()) return; data.setCurrentPreset((data.getCurrentPreset() + 1) % 4); CurrentCharging.stop(player); MagneticMovement.stop(player); MagneticManipulation.stop(player); BodyIntensify.stop(player); ThunderClap.stop(player); Railgun.stop(player); io.github.pinchan4273.reacademycraft.skill.ScatterBomb.stop(player); io.github.pinchan4273.reacademycraft.skill.LightShield.stop(player); io.github.pinchan4273.reacademycraft.skill.MineRay.stop(player); io.github.pinchan4273.reacademycraft.skill.JetEngine.stop(player); io.github.pinchan4273.reacademycraft.skill.ElectronMissile.stop(player); io.github.pinchan4273.reacademycraft.skill.Meltdowner.stop(player); io.github.pinchan4273.reacademycraft.skill.ThreateningTeleport.stop(player); io.github.pinchan4273.reacademycraft.skill.PenetrateTeleport.stop(player); io.github.pinchan4273.reacademycraft.skill.MarkTeleport.stop(player); io.github.pinchan4273.reacademycraft.skill.FleshRipping.stop(player); io.github.pinchan4273.reacademycraft.skill.ShiftTeleport.stop(player); io.github.pinchan4273.reacademycraft.skill.Flashing.stop(player); io.github.pinchan4273.reacademycraft.skill.DirectedShock.stop(player); io.github.pinchan4273.reacademycraft.skill.Groundshock.stop(player); io.github.pinchan4273.reacademycraft.skill.VecAccel.stop(player); io.github.pinchan4273.reacademycraft.skill.VecDeviation.stop(player); io.github.pinchan4273.reacademycraft.skill.DirectedBlastwave.stop(player); io.github.pinchan4273.reacademycraft.skill.StormWing.stop(player); io.github.pinchan4273.reacademycraft.skill.BloodRetrograde.stop(player); io.github.pinchan4273.reacademycraft.skill.VecReflection.stop(player); io.github.pinchan4273.reacademycraft.skill.PlasmaCannon.stop(player); }
            else {
                String failure = ArcGen.ID.equals(data.getSlot(data.getCurrentPreset(), slot))
                        ? ArcGen.cast(player, data) : CurrentCharging.ID.equals(data.getSlot(data.getCurrentPreset(), slot))
                        ? CurrentCharging.start(player, data, slot) : MagneticMovement.ID.equals(data.getSlot(data.getCurrentPreset(), slot))
                        ? MagneticMovement.start(player, data, slot) : MagneticManipulation.ID.equals(data.getSlot(data.getCurrentPreset(), slot))
                        ? MagneticManipulation.start(player, data, slot) : BodyIntensify.ID.equals(data.getSlot(data.getCurrentPreset(), slot))
                        ? BodyIntensify.start(player, data, slot) : io.github.pinchan4273.reacademycraft.skill.MineDetect.ID.equals(data.getSlot(data.getCurrentPreset(), slot))
                        ? io.github.pinchan4273.reacademycraft.skill.MineDetect.cast(player, data) : io.github.pinchan4273.reacademycraft.skill.ThunderBolt.ID.equals(data.getSlot(data.getCurrentPreset(), slot))
                        ? io.github.pinchan4273.reacademycraft.skill.ThunderBolt.cast(player, data) : Railgun.ID.equals(data.getSlot(data.getCurrentPreset(), slot))
                        ? Railgun.start(player, data, slot) : ThunderClap.ID.equals(data.getSlot(data.getCurrentPreset(), slot))
                        ? ThunderClap.start(player, data, slot) : io.github.pinchan4273.reacademycraft.skill.ElectronBomb.ID.equals(data.getSlot(data.getCurrentPreset(), slot))
                        ? io.github.pinchan4273.reacademycraft.skill.ElectronBomb.cast(player, data) : io.github.pinchan4273.reacademycraft.skill.ScatterBomb.ID.equals(data.getSlot(data.getCurrentPreset(), slot))
                        ? io.github.pinchan4273.reacademycraft.skill.ScatterBomb.start(player, data, slot) : io.github.pinchan4273.reacademycraft.skill.LightShield.ID.equals(data.getSlot(data.getCurrentPreset(), slot))
                        ? io.github.pinchan4273.reacademycraft.skill.LightShield.start(player, data, slot) : io.github.pinchan4273.reacademycraft.skill.MineRay.of(data.getSlot(data.getCurrentPreset(), slot)) != null
                        ? io.github.pinchan4273.reacademycraft.skill.MineRay.start(io.github.pinchan4273.reacademycraft.skill.MineRay.of(data.getSlot(data.getCurrentPreset(), slot)), player, data, slot)
                        : io.github.pinchan4273.reacademycraft.skill.JetEngine.ID.equals(data.getSlot(data.getCurrentPreset(), slot))
                        ? io.github.pinchan4273.reacademycraft.skill.JetEngine.start(player, data, slot)
                        : io.github.pinchan4273.reacademycraft.skill.RayBarrage.ID.equals(data.getSlot(data.getCurrentPreset(), slot))
                        ? io.github.pinchan4273.reacademycraft.skill.RayBarrage.cast(player, data)
                        : io.github.pinchan4273.reacademycraft.skill.ElectronMissile.ID.equals(data.getSlot(data.getCurrentPreset(), slot))
                        ? io.github.pinchan4273.reacademycraft.skill.ElectronMissile.start(player, data, slot)
                        : io.github.pinchan4273.reacademycraft.skill.Meltdowner.ID.equals(data.getSlot(data.getCurrentPreset(), slot))
                        ? io.github.pinchan4273.reacademycraft.skill.Meltdowner.start(player, data, slot)
                        : io.github.pinchan4273.reacademycraft.skill.ThreateningTeleport.ID.equals(data.getSlot(data.getCurrentPreset(), slot))
                        ? io.github.pinchan4273.reacademycraft.skill.ThreateningTeleport.start(player, data, slot)
                        : io.github.pinchan4273.reacademycraft.skill.PenetrateTeleport.ID.equals(data.getSlot(data.getCurrentPreset(), slot))
                        ? io.github.pinchan4273.reacademycraft.skill.PenetrateTeleport.start(player, data, slot)
                        : io.github.pinchan4273.reacademycraft.skill.MarkTeleport.ID.equals(data.getSlot(data.getCurrentPreset(), slot))
                        ? io.github.pinchan4273.reacademycraft.skill.MarkTeleport.start(player, data, slot)
                        : io.github.pinchan4273.reacademycraft.skill.FleshRipping.ID.equals(data.getSlot(data.getCurrentPreset(), slot))
                        ? io.github.pinchan4273.reacademycraft.skill.FleshRipping.start(player, data, slot)
                        : io.github.pinchan4273.reacademycraft.skill.ShiftTeleport.ID.equals(data.getSlot(data.getCurrentPreset(), slot))
                        ? io.github.pinchan4273.reacademycraft.skill.ShiftTeleport.start(player, data, slot)
                        : io.github.pinchan4273.reacademycraft.skill.Flashing.ID.equals(data.getSlot(data.getCurrentPreset(), slot))
                        ? io.github.pinchan4273.reacademycraft.skill.Flashing.toggle(player, data, slot)
                        : io.github.pinchan4273.reacademycraft.skill.DirectedShock.ID.equals(data.getSlot(data.getCurrentPreset(), slot))
                        ? io.github.pinchan4273.reacademycraft.skill.DirectedShock.start(player, data, slot)
                        : io.github.pinchan4273.reacademycraft.skill.Groundshock.ID.equals(data.getSlot(data.getCurrentPreset(), slot))
                        ? io.github.pinchan4273.reacademycraft.skill.Groundshock.start(player, data, slot)
                        : io.github.pinchan4273.reacademycraft.skill.VecAccel.ID.equals(data.getSlot(data.getCurrentPreset(), slot))
                        ? io.github.pinchan4273.reacademycraft.skill.VecAccel.start(player, data, slot)
                        : io.github.pinchan4273.reacademycraft.skill.VecDeviation.ID.equals(data.getSlot(data.getCurrentPreset(), slot))
                        ? io.github.pinchan4273.reacademycraft.skill.VecDeviation.toggle(player, data, slot)
                        : io.github.pinchan4273.reacademycraft.skill.DirectedBlastwave.ID.equals(data.getSlot(data.getCurrentPreset(), slot))
                        ? io.github.pinchan4273.reacademycraft.skill.DirectedBlastwave.start(player, data, slot)
                        : io.github.pinchan4273.reacademycraft.skill.StormWing.ID.equals(data.getSlot(data.getCurrentPreset(), slot))
                        ? io.github.pinchan4273.reacademycraft.skill.StormWing.toggle(player, data, slot)
                        : io.github.pinchan4273.reacademycraft.skill.BloodRetrograde.ID.equals(data.getSlot(data.getCurrentPreset(), slot))
                        ? io.github.pinchan4273.reacademycraft.skill.BloodRetrograde.start(player, data, slot)
                        : io.github.pinchan4273.reacademycraft.skill.VecReflection.ID.equals(data.getSlot(data.getCurrentPreset(), slot))
                        ? io.github.pinchan4273.reacademycraft.skill.VecReflection.toggle(player, data, slot)
                        : io.github.pinchan4273.reacademycraft.skill.PlasmaCannon.ID.equals(data.getSlot(data.getCurrentPreset(), slot))
                        ? io.github.pinchan4273.reacademycraft.skill.PlasmaCannon.start(player, data, slot)
                        : "academy.cast.empty";
                // 原作ClientRuntimeは拒否されたキーを実行せず、文字も出さず、キーの案内を灰色にするだけ。そのためここでも拒否は
                // 無言にする。理由はテストのために残す。
            }
            AbilitySyncEvents.sync(player, true);
        });
    }
}
