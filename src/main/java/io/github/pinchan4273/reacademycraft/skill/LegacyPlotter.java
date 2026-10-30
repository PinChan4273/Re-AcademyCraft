package io.github.pinchan4273.reacademycraft.skill;

/**
 * 原作cn.academy.util.Plotter: 始点のブロックから方向に沿って1ブロックずつ進む。主軸に沿って進み、他の2軸が半ブロックより
 * ずれたら1段に1軸ずつ横へずれる。ゼロベクトルを拒むことも含め、1行ずつ移植している。
 */
public final class LegacyPlotter {
    private enum Axis { X, Y, Z }
    private final Axis axis;
    private final double dyx, dzx;
    private final int dirflag;
    private final int x0, y0, z0;
    private int x, y, z;

    public LegacyPlotter(int startX, int startY, int startZ, double dx, double dy, double dz) {
        int sx = startX, sy = startY, sz = startZ;
        double adx = Math.abs(dx), ady = Math.abs(dy), adz = Math.abs(dz);
        double t; int it;
        if (adz > ady && adz > adx) {
            t = dz; dz = dx; dx = t; it = sz; sz = sx; sx = it; axis = Axis.Z;
        } else if (ady > adx) {
            t = dy; dy = dx; dx = t; it = sy; sy = sx; sx = it; axis = Axis.Y;
        } else if (adx > 0) {
            axis = Axis.X;
        } else throw new IllegalArgumentException("Zero slope vector");
        x0 = sx; y0 = sy; z0 = sz;
        x = x0; y = y0; z = z0;
        dyx = dy / dx; dzx = dz / dx;
        dirflag = dx > 0 ? 1 : -1;
    }

    public int[] next() {
        int nextX = x + dirflag;
        double valy = y0 + (nextX - x0) * dyx;
        double valz = z0 + (nextX - x0) * dzx;
        if (Math.abs(valy - y) > .5) y += (int) Math.signum(dyx) * dirflag;
        else if (Math.abs(valz - z) > .5) z += (int) Math.signum(dzx) * dirflag;
        else x = nextX;
        return switch (axis) {
            case X -> new int[] { x, y, z };
            case Y -> new int[] { y, x, z };
            case Z -> new int[] { z, y, x };
        };
    }
}
