package io.github.pinchan4273.reacademycraft.network;

import io.github.pinchan4273.reacademycraft.develop.DeveloperMenu;
import io.github.pinchan4273.reacademycraft.develop.DevelopmentSession;
import io.github.pinchan4273.reacademycraft.develop.FactorPlan;
import javax.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

/**
 * 開発機のコンソールからの、初回の取得・磁気コイルでの変更の開始の要求（クライアント→サーバー、1.5.2）。
 * クライアントが画面に出した計画（ランダムか因子の指定か、候補の数、因子の系統と場所、変更の後のレベル）を一緒に送る。
 * サーバーは開始の時に計画を計算し直し、送られた計画と違えば始めずに、新しい計画を出し直す（{@link DeveloperMenu#startCategory}）。
 * expected が null なら、クライアントは計画を受け取っていない（必ず断って、計画を出し直す）。
 */
public record DevelopCategoryStart(int menu, int action, @Nullable FactorPlan expected) implements AcademyPayload {
    public DevelopCategoryStart {
        if (action != DevelopmentSession.ACQUIRE && action != DevelopmentSession.RESET) throw new IllegalArgumentException("Not a category action: " + action);
        if (expected != null && expected.action() != action) throw new IllegalArgumentException("The plan is for another action");
    }
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(menu);
        buffer.writeVarInt(action);
        buffer.writeBoolean(expected != null);
        if (expected != null) expected.encode(buffer);
    }
    public static DevelopCategoryStart decode(FriendlyByteBuf buffer) {
        int menu = buffer.readVarInt();
        int action = buffer.readVarInt();
        return new DevelopCategoryStart(menu, action, buffer.readBoolean() ? FactorPlan.decode(buffer) : null);
    }
    /** 始めたか（テストのため）。開いているメニューのIDが違えば何もしない。 */
    public boolean handle(@Nullable ServerPlayer player) {
        return player != null && player.containerMenu instanceof DeveloperMenu open && open.containerId == menu
                && open.startCategory(player, action, expected);
    }
}
