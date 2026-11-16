package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.network.VectorAnalysisSync;
import io.github.pinchan4273.reacademycraft.skill.VectorAnalysis;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * 1.5.2: 本人のベクトル操作の解析度（サーバーが最後に送った{@link VectorAnalysisSync}）。画面（HUDの選択、
 * 能力設定とデータ端末の一覧）は、ここから読む。いつも100%のバニラの物理は一覧に無い（{@link #value}は1を返す）。
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class ClientVectorAnalysis {
    private static List<VectorAnalysisSync.Entry> entries = List.of();
    private ClientVectorAnalysis() { }
    public static void receive(VectorAnalysisSync sync) { entries = sync.entries(); }
    /** 受け取った一覧（サーバーが覚えた順。変更できない）。 */
    public static List<VectorAnalysisSync.Entry> entries() { return entries; }
    /** この分類の解析度（物理は1、一覧に無ければ0）。 */
    public static float value(String key) {
        if (VectorAnalysis.PHYSICAL_KEY.equals(key)) return 1;
        for (var entry : entries) if (entry.key().equals(key)) return entry.value();
        return 0;
    }
    /** このキーの項目（一覧に無ければnull）。 */
    public static @javax.annotation.Nullable VectorAnalysisSync.Entry entry(String key) {
        for (var entry : entries) if (entry.key().equals(key)) return entry;
        return null;
    }
    /** キーの表示の名前。一覧に無ければ、使い手の名前などの無い名前（{@link VectorAnalysis#displayName}）。 */
    public static Component name(String key) {
        var entry = entry(key);
        return entry != null ? name(entry) : VectorAnalysis.displayName(key, null);
    }
    /** 項目の表示の名前（翻訳があればその名前、なければID）。 */
    public static Component name(VectorAnalysisSync.Entry entry) {
        return VectorAnalysis.displayName(entry.key(), entry.label().isEmpty() ? null : entry.label());
    }
    @SubscribeEvent public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) { entries = List.of(); }
}
