package io.github.pinchan4273.reacademycraft.network;

import io.github.pinchan4273.reacademycraft.develop.DevelopmentSession;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * 開いている開発機で今学べる外部の技能（サーバー→クライアント、1.4.0のS4）。メニューのデータの48ビットには入れず、一覧が変わったときだけ
 * 送る。画面は、メニューの番号が今のメニューと同じときだけ使う。表示のためだけの値で、学習の可否はサーバーが確かめ直す。
 */
public record DevelopAddonAvailability(int menu, List<ResourceLocation> skills) {
    private static final int MAX_ID = 256;

    public DevelopAddonAvailability {
        if (skills.size() > DevelopmentSession.MAX_ADDON_SKILLS) throw new IllegalArgumentException("Too many add-on skills");
        skills = List.copyOf(skills);
    }
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(menu);
        buffer.writeVarInt(skills.size());
        for (var skill : skills) buffer.writeUtf(skill.toString(), MAX_ID);
    }
    public static DevelopAddonAvailability decode(FriendlyByteBuf buffer) {
        int menu = buffer.readVarInt(), size = buffer.readVarInt();
        if (size < 0 || size > DevelopmentSession.MAX_ADDON_SKILLS) throw new IllegalArgumentException("Too many add-on skills");
        var skills = new ArrayList<ResourceLocation>(size);
        for (int i = 0; i < size; i++) {
            var id = ResourceLocation.tryParse(buffer.readUtf(MAX_ID));
            if (id == null) throw new IllegalArgumentException("Invalid skill identifier");
            skills.add(id);
        }
        return new DevelopAddonAvailability(menu, skills);
    }
}
