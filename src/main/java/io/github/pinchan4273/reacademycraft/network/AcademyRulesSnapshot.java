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
public record AcademyRulesSnapshot(List<String> disabledSkills, List<Float> initCp, List<Float> initOverload, List<ResourceLocation> disabledAddonSkills) {
    public static final int LEVELS = 6, MAX_NAME_LENGTH = 64;
    /** 外部の技能の名前の長さと、無効にした外部の技能の数の上限（1.4.0のS4）。 */
    public static final int MAX_ADDON_NAME_LENGTH = 256, MAX_ADDON_SKILLS = 1024;

    /**
     * 1.4.0のS4: 無効にした外部の技能（名前空間付きの名前）を足した。本体の技能は今までどおり名前（パス）で送る。
     * クライアントは、自分の登録簿に無い名前を無視する（登録簿の照合は別の通知が行う）。
     */
    public AcademyRulesSnapshot {
        disabledSkills = List.copyOf(disabledSkills); initCp = List.copyOf(initCp); initOverload = List.copyOf(initOverload);
        disabledAddonSkills = List.copyOf(disabledAddonSkills);
        if (disabledSkills.size() > AcademyConfig.SKILL_NAMES.size() || !AcademyConfig.SKILL_NAMES.containsAll(disabledSkills)
                || new HashSet<>(disabledSkills).size() != disabledSkills.size() || !levels(initCp) || !levels(initOverload)
                || disabledAddonSkills.size() > MAX_ADDON_SKILLS || new HashSet<>(disabledAddonSkills).size() != disabledAddonSkills.size()
                || disabledAddonSkills.stream().anyMatch(id -> "academy".equals(id.getNamespace()) || id.toString().length() > MAX_ADDON_NAME_LENGTH))
            throw new IllegalArgumentException("Invalid AcademyCraft rules");
    }
    /** 外部の技能の無い規則（1.4.0のS4の前の形）。 */
    public AcademyRulesSnapshot(List<String> disabledSkills, List<Float> initCp, List<Float> initOverload) {
        this(disabledSkills, initCp, initOverload, List.of());
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
        var addons = new ArrayList<ResourceLocation>();
        for (var skill : io.github.pinchan4273.reacademycraft.ability.AbilityRegistry.INSTANCE.skills()) {
            if (skill.id().isAcademy() || addons.size() >= MAX_ADDON_SKILLS) continue;
            var id = io.github.pinchan4273.reacademycraft.ability.RegistryViews.location(skill.id());
            if (!AcademyConfig.skillEnabled(id)) addons.add(id);
        }
        return new AcademyRulesSnapshot(disabled, cp, overload, addons);
    }
    /** floatに収まらない大きな設定値は、floatの最大値とする（サーバー自身の計算でもほぼそうなる）。 */
    private static float sendable(float value) { return Float.isFinite(value) && value >= 0 ? value : Float.MAX_VALUE; }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(disabledSkills.size());
        for (var name : disabledSkills) buffer.writeUtf(name, MAX_NAME_LENGTH);
        for (var value : initCp) buffer.writeFloat(value);
        for (var value : initOverload) buffer.writeFloat(value);
        buffer.writeVarInt(disabledAddonSkills.size());
        for (var id : disabledAddonSkills) buffer.writeUtf(id.toString(), MAX_ADDON_NAME_LENGTH);
    }

    public static AcademyRulesSnapshot decode(FriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        if (count < 0 || count > AcademyConfig.SKILL_NAMES.size()) throw new IllegalArgumentException("Invalid AcademyCraft rules");
        var disabled = new ArrayList<String>();
        for (int i = 0; i < count; i++) disabled.add(buffer.readUtf(MAX_NAME_LENGTH));
        var cp = new ArrayList<Float>(); var overload = new ArrayList<Float>();
        for (int i = 0; i < LEVELS; i++) cp.add(buffer.readFloat());
        for (int i = 0; i < LEVELS; i++) overload.add(buffer.readFloat());
        int addonCount = buffer.readVarInt();
        if (addonCount < 0 || addonCount > MAX_ADDON_SKILLS) throw new IllegalArgumentException("Invalid AcademyCraft rules");
        var addons = new ArrayList<ResourceLocation>();
        for (int i = 0; i < addonCount; i++) {
            var id = ResourceLocation.tryParse(buffer.readUtf(MAX_ADDON_NAME_LENGTH));
            if (id == null) throw new IllegalArgumentException("Invalid AcademyCraft rules");
            addons.add(id);
        }
        return new AcademyRulesSnapshot(disabled, cp, overload, addons);
    }
}
