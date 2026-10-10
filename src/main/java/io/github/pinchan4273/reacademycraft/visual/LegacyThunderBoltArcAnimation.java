package io.github.pinchan4273.reacademycraft.visual;

import java.util.Objects;
import java.util.random.RandomGenerator;

/** 原作Thunder BoltのEntityArc 1つのJavaだけの状態。ワールド・ネットワーク・描画処理には結び付かない。 */
public final class LegacyThunderBoltArcAnimation {
    public static final int TEMPLATES=20,MAIN_LIFE=20,AOE_MIN_LIFE=15,AOE_MAX_LIFE=24;
    public record Frame(int age,int life,int template,boolean visible,boolean finished) { }
    private final int life;
    private int age,template;
    private boolean visible=true,finished;
    private LegacyThunderBoltArcAnimation(int life){this.life=life;}
    public static LegacyThunderBoltArcAnimation mainArc(){return new LegacyThunderBoltArcAnimation(MAIN_LIFE);}
    public static LegacyThunderBoltArcAnimation aoeArc(RandomGenerator random){
        Objects.requireNonNull(random);return new LegacyThunderBoltArcAnimation(AOE_MIN_LIFE+random.nextInt(AOE_MAX_LIFE-AOE_MIN_LIFE+1));
    }
    public void tick(RandomGenerator random){
        Objects.requireNonNull(random);if(finished)return;
        if(++age>=life){finished=true;return;}
        if(random.nextDouble()<.5)template=random.nextInt(TEMPLATES);
        if(visible){if(random.nextDouble()<.2)visible=false;}
        else if(random.nextDouble()<.2)visible=true;
    }
    public Frame snapshot(){return new Frame(age,life,template,visible,finished);}
    public void clear(){finished=true;visible=false;}
}
