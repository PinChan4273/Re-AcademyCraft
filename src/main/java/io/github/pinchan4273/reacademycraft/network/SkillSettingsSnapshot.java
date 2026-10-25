package io.github.pinchan4273.reacademycraft.network;

import io.github.pinchan4273.reacademycraft.skill.AbilitySettings;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * 本人の能力設定の今の値（サーバー→クライアント、1.4.0のS1）。能力設定の画面は、この値だけを表示する（サーバーが正本）。
 * readableがfalseなら、能力データが読めないか読み取り専用で、値は空（画面は変更を受け付けない）。
 * 値はサーバーの並び（{@link AbilitySettings#all()}の順）のまま送る。
 */
public record SkillSettingsSnapshot(boolean readable, Map<ResourceLocation, String> values) {
    private static final int MAX_ENTRIES = 32, MAX_ID = 64;

    public SkillSettingsSnapshot {
        if (values == null || values.size() > MAX_ENTRIES || (!readable && !values.isEmpty())) throw new IllegalArgumentException("Invalid settings snapshot");
        for (var e : values.entrySet()) {
            if (e.getKey().toString().length() > MAX_ID || e.getValue() == null || e.getValue().isEmpty()
                    || e.getValue().length() > AbilitySettings.MAX_VALUE) throw new IllegalArgumentException("Invalid settings snapshot entry");
        }
        values = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBoolean(readable);
        buffer.writeVarInt(values.size());
        for (var e : values.entrySet()) {
            buffer.writeUtf(e.getKey().toString(), MAX_ID);
            buffer.writeUtf(e.getValue(), AbilitySettings.MAX_VALUE);
        }
    }

    public static SkillSettingsSnapshot decode(FriendlyByteBuf buffer) {
        boolean readable = buffer.readBoolean();
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_ENTRIES) throw new IllegalArgumentException("Invalid settings snapshot size");
        var values = new LinkedHashMap<ResourceLocation, String>();
        for (int i = 0; i < count; i++) {
            var id = ResourceLocation.tryParse(buffer.readUtf(MAX_ID));
            if (id == null || values.put(id, buffer.readUtf(AbilitySettings.MAX_VALUE)) != null)
                throw new IllegalArgumentException("Invalid or duplicated setting identifier");
        }
        return new SkillSettingsSnapshot(readable, values);
    }
}
