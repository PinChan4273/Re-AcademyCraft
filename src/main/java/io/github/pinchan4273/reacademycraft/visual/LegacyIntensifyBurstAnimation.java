package io.github.pinchan4273.reacademycraft.visual;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.random.RandomGenerator;

/**
 * EntityIntensifyEffect/SubArcのJavaだけの版（WeAthFolD、AcademyCraft 7b1401c）。
 * EntityX形式の期限のコールバックは、エンティティの年齢が飛んでも、1回の電弧の更新の前に実行する。
 * 受け付けた完了の寿命と描画は、クライアントのアダプタが別に渡す。
 */
public final class LegacyIntensifyBurstAnimation {
    public static final int LIFE = 15, MAX_ARCS = 21, TEMPLATES = 10;
    private static final int[] AT = {0, 1, 3, 4, 6, 7, 8};
    private static final double[] HEIGHT = {2, 1.8, 1.5, 1, .5, 0, -.1};
    public record ArcFrame(double x, double y, double z, int template, double rotX, double rotY, double rotZ,
                           int lifetime, boolean visible, boolean dead) { }
    private static final class Arc {
        final double x, y, z, rotX, rotY, rotZ;
        int template, lifetime;
        boolean visible, dead;
        Arc(double x, double y, double z, RandomGenerator random) {
            this.x=x;this.y=y;this.z=z;template=random.nextInt(TEMPLATES);
            rotX=random.nextDouble()*360;rotY=random.nextDouble()*360;rotZ=random.nextDouble()*360;
        }
        void tick(RandomGenerator random) {
            if(random.nextDouble()<.5*.6)template=random.nextInt(TEMPLATES);
            if(random.nextDouble()<.9)lifetime++;
            if(lifetime==3)dead=true;
            if(visible){if(random.nextDouble()<.4*.7)visible=false;}
            else if(random.nextDouble()<.3*.7)visible=true;
        }
        ArcFrame frame(){return new ArcFrame(x,y,z,template,rotX,rotY,rotZ,lifetime,visible,dead);}
    }
    private final List<Arc> arcs=new ArrayList<>(MAX_ARCS);
    private int lastAge=-1,nextStage,spawned;
    private boolean finished;

    /** 見た目のエンティティの絶対年齢。同じ年齢の繰り返しは何もせず、逆行する年齢は拒否する。 */
    public void update(int age,RandomGenerator random){
        Objects.requireNonNull(random);
        if(age<0||age<lastAge)throw new IllegalArgumentException("Burst age cannot reverse");
        if(finished||age==lastAge)return;
        lastAge=age;
        // 原作は15との等値を判定する。>=にすることで、最後の年齢が飛ばされたときに効果が残り続けるのを防ぐ。
        if(age>=LIFE){clear();return;}
        while(nextStage<AT.length&&AT[nextStage]<=age){
            double height=HEIGHT[nextStage++];
            int count=3+random.nextInt(1); // 原作rangei(3,4)。上限は含まない。
            for(int i=0;i<count;i++){
                double radius=.5+random.nextDouble()*.1,theta=random.nextDouble()*Math.PI*2;
                arcs.add(new Arc(radius*Math.sin(theta),height,radius*Math.cos(theta),random));spawned++;
            }
        }
        var iterator=arcs.iterator();
        while(iterator.hasNext()){var arc=iterator.next();if(arc.dead)iterator.remove();else arc.tick(random);}
    }
    /** 新しく死んだ電弧は次のtickまで一覧に残るが、描画処理はそれを飛ばさなければならない。 */
    public List<ArcFrame> snapshot(){return arcs.stream().map(Arc::frame).toList();}
    public boolean finished(){return finished;}
    public int spawnedArcs(){return spawned;}
    public void clear(){finished=true;arcs.clear();}
}
