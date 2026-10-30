package io.github.pinchan4273.reacademycraft.visual;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.random.RandomGenerator;
import net.minecraft.world.phys.Vec3;

/**
 * WeAthFolDのEntitySurroundArc/SubArc/処理の流れとCubePointFactory（AcademyCraft 7b1401c）の、範囲を限ったCPU版。
 * エンティティ・ネットワーク・GPUの寿命は持たない。
 * 受け付けたクライアントtickごとに1回呼び、描画は不変のスナップショットを読むだけ。
 */
public final class LegacySurroundAnimation {
    public static final int TEMPLATES = 10, LIFE = 30;
    public enum Kind {
        THIN(4), NORMAL(6), BOLD(5);
        private final int count;
        Kind(int count) { this.count = count; }
        public int count() { return count; }
    }
    public record Frame(Vec3 position, int template, double rotX, double rotY, double rotZ,
                        int lifetime, boolean visible, boolean dead) { }

    private static final class Arc {
        final Vec3 position;
        final double rotX, rotY, rotZ;
        int template, lifetime;
        boolean visible, dead;
        Arc(Vec3 position, RandomGenerator random) {
            this.position = position;
            template = random.nextInt(TEMPLATES);
            rotX = random.nextDouble() * 360; rotY = random.nextDouble() * 360; rotZ = random.nextDouble() * 360;
        }
        void tick(RandomGenerator random) {
            if (random.nextDouble() < .5 * .6) template = random.nextInt(TEMPLATES);
            if (random.nextDouble() < .9) lifetime++;
            if (lifetime == LIFE) dead = true;
            if (visible) {
                if (random.nextDouble() < .4 * .7) visible = false;
            } else if (random.nextDouble() < .3 * .7) visible = true;
        }
        Frame frame() { return new Frame(position, template, rotX, rotY, rotZ, lifetime, visible, dead); }
    }
    private final Kind kind;
    private final Vec3 size;
    private final List<Arc> arcs = new ArrayList<>(6);

    public LegacySurroundAnimation(Kind kind, Vec3 size) {
        this.kind = Objects.requireNonNull(kind);
        validateSize(size); this.size = size;
    }

    public void tick(RandomGenerator random) {
        Objects.requireNonNull(random);
        // 原作の生成は、前のまとまりが空のときにだけまとめて行い、tickごとに数を補充するのではない。際限なく増える生存リストは溜まらない。
        if (arcs.isEmpty()) for (int i = 0; i < kind.count; i++)
            arcs.add(new Arc(cubePoint(size, true, random), random));
        var iterator = arcs.iterator();
        while (iterator.hasNext()) {
            var arc = iterator.next();
            if (arc.dead) iterator.remove(); else arc.tick(random);
        }
    }
    /** 原作の処理と同じく、新しく死んだ項目を次の更新まで含む。 */
    public List<Frame> snapshot() { return arcs.stream().map(Arc::frame).toList(); }
    /** このまとまりを消す。所有するセッションは最終停止でアニメーションを破棄しなければならない。 */
    public void clear() { arcs.clear(); }

    /** 面の選択は面積で重み付けせず一様。原作の中央寄せはX/Zだけをずらす。 */
    public static Vec3 cubePoint(Vec3 size, boolean centered, RandomGenerator random) {
        validateSize(size); Objects.requireNonNull(random);
        int face = random.nextInt(6);
        double a = random.nextDouble(), b = random.nextDouble();
        double xOffset = centered ? -size.x * .5 : 0, zOffset = centered ? -size.z * .5 : 0;
        return switch (face) {
            case 0, 1 -> new Vec3(a * size.x + xOffset, face == 0 ? 0 : size.y, b * size.z + zOffset);
            case 2, 3 -> new Vec3(b * size.x + xOffset, a * size.y, (face == 2 ? 0 : size.z) + zOffset);
            case 4, 5 -> new Vec3((face == 4 ? 0 : size.x) + xOffset, a * size.y, b * size.z + zOffset);
            default -> throw new IllegalArgumentException("Random face outside bounds");
        };
    }
    private static void validateSize(Vec3 size) {
        Objects.requireNonNull(size);
        if (!Double.isFinite(size.x) || !Double.isFinite(size.y) || !Double.isFinite(size.z)
                || size.x < 0 || size.y < 0 || size.z < 0 || size.x > 32 || size.y > 32 || size.z > 32)
            throw new IllegalArgumentException("Surround dimensions must be finite within 0..32");
    }
}
