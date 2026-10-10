package io.github.pinchan4273.reacademycraft.network;

import io.github.pinchan4273.reacademycraft.skill.AbilityDataSchema;
import io.github.pinchan4273.reacademycraft.skill.PlayerAbilityData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** 所有者だけに見せる、範囲を限った表示。任意に保存された拡張NBTは決して送らない。 */
public record AbilitySnapshot(UUID player, ResourceLocation dimension,
        @Nullable ResourceLocation ability, int level, float levelExperience, float cp, float maxCp,
        float overload, float maxOverload, boolean active, boolean readOnly, boolean interfering, SkillSnapshot skills) {
    public static final int MAX_ID_LENGTH = 256;

    public AbilitySnapshot {
        if (skills == null || player == null || dimension == null || dimension.toString().length() > MAX_ID_LENGTH
                || (ability != null && ability.toString().length() > MAX_ID_LENGTH)
                || level < 0 || level > 5 || (ability == null ? level != 0 || active : level == 0)
                || !finite(levelExperience) || (ability == null && levelExperience != 0)
                || !finite(cp) || !finite(maxCp) || cp > maxCp
                || !finite(overload) || !finite(maxOverload) || overload > maxOverload) {
            throw new IllegalArgumentException("Invalid ability snapshot");
        }
    }

    private static boolean finite(float value) { return Float.isFinite(value) && value >= 0; }

    public static AbilitySnapshot capture(UUID player, ResourceLocation dimension, PlayerAbilityData data) {
        ResourceLocation id = data.getAbility();
        // 大きすぎる未知のIDはディスク上に保ち、ログインを失敗させる代わりに無効な表示を見せる。
        if (id != null && id.toString().length() > MAX_ID_LENGTH) id = null;
        return new AbilitySnapshot(player, dimension, id, id == null ? 0 : data.getLevel(),
                id == null ? 0 : data.getLevelExperience(),
                data.getCp(), data.getMaxCp(), data.getOverload(), data.getMaxOverload(),
                id != null && data.isActive(), data.isReadOnly(), data.isInterfering(), SkillSnapshot.capture(data));
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUUID(player);
        buffer.writeUtf(dimension.toString(), MAX_ID_LENGTH);
        buffer.writeBoolean(ability != null);
        if (ability != null) buffer.writeUtf(ability.toString(), MAX_ID_LENGTH);
        buffer.writeByte(level);
        buffer.writeFloat(levelExperience);
        buffer.writeFloat(cp); buffer.writeFloat(maxCp);
        buffer.writeFloat(overload); buffer.writeFloat(maxOverload);
        buffer.writeBoolean(active); buffer.writeBoolean(readOnly); buffer.writeBoolean(interfering); skills.encode(buffer);
    }

    public static AbilitySnapshot decode(FriendlyByteBuf buffer) {
        return new AbilitySnapshot(buffer.readUUID(), ResourceLocation.parse(buffer.readUtf(MAX_ID_LENGTH)),
                buffer.readBoolean() ? ResourceLocation.parse(buffer.readUtf(MAX_ID_LENGTH)) : null,
                buffer.readUnsignedByte(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(),
                buffer.readFloat(), buffer.readFloat(), buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(), SkillSnapshot.decode(buffer));
    }

    public boolean belongsTo(UUID owner, ResourceLocation world) {
        return player.equals(owner) && dimension.equals(world);
    }

    public void applyTo(PlayerAbilityData data) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(AbilityDataSchema.VERSION_KEY, readOnly ? Integer.MAX_VALUE : AbilityDataSchema.VERSION);
        CompoundTag abilityPart = AbilityDataSchema.section(tag, AbilityDataSchema.ABILITY);
        if (ability != null) abilityPart.putString(AbilityDataSchema.CATEGORY, ability.toString());
        abilityPart.putInt(AbilityDataSchema.LEVEL, level);
        abilityPart.putFloat(AbilityDataSchema.LEVEL_EXP, levelExperience);
        CompoundTag cpPart = AbilityDataSchema.section(tag, AbilityDataSchema.CP);
        cpPart.putFloat(AbilityDataSchema.CUR_CP, cp); cpPart.putFloat(AbilityDataSchema.MAX_CP, maxCp);
        cpPart.putFloat(AbilityDataSchema.CUR_OVERLOAD, overload); cpPart.putFloat(AbilityDataSchema.MAX_OVERLOAD, maxOverload);
        cpPart.putBoolean(AbilityDataSchema.ACTIVATED, active);
        skills.writeToTag(tag);
        data.load(tag);
        // 妨害の印は保存される形には含まれない。保存せず、伝えるだけ。
        data.setInterfering(interfering);
    }
}
