package io.github.pinchan4273.reacademycraft.client.media;

import io.github.pinchan4273.reacademycraft.world.item.MediaItem;
import java.nio.file.Path;
import javax.annotation.Nullable;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.ResourceLocation;

/**
 * 原作Media: メディアプレイヤーで再生できる曲。内部のものはmodに同梱され、メディアアイテムで取得する必要がある。
 * 外部のものはプレイヤーが自分のacmedia/sourceフォルダに置いた.oggで、最初からその人のものとなり、名前と説明を変更できる。
 *
 * @param source 読み込み元: 内部のものはmodの音、外部のものはプレイヤーのファイル
 * @param location 音自身の名前。音声エンジンはこの名前で扱う
 * @param cover 一覧で横に描くカバー
 */
public record Media(boolean external, String id, @Nullable ResourceLocation internalSource, @Nullable Path file,
                    ResourceLocation location, ResourceLocation cover, float lengthSecs) {
    public String name() {
        return external ? MediaSettings.name(id) : I18n.get(MediaItem.nameKey(id));
    }
    public String desc() {
        return external ? MediaSettings.desc(id) : I18n.get(MediaItem.descKey(id));
    }
    public String displayLength() { return displayTime(lengthSecs); }
    /** 原作MediaBackend.getDisplayTime: 分と秒、それぞれ2桁。 */
    public static String displayTime(float secs) {
        int total = (int) secs;
        return wrap(total / 60) + ":" + wrap(total % 60);
    }
    private static String wrap(int x) { return x < 10 ? "0" + x : Integer.toString(x); }
}
