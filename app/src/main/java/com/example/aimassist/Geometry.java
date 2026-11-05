package com.example.aimassist;

import java.util.ArrayList;
import java.util.List;

/** Mirror (unfolding) method for direct and multi-cushion shots. */
public class Geometry {
    public static final int L = 0, R = 1, T = 2, B = 3;
    static final float MIN_HOP = 40f; // px, roughly one ball diameter
    static final String[] NAMES = {"L", "R", "T", "B"};

    public static class Path {
        public final int[] rails;
        /** [0]=object ball, then bounce points, last=pocket. Each is {x,y}. */
        public final List<float[]> pts = new ArrayList<>();
        public Path(int[] rails) { this.rails = rails; }
        public String key() {
            StringBuilder sb = new StringBuilder();
            for (int r : rails) sb.append(NAMES[r]);
            return sb.toString();
        }
        public String label() {
            return rails.length == 0 ? "Direct" : rails.length + " rail" + (rails.length > 1 ? "s " : " ") + key();
        }
    }

    /**
     * (x1,y1)-(x2,y2) is the cushion rectangle; ball centres bounce on it shrunk by r.
     * Returns all valid paths from the ball to the pocket with up to maxBounces cushions.
     */
    public static List<Path> findPaths(float bx, float by, float px, float py,
                                       float x1, float y1, float x2, float y2,
                                       float r, int maxBounces) {
        float[] c = {x1 + r, x2 - r, y1 + r, y2 - r}; // L,R,T,B rail positions for ball centre
        List<Path> out = new ArrayList<>();
        List<int[]> seqs = new ArrayList<>();
        build(new int[0], maxBounces, seqs);
        for (int[] s : seqs) {
            Path p = trace(bx, by, px, py, c, s);
            if (p != null) out.add(p);
        }
        return out;
    }

    private static void build(int[] cur, int max, List<int[]> out) {
        out.add(cur);
        if (cur.length == max) return;
        for (int r = 0; r < 4; r++) {
            if (cur.length > 0 && cur[cur.length - 1] == r) continue;
            int[] n = java.util.Arrays.copyOf(cur, cur.length + 1);
            n[cur.length] = r;
            build(n, max, out);
        }
    }

    private static Path trace(float bx, float by, float px, float py, float[] c, int[] seq) {
        Path path = new Path(seq);
        path.pts.add(new float[]{bx, by});
        // Unfold the pocket across the rails, last rail first.
        float tx = px, ty = py;
        for (int i = seq.length - 1; i >= 0; i--) {
            if (seq[i] <= R) tx = 2 * c[seq[i]] - tx; else ty = 2 * c[seq[i]] - ty;
        }
        float dx = tx - bx, dy = ty - by;
        if (Math.abs(dx) + Math.abs(dy) < 1e-3f) return null;
        float cx = bx, cy = by;
        final float eps = 0.5f;
        for (int rail : seq) {
            float t;
            if (rail <= R) { if (Math.abs(dx) < 1e-6f) return null; t = (c[rail] - cx) / dx; }
            else           { if (Math.abs(dy) < 1e-6f) return null; t = (c[rail] - cy) / dy; }
            if (t <= 1e-6f) return null;
            float hx = cx + dx * t, hy = cy + dy * t;
            if (rail <= R) { if (hy < c[T] - eps || hy > c[B] + eps) return null; dx = -dx; }
            else           { if (hx < c[L] - eps || hx > c[R] + eps) return null; dy = -dy; }
            // Skip impractical corner hops (bounce-to-bounce shorter than a ball).
            if (path.pts.size() > 1 && Math.hypot(hx - cx, hy - cy) < MIN_HOP) return null;
            cx = hx; cy = hy;
            path.pts.add(new float[]{cx, cy});
        }
        // After the last bounce the ray must head to the real pocket.
        float vx = px - cx, vy = py - cy;
        if (vx * dx + vy * dy <= 0) return null;
        path.pts.add(new float[]{px, py});
        return path;
    }

    /** Point where the cue ball must be at contact, behind the object ball. */
    public static float[] ghost(float tx, float ty, float nx, float ny, float r) {
        float len = (float) Math.hypot(nx - tx, ny - ty);
        if (len < 1e-3f) return new float[]{tx, ty};
        return new float[]{tx - (nx - tx) / len * 2 * r, ty - (ny - ty) / len * 2 * r};
    }
}
