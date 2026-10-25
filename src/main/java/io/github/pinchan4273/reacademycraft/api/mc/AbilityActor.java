package io.github.pinchan4273.reacademycraft.api.mc;

import io.github.pinchan4273.reacademycraft.api.ActorKind;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * 発動の主体（公開API、実験的。Minecraftの型を使う層で、Minecraftの版に結びつく）。値はその時点のもので、呼ぶたびに読み直す。
 * 1.4.0のS4では、自然能力者（プレイヤー本人）だけ。
 */
public interface AbilityActor {
    /** 主体の識別子（自然能力者ならプレイヤーのUUID）。 */
    UUID id();
    ActorKind kind();
    ServerLevel level();
    /** 効果の起点（自然能力者なら目の位置）。 */
    Vec3 position();
    /** 向き（長さ1）。 */
    Vec3 look();
    /** 主体の実体（自然能力者ならそのプレイヤー。装置などで実体が無ければnull）。 */
    @Nullable Entity entity();
    /**
     * 攻撃・地形の変更の帰属先のプレイヤー（自然能力者なら本人。装備なら着けた人、機械なら持ち主など。無ければnull）。
     * Re:AcademyCraftは、これが今ログインしている本物のプレイヤーのときだけ確かめられたとする（{@code AcademySafety.verifiedPlayer}）。
     */
    @Nullable ServerPlayer attributedPlayer();
    /** 主体がまだ使えるか（実体が消えた・装置が壊れた・取り外したらfalse。1.4.0のS5）。使えなくなると、そのセッションを止める。 */
    default boolean valid() { return true; }
}
