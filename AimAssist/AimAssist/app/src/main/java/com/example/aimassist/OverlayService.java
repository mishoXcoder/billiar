package com.example.aimassist;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

public class OverlayService extends Service {
    // Handles: 0 cue ball, 1 target ball, 2 table top-left, 3 table bottom-right
    private static final int[] COLORS = {Color.WHITE, Color.YELLOW, Color.CYAN, Color.CYAN};
    private static final String[] POCKET_NAMES = {"TL", "TM", "TR", "BL", "BM", "BR"};

    private WindowManager wm;
    private SharedPreferences prefs;
    private LinesView lines;
    private final HandleView[] handles = new HandleView[4];
    private final WindowManager.LayoutParams[] hlp = new WindowManager.LayoutParams[4];
    private final float[] hx = new float[4], hy = new float[4]; // handle centres, screen px
    private LinearLayout panel;
    private WindowManager.LayoutParams panelLp;
    private TextView status;
    private float radius = 26f;
    private int pocket = 0;
    private String pathKey = "";
    private boolean hidden = false;
    private int handleSize;

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public void onCreate() {
        super.onCreate();
        startForeground(1, buildNotification());
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        prefs = getSharedPreferences("aim", MODE_PRIVATE);
        handleSize = (int) (44 * getResources().getDisplayMetrics().density);

        DisplayMetrics dm = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(dm);
        float w = Math.max(dm.widthPixels, dm.heightPixels), h = Math.min(dm.widthPixels, dm.heightPixels);
        float[] defX = {0.35f * w, 0.55f * w, 0.15f * w, 0.85f * w};
        float[] defY = {0.50f * h, 0.45f * h, 0.20f * h, 0.80f * h};
        for (int i = 0; i < 4; i++) {
            hx[i] = prefs.getFloat("hx" + i, defX[i]);
            hy[i] = prefs.getFloat("hy" + i, defY[i]);
        }
        radius = prefs.getFloat("r", radius);
        pocket = prefs.getInt("pocket", 0);
        pathKey = prefs.getString("path", "");

        // Full-screen drawing window: not touchable, so the game keeps receiving every touch.
        lines = new LinesView(this);
        WindowManager.LayoutParams ll = params(-1, -1, true);
        ll.alpha = 0.75f; // Android 12+ blocks touches through overlays with alpha > 0.8
        wm.addView(lines, ll);

        for (int i = 0; i < 4; i++) {
            final int idx = i;
            handles[i] = new HandleView(this, COLORS[i], i == 0 ? "C" : i == 1 ? "T" : "");
            hlp[i] = params(handleSize, handleSize, false);
            hlp[i].x = (int) (hx[i] - handleSize / 2f);
            hlp[i].y = (int) (hy[i] - handleSize / 2f);
            handles[i].setOnTouchListener(dragger(hlp[i], handles[i], () -> {
                hx[idx] = hlp[idx].x + handleSize / 2f;
                hy[idx] = hlp[idx].y + handleSize / 2f;
                refresh();
            }));
            wm.addView(handles[i], hlp[i]);
        }
        buildPanel();
        refresh();
    }

    private WindowManager.LayoutParams params(int w, int h, boolean passThrough) {
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        if (passThrough) flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(w, h,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, flags, PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        if (Build.VERSION.SDK_INT >= 30)
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        else if (Build.VERSION.SDK_INT >= 28)
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        return lp;
    }

    /** Drags a window by the finger delta. */
    private View.OnTouchListener dragger(WindowManager.LayoutParams lp, View v, Runnable moved) {
        final float[] s = new float[4];
        return (view, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    s[0] = e.getRawX(); s[1] = e.getRawY(); s[2] = lp.x; s[3] = lp.y;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    lp.x = (int) (s[2] + e.getRawX() - s[0]);
                    lp.y = (int) (s[3] + e.getRawY() - s[1]);
                    wm.updateViewLayout(v, lp);
                    moved.run();
                    return true;
            }
            return true;
        };
    }

    private Button btn(String text, Runnable r) {
        Button b = new Button(this);
        b.setText(text); b.setAllCaps(false); b.setTextSize(12);
        b.setMinWidth(0); b.setMinimumWidth(0); b.setMinHeight(0); b.setMinimumHeight(0);
        b.setPadding(16, 8, 16, 8);
        b.setOnClickListener(v -> r.run());
        return b;
    }

    private void buildPanel() {
        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(Color.argb(200, 20, 20, 20));
        panel.setPadding(8, 8, 8, 8);

        TextView drag = new TextView(this);
        drag.setText("::: Aim (drag)"); drag.setTextColor(Color.WHITE); drag.setTextSize(12);
        status = new TextView(this);
        status.setTextColor(Color.WHITE); status.setTextSize(11);
        panel.addView(drag); panel.addView(status);

        LinearLayout row1 = new LinearLayout(this), row2 = new LinearLayout(this);
        row1.addView(btn("Pocket", () -> { pocket = (pocket + 1) % 6; pathKey = ""; refresh(); }));
        row1.addView(btn("Path", () -> { cyclePath(); }));
        row2.addView(btn("R-", () -> { radius = Math.max(8, radius - 1); refresh(); }));
        row2.addView(btn("R+", () -> { radius += 1; refresh(); }));
        row2.addView(btn("Hide", () -> {
            hidden = !hidden;
            lines.show = !hidden;
            for (View h : handles) h.setVisibility(hidden ? View.GONE : View.VISIBLE);
            lines.invalidate();
        }));
        row2.addView(btn("X", () -> stopSelf()));
        panel.addView(row1); panel.addView(row2);

        panelLp = params(-2, -2, false);
        panelLp.x = 12; panelLp.y = 120;
        drag.setOnTouchListener(dragger(panelLp, panel, () -> { }));
        wm.addView(panel, panelLp);
    }

    private float[][] pockets(float x1, float y1, float x2, float y2) {
        float mx = (x1 + x2) / 2;
        return new float[][]{{x1, y1}, {mx, y1}, {x2, y1}, {x1, y2}, {mx, y2}, {x2, y2}};
    }

    private List<Geometry.Path> compute() {
        float x1 = Math.min(hx[2], hx[3]), x2 = Math.max(hx[2], hx[3]);
        float y1 = Math.min(hy[2], hy[3]), y2 = Math.max(hy[2], hy[3]);
        float[][] pk = pockets(x1, y1, x2, y2);
        return Geometry.findPaths(hx[1], hy[1], pk[pocket][0], pk[pocket][1], x1, y1, x2, y2, radius, 3);
    }

    private void cyclePath() {
        List<Geometry.Path> ps = compute();
        if (ps.isEmpty()) return;
        int cur = 0;
        for (int i = 0; i < ps.size(); i++) if (ps.get(i).key().equals(pathKey)) cur = i;
        pathKey = ps.get((cur + 1) % ps.size()).key();
        refresh();
    }

    private void refresh() {
        float x1 = Math.min(hx[2], hx[3]), x2 = Math.max(hx[2], hx[3]);
        float y1 = Math.min(hy[2], hy[3]), y2 = Math.max(hy[2], hy[3]);
        List<Geometry.Path> ps = compute();
        Geometry.Path sel = null;
        int idx = 0;
        for (int i = 0; i < ps.size(); i++) if (ps.get(i).key().equals(pathKey)) { sel = ps.get(i); idx = i; }
        if (sel == null && !ps.isEmpty()) { sel = ps.get(0); pathKey = sel.key(); idx = 0; }

        lines.cueX = hx[0]; lines.cueY = hy[0]; lines.tgtX = hx[1]; lines.tgtY = hy[1];
        lines.x1 = x1; lines.y1 = y1; lines.x2 = x2; lines.y2 = y2; lines.r = radius;
        lines.pockets = pockets(x1, y1, x2, y2); lines.selPocket = pocket; lines.path = sel;
        lines.invalidate();
        status.setText("Pocket " + POCKET_NAMES[pocket] + "  R=" + (int) radius + "\n"
                + (sel == null ? "no path" : (idx + 1) + "/" + ps.size() + " " + sel.label()));

        SharedPreferences.Editor ed = prefs.edit();
        for (int i = 0; i < 4; i++) ed.putFloat("hx" + i, hx[i]).putFloat("hy" + i, hy[i]);
        ed.putFloat("r", radius).putInt("pocket", pocket).putString("path", pathKey).apply();
    }

    private Notification buildNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel("aim", "Aim Assist", NotificationManager.IMPORTANCE_LOW));
        return new Notification.Builder(this, "aim")
                .setContentTitle("Aim Assist running")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .build();
    }

    @Override public void onDestroy() {
        try { wm.removeView(lines); } catch (Exception ignored) { }
        for (View h : handles) try { wm.removeView(h); } catch (Exception ignored) { }
        try { wm.removeView(panel); } catch (Exception ignored) { }
        super.onDestroy();
    }

    /** Small draggable crosshair dot. */
    static class HandleView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int color; private final String label;
        HandleView(Context c, int color, String label) { super(c); this.color = color; this.label = label; }
        @Override protected void onDraw(Canvas cv) {
            float cx = getWidth() / 2f, cy = getHeight() / 2f, r = cx - 3;
            p.setStyle(Paint.Style.FILL); p.setColor(Color.argb(70, Color.red(color), Color.green(color), Color.blue(color)));
            cv.drawCircle(cx, cy, r, p);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(3f); p.setColor(color);
            cv.drawCircle(cx, cy, r, p);
            cv.drawLine(cx - r, cy, cx + r, cy, p); cv.drawLine(cx, cy - r, cx, cy + r, p);
            if (!label.isEmpty()) { p.setStyle(Paint.Style.FILL); p.setTextSize(r * 0.8f); cv.drawText(label, cx + r * 0.2f, cy - r * 0.2f, p); }
        }
    }
}
