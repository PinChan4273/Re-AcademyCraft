package io.github.pinchan4273.reacademycraft.event;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.Event;

/** 原作の残りのイベント（cn.academy.event）。原作と同じく、事が起きた後にサーバーでMinecraftForge.EVENT_BUSへ送る。 */
public abstract class AcademyEvent extends Event {
    private final Player player;
    protected AcademyEvent(Player player) { this.player = player; }
    public Player player() { return player; }

    /** 原作CoinThrowEvent: コインを投げた（既にワールドにある）。 */
    public static final class CoinThrow extends AcademyEvent {
        private final Entity coin;
        public CoinThrow(Player player, Entity coin) { super(player); this.coin = coin; }
        public Entity coin() { return coin; }
    }
    /** 原作TerminalInstalledEvent: プレイヤーのデータ端末をインストールした。 */
    public static final class TerminalInstalled extends AcademyEvent { public TerminalInstalled(Player player) { super(player); } }
    /** 原作AppInstalledEvent: プレイヤーの端末にアプリをインストールした（名前で示す）。 */
    public static final class AppInstalled extends AcademyEvent {
        private final String app;
        public AppInstalled(Player player, String app) { super(player); this.app = app; }
        public String app() { return app; }
    }
    /** 原作TutorialActivatedEvent: ミサカクラウドの記事がプレイヤーに解放された（idで示す）。 */
    public static final class TutorialActivated extends AcademyEvent {
        private final String tutorial;
        public TutorialActivated(Player player, String tutorial) { super(player); this.tutorial = tutorial; }
        public String tutorial() { return tutorial; }
    }
}
