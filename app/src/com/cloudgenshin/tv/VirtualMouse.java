package com.cloudgenshin.tv;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

public class VirtualMouse extends View {

    private static final int DIR_UP = 0;
    private static final int DIR_DOWN = 1;
    private static final int DIR_LEFT = 2;
    private static final int DIR_RIGHT = 3;

    private static final float BASE_SPEED = 190f;
    private static final float MAX_SPEED = 820f;
    private static final float ACCEL = 950f;
    private static final float EDGE_THRESHOLD = 6f;
    private static final long SCROLL_INTERVAL_MS = 160;

    public interface ScrollListener {
        void onScroll(float vertical, float horizontal);
    }

    private final Activity activity;
    private View targetView;
    private ScrollListener scrollListener;
    private final boolean[] directions = new boolean[4];
    private float mouseX;
    private float mouseY;
    private boolean visible;
    private boolean ticking;
    private long moveStartTime;
    private boolean pressing;
    private long downTime;
    private float lastFrameTime;
    private boolean wasAtTop;
    private boolean wasAtBottom;
    private long lastScrollTime;

    private final Paint outerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint haloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public VirtualMouse(Activity activity, View target) {
        super(activity);
        this.activity = activity;
        this.targetView = target;
        setClickable(false);
        setFocusable(false);
        setWillNotDraw(false);
        float density = getResources().getDisplayMetrics().density;
        outerPaint.setColor(Color.argb(200, 0, 0, 0));
        outerPaint.setStyle(Paint.Style.STROKE);
        outerPaint.setStrokeWidth(4.5f * density);
        outerPaint.setStrokeJoin(Paint.Join.ROUND);
        ringPaint.setColor(Color.argb(245, 255, 255, 255));
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(2.2f * density);
        ringPaint.setStrokeJoin(Paint.Join.ROUND);
        dotPaint.setColor(Color.argb(245, 255, 255, 255));
        dotPaint.setStyle(Paint.Style.FILL);
        haloPaint.setColor(Color.argb(60, 255, 255, 255));
        haloPaint.setStyle(Paint.Style.FILL);
    }

    private float ringRadius() {
        return getResources().getDisplayMetrics().density * 12f;
    }

    public void addToWindow(ViewGroup container) {
        ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        container.addView(this, lp);
    }

    public void setTarget(View target) {
        this.targetView = target;
    }

    public void setScrollListener(ScrollListener listener) {
        this.scrollListener = listener;
    }

    public void show() {
        if (visible) return;
        visible = true;
        setVisibility(VISIBLE);
        mouseX = Math.max(mouseX, getWidth() * 0.5f);
        mouseY = Math.max(mouseY, getHeight() * 0.5f);
        if (mouseX <= 0 || mouseY <= 0) {
            mouseX = getWidth() * 0.5f;
            mouseY = getHeight() * 0.5f;
        }
        invalidate();
    }

    public void showAt(float x, float y) {
        mouseX = clamp(x, 2, Math.max(2, getWidth() - 2));
        mouseY = clamp(y, 2, Math.max(2, getHeight() - 2));
        show();
    }

    public void hide() {
        visible = false;
        setVisibility(GONE);
        releaseAll();
        invalidate();
    }

    public boolean isVisible() {
        return visible;
    }

    public boolean handleKeyDown(int keyCode, KeyEvent event) {
        int dir = directionOf(keyCode);
        if (dir >= 0) {
            if (!directions[dir]) {
                directions[dir] = true;
                if (!anyDirection()) {
                    moveStartTime = SystemClock.uptimeMillis();
                }
                ensureTicking();
            }
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            if (event.getRepeatCount() == 0 && !pressing) {
                pointerDown();
            }
            return true;
        }
        return false;
    }

    public boolean handleKeyUp(int keyCode, KeyEvent event) {
        int dir = directionOf(keyCode);
        if (dir >= 0) {
            directions[dir] = false;
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            if (pressing) {
                pointerUp();
            }
            return true;
        }
        return false;
    }

    private int directionOf(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP: return DIR_UP;
            case KeyEvent.KEYCODE_DPAD_DOWN: return DIR_DOWN;
            case KeyEvent.KEYCODE_DPAD_LEFT: return DIR_LEFT;
            case KeyEvent.KEYCODE_DPAD_RIGHT: return DIR_RIGHT;
            default: return -1;
        }
    }

    private boolean anyDirection() {
        return directions[DIR_UP] || directions[DIR_DOWN]
                || directions[DIR_LEFT] || directions[DIR_RIGHT];
    }

    private void ensureTicking() {
        if (ticking) return;
        ticking = true;
        lastFrameTime = SystemClock.uptimeMillis() / 1000f;
        postOnAnimation(frameRunnable);
    }

    private final Runnable frameRunnable = new Runnable() {
        @Override
        public void run() {
            if (!visible) {
                ticking = false;
                return;
            }
            float now = SystemClock.uptimeMillis() / 1000f;
            float dt = Math.min(now - lastFrameTime, 0.05f);
            lastFrameTime = now;

            long held = SystemClock.uptimeMillis() - moveStartTime;
            float speed = Math.min(BASE_SPEED + ACCEL * (held / 1000f), MAX_SPEED);
            float step = speed * dt;

            float dx = 0f;
            float dy = 0f;
            if (directions[DIR_LEFT]) dx -= step;
            if (directions[DIR_RIGHT]) dx += step;
            if (directions[DIR_UP]) dy -= step;
            if (directions[DIR_DOWN]) dy += step;

            if (dx != 0f && dy != 0f) {
                dx *= 0.7071f;
                dy *= 0.7071f;
            }

            float newX = mouseX + dx;
            float newY = mouseY + dy;
            newX = clamp(newX, 1, Math.max(1, getWidth() - 1));
            newY = clamp(newY, 1, Math.max(1, getHeight() - 1));

            boolean moved = (newX != mouseX || newY != mouseY);
            mouseX = newX;
            mouseY = newY;

            if (pressing && moved) {
                sendPointer(MotionEvent.ACTION_MOVE);
            }

            handleEdgeScroll(dx, dy);
            invalidate();

            if (anyDirection()) {
                postOnAnimation(this);
            } else {
                ticking = false;
                wasAtTop = false;
                wasAtBottom = false;
            }
        }
    };

    private void handleEdgeScroll(float dx, float dy) {
        boolean atTop = directions[DIR_UP] && mouseY <= EDGE_THRESHOLD;
        boolean atBottom = directions[DIR_DOWN] && mouseY >= getHeight() - EDGE_THRESHOLD;
        boolean atLeft = directions[DIR_LEFT] && mouseX <= EDGE_THRESHOLD;
        boolean atRight = directions[DIR_RIGHT] && mouseX >= getWidth() - EDGE_THRESHOLD;

        if (!atTop && !atBottom && !atLeft && !atRight) {
            wasAtTop = false;
            wasAtBottom = false;
            return;
        }

        long now = SystemClock.uptimeMillis();
        if (now - lastScrollTime < SCROLL_INTERVAL_MS) {
            return;
        }
        lastScrollTime = now;

        float vertical = 0f;
        float horizontal = 0f;
        if (atTop) vertical += 1f;
        if (atBottom) vertical -= 1f;
        if (atLeft) horizontal += 1f;
        if (atRight) horizontal -= 1f;

        if (scrollListener != null) {
            scrollListener.onScroll(vertical, horizontal);
        }
        wasAtTop = atTop;
        wasAtBottom = atBottom;
    }

    private void pointerDown() {
        downTime = SystemClock.uptimeMillis();
        sendPointer(MotionEvent.ACTION_DOWN);
        pressing = true;
    }

    private void pointerUp() {
        sendPointer(MotionEvent.ACTION_UP);
        pressing = false;
    }

    private void sendPointer(int action) {
        long now = SystemClock.uptimeMillis();
        MotionEvent ev = MotionEvent.obtain(downTime, now, action, mouseX, mouseY, 0);
        ev.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        try {
            if (targetView != null) {
                targetView.dispatchTouchEvent(ev);
            }
        } catch (Throwable t) {
            // ignore dispatch failures
        }
        ev.recycle();
    }

    public void releaseAll() {
        for (int i = 0; i < directions.length; i++) {
            directions[i] = false;
        }
        if (pressing) {
            sendPointer(MotionEvent.ACTION_CANCEL);
            pressing = false;
        }
    }

    private static float clamp(float value, float min, float max) {
        return value < min ? min : (value > max ? max : value);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!visible) return;
        float r = ringRadius();
        float dot = getResources().getDisplayMetrics().density * 1.8f;
        if (pressing) {
            canvas.drawCircle(mouseX, mouseY, r, haloPaint);
        }
        canvas.drawCircle(mouseX, mouseY, r, outerPaint);
        canvas.drawCircle(mouseX, mouseY, r, ringPaint);
        canvas.drawCircle(mouseX, mouseY, dot, dotPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return false;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        return false;
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        return false;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (mouseX <= 0 || mouseY <= 0) {
            mouseX = (right - left) * 0.5f;
            mouseY = (bottom - top) * 0.5f;
        }
    }
}
