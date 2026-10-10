package io.github.pinchan4273.reacademycraft.network;

import io.github.pinchan4273.reacademycraft.config.AcademyConfig;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * 参加先のサーバーと同じに表示する必要がある少数の規則: そのサーバーが無効にしている技能（開発機は技能の木から外す）と、
 * 各レベルの初期CPとオーバーロード（デバッグコンソールが最大値を分解する）。クライアントが送るものはこれらに依存せず、
 * すべての結果はサーバー自身が決める。ログイン時と、サーバーの設定が再読込されるたびに送る。
 */
public record AcademyRulesSnapshot(List<String> disabledSkills, List<Float> initCp, List<Float> initOverload) {
    public static final int LEVELS = 6, MAX_NAME_LENGTH = 64;

    public AcademyRulesSnapshot {
        disabledSkills = List.copyOf(disabledSkills); initCp = List.copyOf(initCp); initOverload = List.copyOf(initOverload);
        if (disabledSkills.size() > AcademyConfig.SKILL_NAMES.size() || !AcademyConfig.SKILL_NAMES.containsAll(disabledSkills)
                || new HashSet<>(disabledSkills).size() != disabledSkills.size() || !levels(initCp) || !levels(initOverload))
            throw new IllegalArgumentException("Invalid AcademyCraft rules");
    }
    private static boolean levels(List<Float> values) {
        return values.size() == LEVELS && values.stream().allMatch(v -> Float.isFinite(v) && v >= 0);
    }

    /** 論理サーバー自身の規則（その設定から）。 */
    public static AcademyRulesSnapshot capture() {
        var disabled = new ArrayList<String>();
        for (var name : AcademyConfig.SKILL_NAMES)
            if (!AcademyConfig.skillEnabled(ResourceLocation.fromNamespaceAndPath("academy", name))) disabled.add(name);
        var cp = new ArrayList<Float>(); var overload = new ArrayList<Float>();
        for (int level = 0; level < LEVELS; level++) { cp.add(sendable(AcademyConfig.initCp(level))); overload.add(sendable(AcademyConfig.initOverload(level))); }
        return new AcademyRulesSnapshot(disabled, cp, overload);
    }
    /** floatに収まらない大きな設定値は、floatの最大値とする（サーバー自身の計算でもほぼそうなる）。 */
    private static float sendable(float value) { return Float.isFinite(value) && value >= 0 ? value : Float.MAX_VALUE; }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(disabledSkills.size());
        for (var name : disabledSkills) buffer.writeUtf(name, MAX_NAME_LENGTH);
        for (var value : initCp) buffer.writeFloat(value);
        for (var value : initOverload) buffer.writeFloat(value);
    }

    public static AcademyRulesSnapshot decode(FriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        if (count < 0 || count > AcademyConfig.SKILL_NAMES.size()) throw new IllegalArgumentException("Invalid AcademyCraft rules");
        var disabled = new ArrayList<String>();
        for (int i = 0; i < count; i++) disabled.add(buffer.readUtf(MAX_NAME_LENGTH));
        var cp = new ArrayList<Float>(); var overload = new ArrayList<Float>();
        for (int i = 0; i < LEVELS; i++) cp.add(buffer.readFloat());
        for (int i = 0; i < LEVELS; i++) overload.add(buffer.readFloat());
        return new AcademyRulesSnapshot(disabled, cp, overload);
    }
}
