package io.github.pinchan4273.reacademycraft.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * 術者自身のクライアントへ、切り替え型技能のモードがオン・オフになったことを伝える。原作のactivate処理と同じく、
 * 能力キーが能力をオフにする代わりにそのモードを終えられるようにする。
 */
public record SkillModeState(ResourceLocation skill, boolean active) implements AcademyPayload {
    public void encode(FriendlyByteBuf buffer) { buffer.writeResourceLocation(skill); buffer.writeBoolean(active); }
    public static SkillModeState decode(FriendlyByteBuf buffer) { return new SkillModeState(buffer.readResourceLocation(), buffer.readBoolean()); }
    public static void send(net.minecraft.server.level.ServerPlayer player, ResourceLocation skill, boolean active) {
        // 本人だけへの通知でエンティティIDを含まないので、作り直しの最中にも送れる（{@link SelfNotice}）。
        SelfNotice.send(player, new SkillModeState(skill, active));
    }
}
