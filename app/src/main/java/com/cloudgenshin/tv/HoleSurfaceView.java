package com.cloudgenshin.tv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

/**
 * Full-screen transparent SurfaceView used to punch a persistent hole in the
 * host window. SurfaceFlinger keeps the hole valid continuously (unlike the
 * one-frame gap produced by toggling the window PixelFormat), so the Amlogic
 * hardware video plane behind the window can show through. The SurfaceView's
 * own surface is cleared to fully transparent and never painted opaque.
 */
public class HoleSurfaceView extends SurfaceView implements SurfaceHolder.Callback {

    public HoleSurfaceView(Context context) {
        super(context);
        SurfaceHolder holder = getHolder();
        holder.setFormat(PixelFormat.TRANSLUCENT);
        holder.addCallback(this);
        setFocusable(false);
        setFocusableInTouchMode(false);
        setClickable(false);
        setVisibility(GONE);
    }

    private void clearTransparent() {
        SurfaceHolder holder = getHolder();
        Canvas canvas = null;
        try {
            canvas = holder.lockCanvas();
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
        } catch (Exception ignored) {
        } finally {
            if (canvas != null) {
                try {
                    holder.unlockCanvasAndPost(canvas);
                } catch (Exception ignored) {
                }
            }
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        clearTransparent();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        clearTransparent();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
    }
}
