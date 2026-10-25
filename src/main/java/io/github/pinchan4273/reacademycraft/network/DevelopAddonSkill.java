package io.github.pinchan4273.reacademycraft.network;

import io.github.pinchan4273.reacademycraft.develop.DeveloperMenu;
import javax.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * 開発機の画面からの、外部の技能の学習の要求（クライアント→サーバー、1.4.0のS4、司令室11の裁定D11）。送るのは技能の名前だけで、
 * サーバーが、開いている開発機のメニューと今の状態で条件を確かめてから始める（{@link DeveloperMenu#startAddon}）。
 * 本体の技能は旧い経路（メニューのボタン）だけで学ぶので、ここでは断る。
 */
public record DevelopAddonSkill(ResourceLocation skill) {
    private static final int MAX_ID = 256;

    public DevelopAddonSkill {
        if (skill == null || skill.toString().length() > MAX_ID) throw new IllegalArgumentException("Invalid skill");
    }
    public void encode(FriendlyByteBuf buffer) { buffer.writeUtf(skill.toString(), MAX_ID); }
    public static DevelopAddonSkill decode(FriendlyByteBuf buffer) {
        var id = ResourceLocation.tryParse(buffer.readUtf(MAX_ID));
        if (id == null) throw new IllegalArgumentException("Invalid skill identifier");
        return new DevelopAddonSkill(id);
    }
    /** 学習を始めたか（試験のため）。 */
    public boolean handle(@Nullable ServerPlayer player) {
        return player != null && player.containerMenu instanceof DeveloperMenu menu && menu.startAddon(player, skill);
    }
}
