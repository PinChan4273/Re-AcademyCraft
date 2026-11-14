package io.github.pinchan4273.reacademycraft.network;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;

/**
 * 1.5.2: 本人のクライアントへ送る、ベクトル操作の解析度の一覧（サーバーの{@code VectorAnalysis}が送る）。
 * 各項目は分類のキー（{@code physical}・{@code magic}・{@code bypass}・{@code ability/<カテゴリ>}・{@code dark_matter/<UUID>}・
 * {@code tag/<ID>}・{@code type/<ID>}）、解析度（0〜1）、表示の名前（未元物質の使い手の名前など。無ければ空）。
 * いつも100%のバニラの物理と、0の分類は含まない。毎回、全部を送る（受け手は前の一覧を置き換える）。
 */
public record VectorAnalysisSync(List<Entry> entries) {
    /** 送る項目の上限（サーバーが持つ上限と同じ）。 */
    public static final int MAX_ENTRIES = 256;
    public record Entry(String key, float value, String label) { }

    public VectorAnalysisSync { entries = List.copyOf(entries.size() > MAX_ENTRIES ? entries.subList(0, MAX_ENTRIES) : entries); }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(entries.size());
        for (var entry : entries) {
            buffer.writeUtf(entry.key(), 256);
            buffer.writeFloat(entry.value());
            buffer.writeUtf(entry.label(), 64);
        }
    }
    public static VectorAnalysisSync decode(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        if (size < 0 || size > MAX_ENTRIES) throw new IllegalArgumentException("Too many analysis entries: " + size);
        List<Entry> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            String key = buffer.readUtf(256);
            float value = buffer.readFloat();
            String label = buffer.readUtf(64);
            entries.add(new Entry(key, Float.isFinite(value) ? Math.max(0, Math.min(1, value)) : 0, label));
        }
        return new VectorAnalysisSync(entries);
    }
}
