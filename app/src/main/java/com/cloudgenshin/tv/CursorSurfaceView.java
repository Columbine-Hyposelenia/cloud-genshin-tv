package com.cloudgenshin.tv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

/**
 * Cursor rendered on a SurfaceView layered above the window. The reveal-mode
 * HoleSurfaceView punches the whole window transparent region, which wipes out
 * any view drawn inside the window (including the virtual cursor); putting the
 * cursor on an on-top surface keeps it visible over the hole.
 */
public class CursorSurfaceView extends SurfaceView implements SurfaceHolder.Callback {

    private final Drawable mDrawable;
    private boolean mReady;

    public CursorSurfaceView(Context context) {
        super(context);
        mDrawable = context.getResources().getDrawable(R.drawable.cursor);
        SurfaceHolder holder = getHolder();
        holder.setFormat(PixelFormat.TRANSLUCENT);
        setZOrderOnTop(true);
        holder.addCallback(this);
        setFocusable(false);
        setFocusableInTouchMode(false);
        setClickable(false);
    }

    public void drawCursor() {
        if (!mReady || mDrawable == null) {
            return;
        }
        Canvas canvas = null;
        try {
            canvas = getHolder().lockCanvas();
            if (canvas != null) {
                int w = getWidth();
                int h = getHeight();
                if (w > 0 && h > 0) {
                    canvas.drawColor(0, PorterDuff.Mode.CLEAR);
                    mDrawable.setBounds(0, 0, w, h);
                    mDrawable.draw(canvas);
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (canvas != null) {
                try {
                    getHolder().unlockCanvasAndPost(canvas);
                } catch (Exception ignored) {
                }
            }
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        mReady = true;
        drawCursor();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        mReady = true;
        drawCursor();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        mReady = false;
    }
}
