package io.github.pinchan4273.reacademycraft.network;

import io.github.pinchan4273.reacademycraft.develop.FactorPlan;
import javax.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;

/**
 * 開いている開発機の、初回の取得・磁気コイルでの変更の計画（サーバー→クライアント、1.5.2、76番）。計画が変わったときだけ送る。
 * 画面は、メニューのIDが今のメニューと同じときだけ使う。表示のためだけの値で、開始の可否と使う因子はサーバーが確かめ直す。
 * plan が null なら、出す計画が無い（開発機が使えないなど）。
 * refused が true なら、開始の要求（{@link DevelopCategoryStart}）に載っていた計画が、サーバーが開始の時に計算し直した計画と違ったので
 * 始めなかった（通信の版70）。plan はその時の新しい計画で、画面はそれを出し直す。
 */
public record DevelopFactorPlan(int menu, @Nullable FactorPlan plan, boolean refused) {
    public DevelopFactorPlan(int menu, @Nullable FactorPlan plan) { this(menu, plan, false); }
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(menu);
        buffer.writeBoolean(plan != null);
        if (plan != null) plan.encode(buffer);
        buffer.writeBoolean(refused);
    }
    public static DevelopFactorPlan decode(FriendlyByteBuf buffer) {
        int menu = buffer.readVarInt();
        var plan = buffer.readBoolean() ? FactorPlan.decode(buffer) : null;
        return new DevelopFactorPlan(menu, plan, buffer.readBoolean());
    }
}
