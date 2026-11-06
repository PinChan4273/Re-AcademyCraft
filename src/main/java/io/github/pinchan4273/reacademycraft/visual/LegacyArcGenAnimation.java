package io.github.pinchan4273.reacademycraft.visual;

import java.util.Objects;
import java.util.random.RandomGenerator;

/** 原作Arc GenのJavaだけのEntityArcの状態: weakArc、Life(10)。ワールドや描画には結び付かない。 */
public final class LegacyArcGenAnimation {
    public static final int LIFE=10,TEMPLATES=20;
    public record Frame(int age,int template,boolean visible,boolean finished) { }
    private int age,template;
    private boolean visible=true,finished;
    public void tick(RandomGenerator random){
        Objects.requireNonNull(random);
        if(finished)return;
        if(++age>=LIFE){finished=true;return;}
        if(random.nextDouble()<.7)template=random.nextInt(TEMPLATES);
        if(visible){if(random.nextDouble()<.1)visible=false;}
        else if(random.nextDouble()<.4)visible=true;
    }
    public Frame snapshot(){return new Frame(age,template,visible,finished);}
    public void clear(){finished=true;visible=false;}
}
