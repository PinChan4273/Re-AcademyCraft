package io.github.pinchan4273.reacademycraft.ability;

import io.github.pinchan4273.reacademycraft.skill.PlayerAbilityData;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * 技能の振り分けの表（1.4.0のS3、内部の実装）。プレイヤーのキーの入力（{@code AbilityAction.handle}）と、すべてのセッションの停止
 * （{@code SkillSessions.stopAll}）は、技能ごとに手で並べた呼び出しではなく、この表を引く。
 * <p>
 * 表は2種類: 技能のIDから発動（CAST）を引く表と、セッションを持つもの（{@link Owner}）の一覧。キーアップ・心拍・停止は、
 * スロットの技能に関わらず、すべてのセッションの持ち主へ順に配る（1.3.0と同じ。各技能は自分のセッションのスロットだけを見る）。
 * 本体の持ち主は、1.3.0の呼び出しと同じ順に3つの並び（キーアップ・心拍・停止）へ置く（{@link BuiltinSkillDispatch}。
 * {@code SkillDispatchGameTests}が1.3.0のソースから取った表と照らす）。外部の技能（1.4.0のS4、{@link AddonSessions}）は、本体の後に
 * 登録の順で続く。
 */
public final class SkillDispatch {
    private SkillDispatch() { }

    /** スロットを取る呼び出し（離し・中断・心拍）。 */
    @FunctionalInterface public interface SlotAction { void run(ServerPlayer player, int slot); }
    /** プレイヤーのセッションを終える呼び出し。 */
    @FunctionalInterface public interface PlayerAction { void run(ServerPlayer player); }
    /** 発動。戻り値は拒否の理由（{@code academy.cast.*}）か、受け付けたらnull。振り分けは理由を使わない（試験のために返す）。 */
    @FunctionalInterface public interface Start { @Nullable String run(ServerPlayer player, PlayerAbilityData data, int slot); }
    /** 切り替え型の、そのスロットで動作中の時間（tick）。動作していなければ負。 */
    @FunctionalInterface public interface Age { int run(ServerPlayer player, int slot); }

    /** 名前の付いた呼び出し。名前は試験の記録（{@link #traceForTest}）に使う（例: {@code "ThunderClap.release"}）。 */
    public record Named<T>(String name, T action) { }

    /**
     * セッションを持つもの（1つのクラスが複数の技能の持ち主になりうる。MineRayは3つの変種）。各呼び出しは、無ければnull。
     * ageOnがあるものは切り替え型で、自分のキーの次の押下でstopが終える。
     */
    public record Owner(String name, @Nullable Named<SlotAction> release, @Nullable Named<SlotAction> cancel,
                        @Nullable Named<SlotAction> renew, @Nullable Named<PlayerAction> stop, @Nullable Named<Age> ageOn) { }

    /** 技能の発動。ownerは、その技能のセッションの持ち主（切り替え型の判定に使う。無ければnull）。 */
    public record Cast(Named<Start> start, @Nullable Owner owner) { }

    private static final Map<ResourceLocation, Cast> CASTS = new HashMap<>();
    private static final List<Owner> KEY_UP = new ArrayList<>(), HELD = new ArrayList<>(), STOP = new ArrayList<>();
    private static boolean builtIns;
    /** 試験だけが置く記録の受け手。ゲームではnull。 */
    private static volatile @Nullable Consumer<String> trace;

    static {
        BuiltinSkillDispatch.register();
        builtIns = true;
    }

    /** 本体の表を作る間だけ使う（{@link BuiltinSkillDispatch}）。並びは呼んだ順。 */
    static void builtInOrder(List<Owner> keyUp, List<Owner> held, List<Owner> stop) {
        if (builtIns) throw new IllegalStateException("The built-in dispatch order is already set");
        KEY_UP.addAll(keyUp); HELD.addAll(held); STOP.addAll(stop);
    }
    static void builtInCast(ResourceLocation skill, Cast cast) {
        if (builtIns) throw new IllegalStateException("The built-in casts are already set");
        CASTS.put(skill, cast);
    }

    /**
     * 外部の技能を足す（1.4.0のS4。振る舞いの登録簿の凍結の時に、登録の順で呼ぶ）。持ち主は、押し続け型・切り替え型なら、キーアップ・心拍・
     * 停止のすべての並びで本体の後ろに続く（一回きりの技能はnull）。本体の技能の名前は使えない。
     */
    static void addAddon(ResourceLocation skill, @Nullable Owner owner, Cast cast) {
        if (skill.getNamespace().equals(AbilityRegistry.ACADEMY) || CASTS.containsKey(skill))
            throw new IllegalStateException("The dispatch already has " + skill);
        CASTS.put(skill, cast);
        if (owner != null) { KEY_UP.add(owner); HELD.add(owner); STOP.add(owner); }
    }

    /** 試験のための記録。nullで外す。同じスレッドで、記録したい呼び出しの直前に置き、直後に外す。 */
    public static void traceForTest(@Nullable Consumer<String> recorder) { trace = recorder; }
    private static void record(String name) {
        var recorder = trace;
        if (recorder != null) recorder.accept(name);
    }

    /** その技能の発動の表（無ければnull。受動技能と、クライアントの画面から始まる技能（Location Teleport）は無い）。 */
    public static @Nullable Cast cast(@Nullable ResourceLocation skill) { return skill == null ? null : CASTS.get(skill); }
    /** 停止の順のセッションの持ち主（変えられない写し）。 */
    public static List<Owner> owners() { return Collections.unmodifiableList(new ArrayList<>(STOP)); }

    /** キーを離した: すべての持ち主の離しを、そのスロットで呼ぶ。 */
    public static void release(ServerPlayer player, int slot) {
        for (Owner owner : KEY_UP) if (owner.release() != null) { record(owner.release().name()); owner.release().action().run(player, slot); }
    }
    /** キーの中断（クライアントの安全な停止）: すべての持ち主の中断を、そのスロットで呼ぶ。 */
    public static void cancel(ServerPlayer player, int slot) {
        for (Owner owner : KEY_UP) if (owner.cancel() != null) { record(owner.cancel().name()); owner.cancel().action().run(player, slot); }
    }
    /** 押し続けの心拍: すべての持ち主の心拍を、そのスロットで呼ぶ。 */
    public static void renew(ServerPlayer player, int slot) {
        for (Owner owner : HELD) if (owner.renew() != null) { record(owner.renew().name()); owner.renew().action().run(player, slot); }
    }
    /** プレイヤーのすべてのセッションを終える（原作ContextManager.disposePlayer）。 */
    public static void stopAll(ServerPlayer player) {
        for (Owner owner : STOP) if (owner.stop() != null) { record(owner.stop().name()); owner.stop().action().run(player); }
    }
    /**
     * 動作中の切り替え型技能は、自身のキーで終わる。この押下がそのキーだったらtrue（終えた、または直前にオンにした押下の
     * リピートとして飲み込んだ）。
     */
    public static boolean switchOff(ServerPlayer player, @Nullable ResourceLocation skill, int slot, int minOnTicks) {
        var cast = cast(skill);
        if (cast == null || cast.owner() == null || cast.owner().ageOn() == null) return false;
        Owner owner = cast.owner();
        record(owner.ageOn().name());
        int age = owner.ageOn().action().run(player, slot);
        if (age < 0) return false;
        if (age < minOnTicks) return true;
        record(owner.stop().name());
        owner.stop().action().run(player);
        return true;
    }
    /** スロットの技能を発動する。表に無ければ{@code academy.cast.empty}。 */
    public static @Nullable String start(ServerPlayer player, PlayerAbilityData data, @Nullable ResourceLocation skill, int slot) {
        var cast = cast(skill);
        if (cast == null) return "academy.cast.empty";
        record(cast.start().name());
        return cast.start().action().run(player, data, slot);
    }
}
