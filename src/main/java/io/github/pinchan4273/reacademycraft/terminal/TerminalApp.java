package io.github.pinchan4273.reacademycraft.terminal;

/**
 * 原作App: データ端末の1つのプログラム。名前で識別し、プレイヤーのインストール済みの一覧はその名前を持つ。
 * そのうち1つ（原作のAbout）は最初からある。
 *
 * 原作は各アプリに、開くとクライアントで動くAppEnvironmentを渡す。ここではアプリは移植版が開き方を知る少数のもののうちどれかを示し、
 * 開くのはクライアントが行うので、サーバーがクライアントのクラスを名指すことはない。
 */
public record TerminalApp(String name, boolean preInstalled, Kind kind) {
    /** このアプリを開いたときの動作。 */
    public enum Kind {
        /** 原作AppAbout: AboutUI、クレジット、寄付のページ。 */
        ABOUT,
        /** 原作AppSkillTree: 背後に開発機の無い技能の木なので、何も習得できない。 */
        SKILL_TREE,
        /** 原作AppSettings: シングルプレイのワールドのホストに原作が設定させるスイッチ。 */
        SETTINGS,
        /** 原作MediaApp: メディアプレイヤー。クライアントの独自の画面。 */
        MEDIA_PLAYER,
        /** 原作AppTutorial: ミサカクラウド。プリインストール。 */
        TUTORIAL,
        /** 原作AppFreqTransmitter: nodeと機械を繋ぐFreqTransmitterUI。 */
        FREQ_TRANSMITTER
    }
    public TerminalApp {
        if (name == null || name.isEmpty() || name.length() > 32) throw new IllegalArgumentException("Invalid app name");
        if (kind == null) throw new IllegalArgumentException("Invalid app kind");
    }
    public String titleKey() { return "academy.app." + name + ".name"; }
}
