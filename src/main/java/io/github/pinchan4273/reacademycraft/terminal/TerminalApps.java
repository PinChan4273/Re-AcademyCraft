package io.github.pinchan4273.reacademycraft.terminal;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/** 原作AppRegistry: 端末が知るすべてのアプリ。登録された順。 */
public final class TerminalApps {
    private static final Map<String, TerminalApp> APPS = new LinkedHashMap<>();
    // 原作RegAppImplは優先度の高い順に登録する: 0のままのアプリ（その間の順はクラスの走査順）、次にSettings（-1）、最後にAbout（-2）。
    /** 原作AppSkillTree。アプリアイテムでインストールする。 */
    public static final TerminalApp SKILL_TREE = register(new TerminalApp("skill_tree", false, TerminalApp.Kind.SKILL_TREE));
    /** 原作MediaApp。アプリアイテムでインストールする。 */
    public static final TerminalApp MEDIA_PLAYER = register(new TerminalApp("media_player", false, TerminalApp.Kind.MEDIA_PLAYER));
    /** 原作AppFreqTransmitter。そのアプリアイテムでインストールする。 */
    public static final TerminalApp FREQ_TRANSMITTER = register(new TerminalApp("freq_transmitter", false, TerminalApp.Kind.FREQ_TRANSMITTER));
    /** 原作AppTutorial。原作でプリインストール。 */
    public static final TerminalApp TUTORIAL = register(new TerminalApp("tutorial", true, TerminalApp.Kind.TUTORIAL));
    /** 原作AppSettings、@RegApp(priority = -1)。原作でプリインストール。 */
    public static final TerminalApp SETTINGS = register(new TerminalApp("settings", true, TerminalApp.Kind.SETTINGS));
    /** 原作AppAbout、@RegApp(priority = -2)。原作でプリインストール。 */
    public static final TerminalApp ABOUT = register(new TerminalApp("about", true, TerminalApp.Kind.ABOUT));
    private TerminalApps() { }
    public static TerminalApp register(TerminalApp app) { APPS.put(app.name(), app); return app; }
    @Nullable public static TerminalApp byName(String name) { return APPS.get(name); }
    public static List<TerminalApp> all() { return List.copyOf(APPS.values()); }
}
