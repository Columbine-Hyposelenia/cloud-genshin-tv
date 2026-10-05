package com.cloudgenshin.tv;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ScrollView;
import android.widget.TextView;

public final class ReportScreen {
    private ReportScreen() {
    }

    public static void show(Context context, String body) {
        final Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        ScrollView scrollView = new ScrollView(context);
        scrollView.setFocusable(true);
        scrollView.setFocusableInTouchMode(true);
        scrollView.setBackgroundColor(0xF0101826);
        int pad = dp(context, 24);
        scrollView.setPadding(pad, pad, pad, pad);

        TextView text = new TextView(context);
        text.setTextColor(Color.parseColor("#EAF0FB"));
        text.setTextSize(11);
        text.setTypeface(Typeface.MONOSPACE);
        text.setText("(dpad up/down to scroll, back to close)\n\n" + body);
        scrollView.addView(text);

        dialog.setContentView(scrollView);
        Window window = dialog.getWindow();
        window.setBackgroundDrawableResource(android.R.color.black);
        WindowManager.LayoutParams params = window.getAttributes();
        params.width = WindowManager.LayoutParams.MATCH_PARENT;
        params.height = WindowManager.LayoutParams.MATCH_PARENT;
        window.setAttributes(params);

        dialog.setOnKeyListener(new android.content.DialogInterface.OnKeyListener() {
            @Override
            public boolean onKey(android.content.DialogInterface d, int keyCode, KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_MENU
                        || keyCode == KeyEvent.KEYCODE_ESCAPE) {
                    if (event.getAction() == KeyEvent.ACTION_UP) {
                        dialog.dismiss();
                    }
                    return true;
                }
                return false;
            }
        });

        dialog.show();
        scrollView.requestFocus(View.FOCUS_DOWN);
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
