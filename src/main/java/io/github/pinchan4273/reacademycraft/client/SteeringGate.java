package io.github.pinchan4273.reacademycraft.client;

/**
 * TOGGLE-GUI-001: 移動キーで技能を操る入力（Flashingの方向の狙い、Storm Wingの方向の推進）を止める間か。
 *
 * <p>画面（インベントリ・ESC・コンテナ・この移植の画面等）、データ端末（画面ではなくゲームの上に描くパネル。開いている間も
 * 移動キーはプレイヤーを動かす）、ウィンドウのフォーカスの喪失の間は止める。止めている間もモードはONのまま（サーバーが正本）で、
 * 止める前から押していたキーも、止めている間に押し始めたキーも、戻った後は一度離されるまで数えない。
 * Minecraftの型を使わない純粋な判定（オフライン試験で確かめる）。
 */
public final class SteeringGate {
    private SteeringGate() { }

    public static boolean suspended(boolean screenOpen, boolean terminalOpen, boolean windowActive) {
        return screenOpen || terminalOpen || !windowActive;
    }

    /**
     * TOGGLE-GUI-001 F03（P-C′＋M2。2026-10-22のK21の実機観測に基づく）: 1つのキー・ボタンの、フォーカスの喪失の印。
     * 印のあるキーは、そのキーの本当の離し（ウィンドウにフォーカスがある間の離し）を見るまで、押下もリピートも新しい入力に使わない。
     * 時間（tickの数）では解かない（以前の25tickの猶予の代わり）。
     *
     * <p>GLFW（1.20.1が使うLWJGL 3.3.1のnativeは3.4.0）は、フォーカスを失うと、フォーカスの通知の後に、押していたキー・ボタンへ
     * 合成の離しを出す。戻った後も押したままのキーには、OSのキーリピートが届き、GLFWはそれを新しい押下（PRESS）として報告する。
     * 押したまま離れてAlt+Tab等でリピートが止まったキーは、戻った後に物理的に離しても、GLFWが離しを捨てるので何も届かない
     * （K21のS5a・S5b）。そのため、戻った直後の押下やGLFWの状態からは、本当の新しい押下かを区別できない。
     * <ul>
     * <li>キーボードのキー（P-C′）: フォーカスの無いtickで、押していたかに関わらず印を付ける（他の窓で押し始めて戻ったキーにも。K21のS4）。
     * <li>マウスのボタン（M2）: 次の2つだけに印を付ける。
     *     <ul>
     *     <li>フォーカスの無い間に届いたイベント（押したまま失ったボタンの合成の離し、フォーカスの通知より前の押下）。
     *     <li>戻った後の最初のtickより前に届いた押下（窓を戻すクリック）。
     *     </ul>
     *     K21では、窓の中のクリックで戻した40回の押下が全て、フォーカスの通知の後・最初のtickより前（0.1〜39.9ミリ秒前）に届いた。
     *     Alt+Tab等で戻った後の普通のクリックは、全て最初のtickより後（87.5ミリ秒以上後）に届いた。そのため、戻った後の普通のクリックは捨てない。
     * </ul>
     * Minecraftの型を使わない（オフライン試験で確かめる）。
     */
    public static final class FocusMark {
        private boolean marked, lostAtLastTick;
        /**
         * 各tickに1回、そのtickの時点のフォーカスで呼ぶ。キーボードのキー（{@code keyboard}）は、フォーカスの無いtickで印を付ける。
         * 最後のtickでフォーカスが無かったかを覚える（次のtickの前に届いた押下を、戻すクリックと見分けるため）。
         */
        public void tick(boolean windowActive, boolean keyboard) {
            if (!windowActive && keyboard) marked = true;
            lostAtLastTick = !windowActive;
        }
        /** GLFWのキー・ボタンのイベント（{@code action}は{@link SkillKeyGate#RELEASE}等）を、届いた順に知らせる。 */
        public void event(int action, boolean windowActive) {
            // フォーカスの無い間のイベント（合成の離し・通知より前の押下）と、戻った後の最初のtickより前に届いた押下・リピート
            // （戻すクリック、押したまま戻ったキーのリピート）は、印にする。本当の離しだけが印を解く。
            if (!windowActive || (lostAtLastTick && action != SkillKeyGate.RELEASE)) marked = true;
            else if (action == SkillKeyGate.RELEASE) marked = false;
        }
        /** 印があるか（本当の離しを見るまで、新しい入力に使わない）。 */
        public boolean marked() { return marked; }
    }
}
