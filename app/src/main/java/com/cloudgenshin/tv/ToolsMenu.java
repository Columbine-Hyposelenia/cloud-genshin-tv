package com.cloudgenshin.tv;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class ToolsMenu {
    public interface Host {
        void reloadPage();

        void clearCacheAndReload();

        void showReport();

        String toggleLogServerLabel();

        String toggleLogServer();

        String toggleVerbose();

        void clearLogs();

        void restartEngine();

        void toggleReveal();

        void toggleGameMode();

        String gameModeLabel();

        void exitApp();
    }

    private static final int BG = 0xF0101826;
    private static final int ROW_BG = 0xFF1B2740;
    private static final int ROW_FOCUS = 0xFF3D63B0;
    private static final int TEXT = 0xFFEAF0FB;
    private static final int HEADER = 0xFF8FB0E6;

    private final Context mContext;
    private final Host mHost;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private Dialog mDialog;

    public ToolsMenu(Context context, Host host) {
        mContext = context;
        mHost = host;
    }

    public boolean isShowing() {
        return mDialog != null && mDialog.isShowing();
    }

    public void show() {
        if (isShowing()) {
            return;
        }
        mDialog = new Dialog(mContext);
        mDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout container = new LinearLayout(mContext);
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        container.setPadding(pad, pad, pad, pad);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(BG);
        bg.setCornerRadius(dp(10));
        container.setBackground(bg);

        addTitle(container, "工具");

        addHeader(container, "画面（游戏中即时生效）");
        addCycle(container, DisplayFix.revealLabel(), new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mHost.toggleReveal();
                ((TextView) view).setText(DisplayFix.revealLabel());
            }
        });
        addCycle(container, mHost.gameModeLabel(), new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mHost.toggleGameMode();
                ((TextView) view).setText(mHost.gameModeLabel());
            }
        });

        addHeader(container, "诊断与日志");
        addAction(container, "诊断报告", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dismiss();
                mHost.showReport();
            }
        });
        addCycle(container, mHost.toggleLogServerLabel(), new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ((TextView) view).setText(mHost.toggleLogServer());
            }
        });
        addCycle(container, verboseLabel(), new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ((TextView) view).setText(mHost.toggleVerbose());
                scheduleRestart();
            }
        });
        addAction(container, "清空日志", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mHost.clearLogs();
            }
        });

        addHeader(container, "操作");
        addAction(container, "重新加载页面", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dismiss();
                mHost.reloadPage();
            }
        });
        addAction(container, "清理缓存并重新加载", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dismiss();
                mHost.clearCacheAndReload();
            }
        });
        addAction(container, "重启应用", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dismiss();
                mHost.restartEngine();
            }
        });
        addAction(container, "退出应用", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dismiss();
                mHost.exitApp();
            }
        });

        ScrollView scrollView = new ScrollView(mContext);
        scrollView.addView(container);
        mDialog.setContentView(scrollView);
        Window window = mDialog.getWindow();
        window.setBackgroundDrawableResource(android.R.color.transparent);
        WindowManager.LayoutParams params = window.getAttributes();
        params.width = dp(460);
        params.height = (int) (mContext.getResources().getDisplayMetrics().heightPixels * 0.88f);
        params.gravity = Gravity.CENTER;
        window.setAttributes(params);

        mDialog.setOnKeyListener(new android.content.DialogInterface.OnKeyListener() {
            @Override
            public boolean onKey(android.content.DialogInterface dialog, int keyCode,
                    KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_MENU
                        || keyCode == KeyEvent.KEYCODE_ESCAPE) {
                    if (event.getAction() == KeyEvent.ACTION_UP) {
                        dismiss();
                    }
                    return true;
                }
                return false;
            }
        });

        mDialog.show();
        View focused = container.getChildAt(2);
        if (focused != null) {
            focused.requestFocus();
        }
    }

    private void scheduleRestart() {
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                dismiss();
                mHost.restartEngine();
            }
        }, 650);
    }

    public void dismiss() {
        if (mDialog != null && mDialog.isShowing()) {
            mDialog.dismiss();
        }
    }

    private String verboseLabel() {
        return "详细日志：" + (Diag.isVerbose(mContext) ? "开启" : "关闭");
    }

    private void addTitle(LinearLayout container, String text) {
        LinearLayout row = new LinearLayout(mContext);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.BOTTOM);
        row.setPadding(dp(8), 0, dp(8), dp(12));

        TextView title = baseText(text, 20, TEXT);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        row.addView(title);

        String version = versionLabel();
        if (version.length() > 0) {
            TextView badge = baseText(version, 11, HEADER);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            params.leftMargin = dp(6);
            params.bottomMargin = dp(1);
            badge.setLayoutParams(params);
            row.addView(badge);
        }
        container.addView(row);
    }

    private String versionLabel() {
        try {
            String name = mContext.getPackageManager()
                    .getPackageInfo(mContext.getPackageName(), 0).versionName;
            if (name == null || name.length() == 0) {
                return "";
            }
            return "v" + name;
        } catch (Throwable throwable) {
            return "";
        }
    }

    private void addHeader(LinearLayout container, String text) {
        TextView header = baseText(text, 13, HEADER);
        header.setPadding(dp(8), dp(6), dp(8), dp(6));
        container.addView(header);
    }

    private void addCycle(LinearLayout container, String label, View.OnClickListener listener) {
        TextView row = baseRow();
        row.setText(label);
        attachFocusStyle(row);
        row.setOnClickListener(listener);
        container.addView(row);
    }

    private void addAction(LinearLayout container, String label, View.OnClickListener listener) {
        TextView row = baseRow();
        row.setText(label);
        attachFocusStyle(row);
        row.setOnClickListener(listener);
        container.addView(row);
    }

    private TextView baseText(String text, int size, int color) {
        TextView view = new TextView(mContext);
        view.setText(text);
        view.setTextColor(color);
        view.setTextSize(size);
        return view;
    }

    private TextView baseRow() {
        TextView row = new TextView(mContext);
        row.setTextColor(TEXT);
        row.setTextSize(16);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(13), dp(14), dp(13));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(10);
        row.setLayoutParams(params);
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(ROW_BG);
        drawable.setCornerRadius(dp(8));
        row.setBackground(drawable);
        row.setFocusable(true);
        row.setClickable(true);
        return row;
    }

    private void attachFocusStyle(final TextView row) {
        row.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean focused) {
                GradientDrawable drawable = new GradientDrawable();
                drawable.setColor(focused ? ROW_FOCUS : ROW_BG);
                drawable.setCornerRadius(dp(8));
                row.setBackground(drawable);
                row.setTextColor(focused ? Color.WHITE : TEXT);
            }
        });
    }

    private int dp(int value) {
        return Math.round(value * mContext.getResources().getDisplayMetrics().density);
    }
}
