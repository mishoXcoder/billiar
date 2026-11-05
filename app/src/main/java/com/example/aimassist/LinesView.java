package com.example.aimassist;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

import java.util.List;

/** Full-screen, non-touchable view that draws the table, the shot path and the cue aim line. */
public class LinesView extends View {
    public float cueX, cueY, tgtX, tgtY, x1, y1, x2, y2, r;
    public float[][] pockets = new float[0][];
    public int selPocket;
    public Geometry.Path path;
    public boolean show = true;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int[] loc = new int[2];

    public LinesView(Context c) { super(c); }

    private void circle(Canvas cv, float x, float y, float rad, int color, float w) {
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(w); p.setColor(color);
        cv.drawCircle(x, y, rad, p);
    }

    @Override protected void onDraw(Canvas cv) {
        if (!show) return;
        getLocationOnScreen(loc);
        cv.translate(-loc[0], -loc[1]); // draw in screen coordinates

        // Table outline and pockets
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2f); p.setColor(Color.argb(160, 0, 220, 255));
        cv.drawRect(x1, y1, x2, y2, p);
        p.setColor(Color.argb(110, 0, 220, 255));
        cv.drawRect(x1 + r, y1 + r, x2 - r, y2 - r, p); // limit for ball centres
        for (int i = 0; i < pockets.length; i++)
            circle(cv, pockets[i][0], pockets[i][1], r * 1.2f,
                   i == selPocket ? Color.RED : Color.argb(120, 0, 220, 255), i == selPocket ? 5f : 2f);

        if (path == null) return;
        List<float[]> pts = path.pts;

        // Object ball path (target -> bounces -> pocket)
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(5f); p.setColor(Color.YELLOW);
        for (int i = 0; i + 1 < pts.size(); i++)
            cv.drawLine(pts.get(i)[0], pts.get(i)[1], pts.get(i + 1)[0], pts.get(i + 1)[1], p);
        for (int i = 1; i + 1 < pts.size(); i++) {
            p.setStyle(Paint.Style.FILL); p.setColor(Color.rgb(255, 120, 0));
            cv.drawCircle(pts.get(i)[0], pts.get(i)[1], 9f, p);
        }

        // Ghost ball + cue aim line
        float[] first = pts.get(1);
        float[] g = Geometry.ghost(tgtX, tgtY, first[0], first[1], r);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(4f); p.setColor(Color.WHITE);
        cv.drawLine(cueX, cueY, g[0], g[1], p);
        circle(cv, g[0], g[1], r, Color.WHITE, 3f);
        circle(cv, tgtX, tgtY, r, Color.YELLOW, 3f);
        circle(cv, cueX, cueY, r, Color.WHITE, 3f);
    }
}
