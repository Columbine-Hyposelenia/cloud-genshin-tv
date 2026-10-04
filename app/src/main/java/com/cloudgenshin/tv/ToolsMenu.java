package com.cloudgenshin.tv;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

public class ToolsMenu {
    public interface Host {
        void reloadPage();

        void clearCacheAndReload();

        void showDiagnostics();

        void exitApp();
    }

    private static final int BG = 0xF0101826;
    private static final int ROW_BG = 0xFF1B2740;
    private static final int ROW_FOCUS = 0xFF3D63B0;
    private static final int TEXT = 0xFFEAF0FB;

    private final Context mContext;
    private final Host mHost;
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

        addTitle(container);
        addToggle(container, outputLabel(), new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int next = DecoderMode.isByteBuffer()
                        ? DecoderMode.OUTPUT_TEXTURE
                        : DecoderMode.OUTPUT_BYTEBUFFER;
                DecoderMode.setOutput(mContext, next);
                ((TextView) view).setText(outputLabel());
            }
        });
        addToggle(container, hardwareLabel(), new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                DecoderMode.setHardwareDecode(mContext, !DecoderMode.hardwareDecode());
                ((TextView) view).setText(hardwareLabel());
            }
        });
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
        addAction(container, "故障诊断信息", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dismiss();
                mHost.showDiagnostics();
            }
        });
        addAction(container, "退出应用", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dismiss();
                mHost.exitApp();
            }
        });

        mDialog.setContentView(container);
        Window window = mDialog.getWindow();
        window.setBackgroundDrawableResource(android.R.color.transparent);
        WindowManager.LayoutParams params = window.getAttributes();
        params.width = dp(420);
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
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
        View first = container.getChildAt(1);
        if (first != null) {
            first.requestFocus();
        }
    }

    public void dismiss() {
        if (mDialog != null && mDialog.isShowing()) {
            mDialog.dismiss();
        }
    }

    private String outputLabel() {
        if (DecoderMode.isByteBuffer()) {
            return "解码输出：字节缓冲（推荐）";
        }
        return "解码输出：纹理";
    }

    private String hardwareLabel() {
        if (DecoderMode.hardwareDecode()) {
            return "硬件解码：开启";
        }
        return "硬件解码：关闭（软件解码）";
    }

    private void addTitle(LinearLayout container) {
        TextView title = new TextView(mContext);
        title.setText("工具");
        title.setTextColor(TEXT);
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(dp(8), 0, dp(8), dp(14));
        container.addView(title);
    }

    private void addToggle(LinearLayout container, String label, View.OnClickListener listener) {
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

    private TextView baseRow() {
        TextView row = new TextView(mContext);
        row.setTextColor(TEXT);
        row.setTextSize(16);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int vertical = dp(13);
        int horizontal = dp(14);
        row.setPadding(horizontal, vertical, horizontal, vertical);
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
