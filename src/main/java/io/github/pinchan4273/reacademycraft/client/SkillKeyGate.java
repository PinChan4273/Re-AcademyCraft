package io.github.pinchan4273.reacademycraft.client;

import java.util.ArrayList;
import java.util.List;

/**
 * TOGGLE-GUI-001: 能力のキー（技能の4つの枠、能力・プリセット・編集・端末のキー）の、Minecraftの型を使わない純粋な判断。
 * {@link AbilityControls}が使い、オフライン試験でも同じクラスを確かめる。
 *
 * <p>画面（インベントリ・ESC・コンテナ等）を閉じると、ゲームは物理的なキーの状態を読み直して押下にする
 * （{@code MouseHandler.grabMouse}→{@code KeyMapping.setAll}）。さらに、押したままのキーボードのキーにはOSのキーリピートが
 * 届き、画面が無い間は届くたびにクリック（{@code KeyMapping.click}）になる。そのため、画面の中で押したまま閉じたキーは、
 * 閉じた直後からCAST等を送れてしまう（押し続け型のHELDを止めるだけでは足りない）。
 *
 * <p>{@link Latch}は、操作できない間（画面・ポーズ・フォーカスの喪失。技能の枠ではデータ端末の表示中も）を見たキーを、
 * 物理的なキー（GLFWが報告する状態）が離されるまで「古い」とする。古いキーは、新しい発動（CAST・押し続けの開始・
 * 能力やプリセットの切替・画面を開く）に使わない。押し続けている技能の中断（CANCEL）・離し（RELEASE）の通知は妨げない。
 */
public final class SkillKeyGate {
    private SkillKeyGate() { }

    /** GLFWのキー・ボタンのイベントの種類（{@code GLFW_RELEASE}・{@code GLFW_PRESS}・{@code GLFW_REPEAT}と同じ値）。 */
    public static final int RELEASE = 0, PRESS = 1, REPEAT = 2;

    /**
     * 1つのキーの「離されるまで数えない」状態。
     *
     * <p>QA 3回目（TOGGLE-QA3-F01）: 古いキーが離されたtickには、離される前に届いたリピートのクリックが溜まっていることがある。
     * tickの初めの状態（押下・クリックの有無）だけでは、「リピートの後に離した」のか「離してから押し直した」のか区別できない。
     * そのため、GLFWのイベント（{@link #event}）で、古くなった後に本当に離され（ウィンドウにフォーカスがある間の離し）、
     * その後に押された（リピートでない押下）ことを数える。離されたtickに溜まっていたクリックは捨て（{@link #usable()}がfalse）、
     * 離してから押し直したクリックは、同じtickの中でも新しい押下として使う（{@link #newPress()}）。
     */
    public static final class Latch {
        private boolean stale, released, pressed, usable = true, newPress;
        /** F03: フォーカスの喪失の印（{@link SteeringGate.FocusMark}）。印がある間は、離されて見えても古いまま。 */
        private final SteeringGate.FocusMark mark = new SteeringGate.FocusMark();
        /**
         * GLFWのイベントを、届いた順に知らせる。古くない間は何もしない。フォーカスが無い間に届く離しは、GLFWが
         * フォーカスの喪失のときに作る合成の離し（フォーカスの無いウィンドウには本当のキーのイベントは届かない）なので数えない。
         * 押下（{@link #PRESS}）は、本当の離しの後のものだけを新しい押下とする。リピート（{@link #REPEAT}）は数えない。
         */
        public void event(int action, boolean windowActive) { event(action, windowActive, true); }
        /**
         * QA 4回目（TOGGLE-QA4-F01-R1）: {@code operable}は、そのイベントが届いた時点で操作できたか（画面・ポーズ・フォーカス。
         * 技能の枠はデータ端末も）。操作できない間の押下は、その後に画面・端末が閉じても新しい押下にしない（キーは押されたので、
         * それまでに見た離しも無効にする）。操作できない間の離しは、本当の離しとして数える。
         * F03: 先に印へ知らせる。印が付いた（戻すクリック・合成の離し等）キーは、その時点から古い。印のある間の押下は新しい押下にしない。
         */
        public void event(int action, boolean windowActive, boolean operable) {
            mark.event(action, windowActive);
            if (mark.marked() && !stale) { stale = true; released = false; pressed = false; }
            if (!stale || !windowActive) return;
            if (action == RELEASE) released = true;
            else if (action == PRESS) {
                if (!operable || mark.marked()) { released = false; pressed = false; }
                else if (released) pressed = true;
            }
        }
        /**
         * F03: 各tickに1回、そのtickのフォーカスで呼ぶ（{@link #update}の後）。キーボードのキーか（{@code keyboard}、マウスのボタンはfalse）で、
         * 印の付け方が変わる（{@link SteeringGate.FocusMark}）。
         */
        public void focusTick(boolean windowActive, boolean keyboard) { mark.tick(windowActive, keyboard); }
        /** フォーカスの喪失の印があるか。 */
        public boolean focusMarked() { return mark.marked(); }
        /**
         * 各tickの初めに呼ぶ。物理的なキーが離されていれば古くない。操作できない間は、押していてもいなくても古い
         * （その間に押し始めたキーも、戻った後に離されるまで数えない）。戻ったtickに離されていれば、すぐ使える。
         * F03: フォーカスの喪失の印がある間は、離されて見えても古いまま（本当の離しのイベントで印が解けるまで）。
         */
        public boolean update(boolean suspended, boolean physicalDown) {
            boolean wasStale = stale;
            newPress = false;
            if (stale && !mark.marked()) {
                if (pressed) { stale = false; newPress = true; }
                else if (!physicalDown) stale = false;
            }
            // 古くなくなったtickのクリックは、離してから押し直したものだけ使う（離される前のリピートのクリックは捨てる）。
            usable = !stale && (!wasStale || newPress);
            if (suspended) {
                stale = true; usable = false; newPress = false; pressed = false;
                // 操作できない間（印のあるキーを除く）に離されているキーは、戻った後の最初の押下を新しい押下とする。
                // 操作できない間の押し直し（データ端末の中等）は、戻った後へ持ち越さない。
                released = !physicalDown && !mark.marked();
            } else if (!stale) { released = false; pressed = false; }
            return stale;
        }
        public boolean stale() { return stale; }
        /** このtickに取り出すクリックを、新しい発動に使ってよいか。 */
        public boolean usable() { return usable; }
        /** このtickに古くなくなり、そのクリックが離してから押し直したものか（前のtickの押下の状態を引き継がない）。 */
        public boolean newPress() { return newPress; }
    }

    /** サーバーへ送る物（{@code AbilityAction}の種類に対応）。 */
    public enum Send { CAST, HELD, RELEASE, CANCEL }

    /**
     * 技能の枠1つの、1tickの入力。
     *
     * @param canSend      画面が無く、ポーズでなく、ウィンドウにフォーカスがある
     * @param terminalOpen データ端末が開いている
     * @param down         キーの押下の状態（{@code KeyMapping.isDown}）
     * @param wasDown      前のtickの{@code down}
     * @param clicked      このtickまでにクリックが溜まっていた（取り出して捨てるかどうかは結果による）
     * @param stale        {@link Latch}の結果
     * @param toggled      切り替え型の技能（新しい押下でだけCAST）
     * @param heldSkill    押し続け型の技能
     * @param active       能力がON（データが無ければfalse）
     * @param blocked      能力を使えない・クールダウン中・端末を開いている（原作shouldAbort）
     * @param player       プレイヤーがいる
     * @param heartbeat    押し続けの心拍を送るtick
     * @param held         押し続けを送っている（前のtickまで）
     * @param aborted      中断されたキー（離されるまで何もしない、原作realState）
     */
    public record Input(boolean canSend, boolean terminalOpen, boolean down, boolean wasDown, boolean clicked, boolean stale,
                        boolean toggled, boolean heldSkill, boolean active, boolean blocked, boolean player, boolean heartbeat,
                        boolean held, boolean aborted) { }

    /** 送る物（送る順）と、次のtickの押し続け・中断の状態。 */
    public record Result(List<Send> sends, boolean held, boolean aborted) { }

    /**
     * {@code AbilityControls}の枠ごとの処理（`39c06e21`と同じ順序と条件）に、古いキーの扱いだけを足したもの。
     * `a5a5a298`で足した「送れない間は中断の印を立てる」は、この古いキーの扱いに置き換えた（あの印はHELDしか止めなかった）。
     * 古いキーは、CASTも押し続けの開始も送らない。中断・離し・心拍は従来どおり。
     */
    public static Result slot(Input in) {
        var sends = new ArrayList<Send>(2);
        // 切り替え型の技能は、新しく押されたときだけ受け付ける（押し続けるとクリックが繰り返され、OFFとONを繰り返すため）。
        boolean fresh = !in.toggled() || !in.wasDown();
        boolean cast = in.clicked() && in.canSend() && !in.terminalOpen() && fresh && !in.stale();
        if (cast) sends.add(Send.CAST);
        boolean aborted = in.aborted() && in.down();
        boolean down = in.canSend() && in.down() && in.heldSkill() && in.active() && !aborted;
        if (down && in.blocked()) {
            // 押している間に使えなくなった: 中断する。既に使えない状態で押された: 開始しない。
            if (in.held()) sends.add(Send.CANCEL);
            return new Result(sends, false, true);
        }
        // 古いキーでは押し続けを始めない（既に押し続けているなら、操作できない間を通っていないので古くならない）。
        if (down && !in.held() && in.stale()) down = false;
        if (down && (!in.held() || in.heartbeat())) sends.add(Send.HELD);
        else if (!down && in.held() && in.player()) sends.add(in.canSend() && in.active() ? Send.RELEASE : Send.CANCEL);
        // キューに入ったクリックは、押し続けている技能をすべて離す必要がある（leaseの期限まで有効のままにしない）。
        if (cast && !down && !in.held() && in.heldSkill()) sends.add(Send.RELEASE);
        return new Result(sends, down, aborted);
    }
}
