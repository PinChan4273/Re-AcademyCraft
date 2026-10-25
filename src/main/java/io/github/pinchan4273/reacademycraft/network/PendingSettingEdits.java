package io.github.pinchan4273.reacademycraft.network;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/**
 * 能力設定の画面が送った変更の保留（1.4.0のS1、WORK-143 F140-S1-F01の修正）。クライアントのクラスに依存しないので、GameTestでも試せる。
 * <p>
 * サーバーは、受け入れても断っても今の値を返す（{@link SkillSettingEdit#handle}）。その値が届けば、保留はすべて終わる
 * （{@link #answered()}）。返事が届かないまま{@link #TIMEOUT_TICKS}tickが経った変更は、念のため保留を終える（{@link #expire}）。
 * 画面の値は、届いたサーバーの値のままなので、保留が終わると、変わっていない設定は元の表示に見える。
 */
public final class PendingSettingEdits {
    /** 返事を待つ上限（クライアントのtick。約2秒）。localhostの往復と、混んだサーバーの数tickの遅れを十分に上回る。 */
    public static final int TIMEOUT_TICKS = 40;
    private final Map<ResourceLocation, Long> sentAt = new HashMap<>();

    /** 変更を送った（同じ設定をもう一度送ったら、待ちの始まりを新しくする）。 */
    public void sent(ResourceLocation setting, long tick) { sentAt.put(setting, tick); }
    /** サーバーの値が届いた: 送ったものすべての保留が終わる。 */
    public void answered() { sentAt.clear(); }
    /** 待ちが{@link #TIMEOUT_TICKS}を超えた変更の保留を終える。終えたものがあればtrue。 */
    public boolean expire(long tick) { return sentAt.values().removeIf(at -> tick - at >= TIMEOUT_TICKS); }
    public boolean isEmpty() { return sentAt.isEmpty(); }
    public boolean contains(ResourceLocation setting) { return sentAt.containsKey(setting); }
    public Set<ResourceLocation> settings() { return Set.copyOf(sentAt.keySet()); }
}
