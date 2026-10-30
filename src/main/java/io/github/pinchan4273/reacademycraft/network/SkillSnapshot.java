package io.github.pinchan4273.reacademycraft.network;

import io.github.pinchan4273.reacademycraft.skill.AbilityDataSchema;
import io.github.pinchan4273.reacademycraft.skill.PlayerAbilityData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import java.util.ArrayList;
import java.util.List;

/** 拡張可能なディスクのNBTから独立した、明示的な通信の形式。 */
/**
 * overloadedは原作CPData.isOverloaded（ロック中で回復のクールダウン内）で、CPバーと画面マスクはこれで描く。
 * クールダウン自体は送らず、動いているかだけを送る。
 */
public record SkillSnapshot(List<Entry> skills, List<String> slots, int preset, boolean overloadLocked, boolean overloaded) {
    public record Entry(ResourceLocation id, float proficiency, int cooldown) {
        public Entry {
            if (id == null || id.toString().length() > 256 || !Float.isFinite(proficiency)
                    || proficiency < 0 || proficiency > 1 || cooldown < 0 || cooldown > 72000)
                throw new IllegalArgumentException("Invalid skill snapshot entry");
        }
    }
    public SkillSnapshot {
        skills = List.copyOf(skills); slots = List.copyOf(slots);
        if (skills.size() > PlayerAbilityData.MAX_SKILLS || slots.size() != 16 || preset < 0 || preset >= 4)
            throw new IllegalArgumentException("Invalid skill snapshot size");
        var ids = new java.util.HashSet<ResourceLocation>();
        for (var entry : skills) if (!ids.add(entry.id())) throw new IllegalArgumentException("Duplicate skill");
        for (String slot : slots) if (slot.length() > 256 || (!slot.isEmpty() && !ids.contains(ResourceLocation.tryParse(slot))))
            throw new IllegalArgumentException("Invalid equipped skill");
    }
    public static SkillSnapshot capture(PlayerAbilityData data) {
        List<Entry> skills = data.learnedSkills().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey())
                .map(e -> new Entry(e.getKey(), e.getValue(), data.getCooldown(e.getKey()))).toList();
        List<String> slots = new ArrayList<>();
        for (int p = 0; p < 4; p++) for (int s = 0; s < 4; s++) {
            ResourceLocation id = data.getSlot(p, s); slots.add(id == null ? "" : id.toString());
        }
        return new SkillSnapshot(skills, slots, data.getCurrentPreset(), data.isOverloadLocked(), data.isOverloaded());
    }
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(skills.size());
        for (var entry : skills) {
            buffer.writeUtf(entry.id().toString(), 256); buffer.writeFloat(entry.proficiency()); buffer.writeVarInt(entry.cooldown());
        }
        for (String slot : slots) buffer.writeUtf(slot, 256);
        buffer.writeByte(preset); buffer.writeBoolean(overloadLocked); buffer.writeBoolean(overloaded);
    }
    public static SkillSnapshot decode(FriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        if (count < 0 || count > PlayerAbilityData.MAX_SKILLS) throw new IllegalArgumentException("Excessive skill count");
        List<Entry> skills = new ArrayList<>();
        for (int i = 0; i < count; i++) skills.add(new Entry(ResourceLocation.parse(buffer.readUtf(256)), buffer.readFloat(), buffer.readVarInt()));
        List<String> slots = new ArrayList<>();
        for (int i = 0; i < 16; i++) slots.add(buffer.readUtf(256));
        return new SkillSnapshot(skills, slots, buffer.readUnsignedByte(), buffer.readBoolean(), buffer.readBoolean());
    }
    /** 能力データの保存の形（{@link AbilityDataSchema}）の該当部分へ書く。 */
    public void writeToTag(CompoundTag tag) {
        ListTag learned = new ListTag();
        CompoundTag cooling = new CompoundTag();
        for (var entry : skills) {
            CompoundTag item = new CompoundTag(); item.putString(AbilityDataSchema.SKILL, entry.id().toString());
            item.putFloat(AbilityDataSchema.EXP, entry.proficiency()); learned.add(item);
            if (entry.cooldown() > 0) cooling.putInt(entry.id().toString(), entry.cooldown());
        }
        ListTag table = new ListTag();
        for (int p = 0; p < 4; p++) {
            ListTag row = new ListTag();
            for (int s = 0; s < 4; s++) row.add(StringTag.valueOf(slots.get(p * 4 + s)));
            table.add(row);
        }
        AbilityDataSchema.section(tag, AbilityDataSchema.ABILITY).put(AbilityDataSchema.SKILLS, learned);
        CompoundTag presetPart = AbilityDataSchema.section(tag, AbilityDataSchema.PRESET);
        presetPart.put(AbilityDataSchema.PRESETS, table); presetPart.putInt(AbilityDataSchema.PRESET_ID, preset);
        tag.put(AbilityDataSchema.COOLDOWN, cooling);
        CompoundTag cpPart = AbilityDataSchema.section(tag, AbilityDataSchema.CP);
        cpPart.putBoolean(AbilityDataSchema.OVERLOAD_FINE, !overloadLocked);
        // クライアントはデータをtickしないので、動いているクールダウンは、別の通知が来るまで1tickとして保つ。
        cpPart.putInt(AbilityDataSchema.UNTIL_OVERLOAD_RECOVER, overloaded ? 1 : 0);
    }
}
