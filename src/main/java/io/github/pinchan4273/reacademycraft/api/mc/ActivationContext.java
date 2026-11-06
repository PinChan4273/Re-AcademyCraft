package io.github.pinchan4273.reacademycraft.api.mc;

import io.github.pinchan4273.reacademycraft.api.AbilityId;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;

/**
 * 1回の発動（とそのセッション）の文脈（公開API、実験的）。Re:AcademyCraftが作って振る舞いへ渡す。サーバーのスレッドでだけ使い、
 * 振る舞いの呼び出しの外へ持ち出さない（セッションの間は、同じ文脈が渡される）。
 */
public interface ActivationContext {
    AbilityId skill();
    AbilityActor actor();
    /** 自然能力者（CP・熟練度を持つプレイヤー）。外部の主体の発動ではnull。 */
    @Nullable ServerPlayer naturalUser();
    /** 発動したキーのスロット（0〜3）。キーによらない発動では-1。 */
    int slot();
    /** セッションが始まってからのtick（発動の呼び出しの中では0）。 */
    int age();
    /** その技能の熟練度（0〜1）。自然能力者でなければ0。 */
    float proficiency();
    /**
     * 資源を払う（不可分の取引。払えなければ何も変えずにfalse）。自然能力者は、その技能の設定の倍率を掛けたCPと過負荷を払う
     * （クリエイティブは免除）。能力がオフ・過負荷の錠がかかっているときも払えない。
     */
    boolean pay(float cp, float overload);
    /** 熟練度を加える（実際の使用による獲得。設定の倍率を掛ける）。自然能力者でなければ何もしない。 */
    void addProficiency(float amount);
    /** その技能のクールダウン（tick、0〜72000）を置く。自然能力者でなければ何もしない。 */
    void setCooldown(int ticks);
}
