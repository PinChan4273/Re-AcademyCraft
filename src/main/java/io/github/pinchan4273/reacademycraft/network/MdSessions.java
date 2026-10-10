package io.github.pinchan4273.reacademycraft.network;

/**
 * すべてのmdのオーブとビームのセッション（Electron Bomb、Scatter Bomb、Electron Missile、Meltdowner、Railgun）で共通の番号。
 * クライアントはオーブと、それを退かせるビームを、術者とセッション番号で引く。技能ごとに別々に数えると、ある技能のビームが
 * 同じ術者の別の技能のオーブを退かせたり、ある技能の更新が別の技能のオーブを延長したりした。サーバースレッド専用。
 */
public final class MdSessions {
    private static long last;
    private MdSessions() { }
    public static long next() { return last = Math.incrementExact(last); }
}
