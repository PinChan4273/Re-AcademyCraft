package io.github.pinchan4273.reacademycraft.develop;

import javax.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * 開発機の、初回の取得・磁気コイルでの変更の計画（1.5.2）。サーバーが計算して画面へ送る、表示のためだけの値で、
 * 開始の可否と、実際に使う因子はサーバーが確かめ直す。開発中は、開始の時に固定した計画を送る。
 * <ul>
 * <li>action: {@link DevelopmentSession#ACQUIRE}または{@link DevelopmentSession#RESET}。</li>
 * <li>mode: ランダム・因子の指定・同じ系統の因子だけ・候補なし。</li>
 * <li>candidates: ランダムの候補の数（ランダムのときだけ意味がある）。</li>
 * <li>category・place・index: 指定の系統と、使う因子の場所（因子の指定のときだけ）。</li>
 * <li>level: 完了した後のレベル（取得は1、変更は今のレベルより1つ下）。</li>
 * </ul>
 */
public record FactorPlan(int action, Mode mode, int candidates, @Nullable ResourceLocation category, @Nullable FactorSearch.Place place,
                         int index, int level) {
    /** 計画の種類。 */
    public enum Mode {
        /** 因子が無いので、候補から均等に抽選する（結果は完了の時にサーバーが決める）。 */
        RANDOM,
        /** 因子で系統を指定する。 */
        FACTOR,
        /** 変更で、今と同じ系統の因子だけを持っている（始めずに、因子を外すよう案内する）。 */
        SAME_ONLY,
        /** ランダムの候補が無い（始めない）。 */
        NO_CANDIDATE
    }
    /** 送る候補の数の上限（読むときの検査）。 */
    public static final int MAX_CANDIDATES = 4096;

    public FactorPlan {
        if (action != DevelopmentSession.ACQUIRE && action != DevelopmentSession.RESET) throw new IllegalArgumentException("Not a category action: " + action);
        java.util.Objects.requireNonNull(mode, "mode");
        if (candidates < 0 || candidates > MAX_CANDIDATES) throw new IllegalArgumentException("Bad candidate count: " + candidates);
        if (mode == Mode.FACTOR && (category == null || place == null || !place.validIndex(index)))
            throw new IllegalArgumentException("A factor plan needs its category and place");
        if (mode != Mode.FACTOR) { category = null; place = null; index = 0; }
        if (level < 0 || level > 100) throw new IllegalArgumentException("Bad level: " + level);
    }
    /** 始められる計画か（ランダムか因子の指定）。 */
    public boolean startable() { return mode == Mode.RANDOM || mode == Mode.FACTOR; }

    static FactorPlan random(int action, int candidates, int level) {
        return new FactorPlan(action, candidates == 0 ? Mode.NO_CANDIDATE : Mode.RANDOM, candidates, null, null, 0, level);
    }
    static FactorPlan factor(int action, FactorSearch.Found found, int level) {
        return new FactorPlan(action, Mode.FACTOR, 0, found.category().id(), found.place(), found.index(), level);
    }
    static FactorPlan sameOnly(int action, int level) { return new FactorPlan(action, Mode.SAME_ONLY, 0, null, null, 0, level); }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(action);
        buffer.writeEnum(mode);
        buffer.writeVarInt(candidates);
        if (mode == Mode.FACTOR) {
            buffer.writeResourceLocation(category);
            buffer.writeEnum(place);
            buffer.writeVarInt(index);
        }
        buffer.writeVarInt(level);
    }
    public static FactorPlan decode(FriendlyByteBuf buffer) {
        int action = buffer.readVarInt();
        var mode = buffer.readEnum(Mode.class);
        int candidates = buffer.readVarInt();
        ResourceLocation category = null; FactorSearch.Place place = null; int index = 0;
        if (mode == Mode.FACTOR) {
            category = buffer.readResourceLocation();
            place = buffer.readEnum(FactorSearch.Place.class);
            index = buffer.readVarInt();
        }
        return new FactorPlan(action, mode, candidates, category, place, index, buffer.readVarInt());
    }
}
