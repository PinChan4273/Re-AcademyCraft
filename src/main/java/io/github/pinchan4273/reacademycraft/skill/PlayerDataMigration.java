package io.github.pinchan4273.reacademycraft.skill;

import static io.github.pinchan4273.reacademycraft.skill.AbilityDataSchema.*;

import io.github.pinchan4273.reacademycraft.AcademyCraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

/**
 * 以前の版の保存形式（schema 1: 根に平らなキーを並べた形）を、schema 2（{@link AbilityDataSchema}）へ移す。
 * 旧形式のキーの名前を知っているのはこのクラスだけで、PlayerAbilityDataは新形式しか読まない。
 * 値は型ごとそのまま移し、範囲の検証は新形式を読むPlayerAbilityData.loadに任せる（移す前と同じ結果になる）。
 * 旧形式のキーは取り除き、知らないキーは根に残す。
 */
final class PlayerDataMigration {
    private static final String[] OLD_KEYS = {"ability", "level", "level_experience", "max_cp", "cp", "max_overload", "overload",
            "ability_active", "overload_locked", "cp_recovery_delay", "overload_recovery_delay", "bonus_cp", "bonus_overload",
            "current_preset", "skills", "presets"};

    private PlayerDataMigration() { }

    /** schema 1か、版の無い（さらに古い、または空の）データ。版の値が数でないものや、新しい版は移さない。 */
    static boolean needed(CompoundTag tag) {
        if (!tag.contains(VERSION_KEY)) return true;
        if (!tag.contains(VERSION_KEY, Tag.TAG_INT)) return false;
        int version = tag.getInt(VERSION_KEY);
        return version == 0 || version == 1;
    }

    static CompoundTag fromSchemaOne(CompoundTag old) {
        CompoundTag root = old.copy();
        CompoundTag ability = new CompoundTag(), cp = new CompoundTag(), preset = new CompoundTag();
        // 根に既にCooldownDataという区分があれば（以前の版にとっては知らないキー）、その中身を引き継ぐ。
        CompoundTag cooldown = old.contains(COOLDOWN, Tag.TAG_COMPOUND) ? old.getCompound(COOLDOWN).copy() : new CompoundTag();

        String category = old.contains("ability", Tag.TAG_STRING) ? old.getString("ability") : "";
        // 名前空間の無いIDは、このmodの名前空間のものとして読む。
        if (!category.isEmpty()) ability.putString(CATEGORY, category.contains(":") ? category : AcademyCraft.MODID + ":" + category);
        move(old, "level", ability, LEVEL);
        move(old, "level_experience", ability, LEVEL_EXP);
        ListTag skills = new ListTag();
        for (Tag item : old.getList("skills", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) item, moved = new CompoundTag();
            move(entry, "id", moved, SKILL);
            move(entry, "proficiency", moved, EXP);
            skills.add(moved);
            String id = entry.getString("id");
            if (!id.isEmpty() && entry.contains("cooldown", Tag.TAG_ANY_NUMERIC)) cooldown.put(id, entry.get("cooldown").copy());
        }
        ability.put(SKILLS, skills);

        move(old, "ability_active", cp, ACTIVATED);
        move(old, "cp", cp, CUR_CP);
        move(old, "max_cp", cp, MAX_CP);
        move(old, "bonus_cp", cp, ADD_MAX_CP);
        move(old, "overload", cp, CUR_OVERLOAD);
        move(old, "max_overload", cp, MAX_OVERLOAD);
        move(old, "bonus_overload", cp, ADD_MAX_OVERLOAD);
        if (old.contains("overload_locked", Tag.TAG_ANY_NUMERIC)) cp.putBoolean(OVERLOAD_FINE, !old.getBoolean("overload_locked"));
        move(old, "cp_recovery_delay", cp, UNTIL_RECOVER);
        move(old, "overload_recovery_delay", cp, UNTIL_OVERLOAD_RECOVER);

        move(old, "current_preset", preset, PRESET_ID);
        String[][] grid = new String[PlayerAbilityData.PRESET_COUNT][PlayerAbilityData.SLOT_COUNT];
        for (String[] row : grid) java.util.Arrays.fill(row, "");
        ListTag slots = old.getList("presets", Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(16, slots.size()); i++) {
            CompoundTag slot = slots.getCompound(i);
            int p = slot.getInt("preset"), s = slot.getInt("slot");
            if (p >= 0 && p < grid.length && s >= 0 && s < grid[p].length && slot.contains("skill", Tag.TAG_STRING)) grid[p][s] = slot.getString("skill");
        }
        ListTag presets = new ListTag();
        for (String[] row : grid) {
            ListTag line = new ListTag();
            for (String id : row) line.add(StringTag.valueOf(id));
            presets.add(line);
        }
        preset.put(PRESETS, presets);

        for (String key : OLD_KEYS) root.remove(key);
        root.putInt(VERSION_KEY, VERSION);
        root.put(ABILITY, ability);
        root.put(CP, cp);
        root.put(PRESET, preset);
        root.put(COOLDOWN, cooldown);
        return root;
    }

    private static void move(CompoundTag from, String oldKey, CompoundTag to, String newKey) {
        Tag value = from.get(oldKey);
        if (value != null) to.put(newKey, value.copy());
    }
}
