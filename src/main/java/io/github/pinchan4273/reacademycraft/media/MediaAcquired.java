package io.github.pinchan4273.reacademycraft.media;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;

/**
 * 原作MediaAcquireData: modに同梱のメディアのうち、このプレイヤーがメディアプレイヤーへ入れたもの。原作はこれを同期する
 * データ部分として持ち、そのNBTは"acquired"の下のメディアidのリスト。移植版は同じリストをプレイヤーの永続データに置き、
 * MediaSnapshotでクライアントへ伝える。プレイヤーが自分のフォルダから加えたメディアは最初からその人のもので、原作と同じく
 * ここには現れない。
 *
 * 原作のdefault.confはmodに同梱のメディアを、メディアアイテムの番号の順に挙げる。INTERNALはそのリスト。
 */
public final class MediaAcquired {
    public static final List<String> INTERNAL = List.of("level5_judgelight", "only_my_railgun", "sisters_noise");
    private static final String KEY = "academy_media";
    private MediaAcquired() { }

    public static Set<String> installed(Player player) {
        var out = new LinkedHashSet<String>();
        if (player == null) return out;
        // 原作がもう知らないidは、原作のfromNBTと同じく読み込み時に捨てる。
        for (Tag tag : player.getPersistentData().getList(KEY, Tag.TAG_STRING))
            if (INTERNAL.contains(tag.getAsString())) out.add(tag.getAsString());
        return out;
    }
    public static boolean has(Player player, String id) { return installed(player).contains(id); }
    /** 原作のinstall()と同じくサーバー専用。プレイヤーが既に持っていればfalse。 */
    public static boolean install(Player player, String id) {
        if (player == null || !INTERNAL.contains(id) || has(player, id)) return false;
        var ids = installed(player); ids.add(id);
        write(player, List.copyOf(ids));
        return true;
    }
    /** クライアント専用: サーバーが最後に伝えた内容。 */
    public static void accept(Player player, List<String> ids) {
        if (player != null) write(player, ids.stream().filter(INTERNAL::contains).distinct().toList());
    }
    private static void write(Player player, List<String> ids) {
        var list = new ListTag();
        // 原作の順（そのビット集合の順）を保つ。
        for (var id : INTERNAL) if (ids.contains(id)) list.add(StringTag.valueOf(id));
        player.getPersistentData().put(KEY, list);
    }
    public static String key() { return KEY; }
}
