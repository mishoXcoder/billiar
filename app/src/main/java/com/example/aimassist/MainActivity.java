package com.example.aimassist;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(48, 48, 48, 48);

        TextView t = new TextView(this);
        t.setTextSize(16);
        t.setText("Aim Assist (offline practice only)\n\n"
                + "1. Tap Start, then open the game in landscape.\n"
                + "2. Drag the cyan handles onto the table's two opposite cushion corners (top-left / bottom-right).\n"
                + "3. Drag the white handle onto the cue ball and the yellow one onto the target ball.\n"
                + "4. Use the panel: Pocket / Path pick the shot, R- / R+ match the ball size.\n");
        root.addView(t);

        Button start = new Button(this); start.setText("Start overlay");
        Button stop = new Button(this); stop.setText("Stop overlay");
        root.addView(start); root.addView(stop);
        setContentView(root);

        start.setOnClickListener(v -> {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Allow \"Display over other apps\", then tap Start again", Toast.LENGTH_LONG).show();
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
                return;
            }
            if (Build.VERSION.SDK_INT >= 33)
                requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
            startForegroundService(new Intent(this, OverlayService.class));
            moveTaskToBack(true);
        });
        stop.setOnClickListener(v -> stopService(new Intent(this, OverlayService.class)));
    }
}
