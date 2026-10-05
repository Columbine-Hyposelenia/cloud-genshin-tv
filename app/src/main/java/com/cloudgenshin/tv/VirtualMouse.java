package com.cloudgenshin.tv;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

public class VirtualMouse {
    public static final int DIR_LEFT = 0;
    public static final int DIR_RIGHT = 1;
    public static final int DIR_UP = 2;
    public static final int DIR_DOWN = 3;

    private static final float BASE_SPEED = 155f;
    private static final float MAX_SPEED = 560f;
    private static final float ACCEL = 470f;
    private static final float DIAGONAL = 0.7071f;
    private static final int EDGE = 6;
    private static final int EDGE_INTERVAL = 160;
    private static final int TICK = 16;
    private static final int CURSOR_SIZE = 32;
    private static final int IDLE_HIDE_DELAY = 3000;

    private final ViewGroup mRoot;
    private final View mTarget;
    private final ImageView mCursor;
    private final Handler mHandler;

    private final boolean[] mHeld = new boolean[4];
    private final float[] mSpeed = new float[4];
    private float mX;
    private float mY;
    private int mWidth;
    private int mHeight;
    private long mLastTick;
    private long mLastEdge;
    private int mLastEdgeDir = -1;
    private boolean mRunning;

    private final Runnable mTickRunnable = new Runnable() {
        @Override
        public void run() {
            step();
            if (mRunning) {
                mHandler.postDelayed(this, TICK);
            }
        }
    };

    private final Runnable mHideRunnable = new Runnable() {
        @Override
        public void run() {
            if (!anyHeld()) {
                hide();
            }
        }
    };

    public VirtualMouse(ViewGroup root, View target) {
        mRoot = root;
        mTarget = target;
        mHandler = new Handler(Looper.getMainLooper());
        mCursor = new ImageView(root.getContext());
        mCursor.setImageResource(R.drawable.cursor);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dp(CURSOR_SIZE), dp(CURSOR_SIZE));
        params.gravity = Gravity.TOP | Gravity.LEFT;
        mCursor.setLayoutParams(params);
        mRoot.addView(mCursor);
        mCursor.setVisibility(View.GONE);

        mRoot.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom,
                    int oldLeft, int oldTop, int oldRight, int oldBottom) {
                int w = right - left;
                int h = bottom - top;
                if (w > 0 && h > 0 && (w != mWidth || h != mHeight)) {
                    updateBounds(w, h);
                }
            }
        });
        mRoot.post(new Runnable() {
            @Override
            public void run() {
                int w = mRoot.getWidth();
                int h = mRoot.getHeight();
                if (w > 0 && h > 0 && (w != mWidth || h != mHeight)) {
                    updateBounds(w, h);
                }
            }
        });
    }

    private void updateBounds(int w, int h) {
        boolean wasZero = mWidth <= 0 && mHeight <= 0;
        mWidth = w;
        mHeight = h;
        if (wasZero && mX == 0f && mY == 0f) {
            mX = mWidth / 2f;
            mY = mHeight / 2f;
        }
        mX = Math.max(EDGE, Math.min(mWidth - EDGE, mX));
        mY = Math.max(EDGE, Math.min(mHeight - EDGE, mY));
        applyPosition();
    }

    public void onLayoutReady() {
        int w = mRoot.getWidth();
        int h = mRoot.getHeight();
        if (w > 0 && h > 0) {
            updateBounds(w, h);
        }
    }

    public void startDirection(int dir) {
        if (dir < 0 || dir > 3) {
            return;
        }
        boolean wasActive = anyHeld();
        if (!mHeld[dir]) {
            mHeld[dir] = true;
            mSpeed[dir] = 0f;
        }
        mHandler.removeCallbacks(mHideRunnable);
        show();
        if (!wasActive) {
            mLastTick = SystemClock.uptimeMillis();
            mRunning = true;
            mHandler.removeCallbacks(mTickRunnable);
            mHandler.post(mTickRunnable);
        }
    }

    public void stopDirection(int dir) {
        if (dir < 0 || dir > 3) {
            return;
        }
        mHeld[dir] = false;
        mSpeed[dir] = 0f;
        if (!anyHeld()) {
            mRunning = false;
            mHandler.removeCallbacks(mTickRunnable);
            scheduleIdleHide();
        }
    }

    public void stopAll() {
        for (int i = 0; i < 4; i++) {
            mHeld[i] = false;
            mSpeed[i] = 0f;
        }
        mRunning = false;
        mHandler.removeCallbacks(mTickRunnable);
        scheduleIdleHide();
    }

    private void scheduleIdleHide() {
        mHandler.removeCallbacks(mHideRunnable);
        mHandler.postDelayed(mHideRunnable, IDLE_HIDE_DELAY);
    }

    private boolean anyHeld() {
        for (boolean held : mHeld) {
            if (held) {
                return true;
            }
        }
        return false;
    }

    private void step() {
        if (mWidth <= 0 || mHeight <= 0) {
            int rootW = mRoot.getWidth();
            int rootH = mRoot.getHeight();
            if (rootW <= 0 || rootH <= 0) {
                return;
            }
            updateBounds(rootW, rootH);
            if (mWidth <= 0 || mHeight <= 0) {
                return;
            }
        }

        long now = SystemClock.uptimeMillis();
        float dt = (now - mLastTick) / 1000f;
        mLastTick = now;
        if (dt <= 0) {
            return;
        }
        if (dt > 0.05f) {
            dt = 0.05f;
        }

        float horizontal = 0f;
        float vertical = 0f;
        if (mHeld[DIR_LEFT]) {
            horizontal -= 1f;
        }
        if (mHeld[DIR_RIGHT]) {
            horizontal += 1f;
        }
        if (mHeld[DIR_UP]) {
            vertical -= 1f;
        }
        if (mHeld[DIR_DOWN]) {
            vertical += 1f;
        }

        if (horizontal != 0f) {
            int dir = horizontal > 0 ? DIR_RIGHT : DIR_LEFT;
            mSpeed[dir] = Math.min(MAX_SPEED, mSpeed[dir] + ACCEL * dt);
            float stepSpeed = BASE_SPEED + mSpeed[dir];
            if (vertical != 0f) {
                stepSpeed *= DIAGONAL;
            }
            mX += horizontal * stepSpeed * dt;
        }
        if (vertical != 0f) {
            int dir = vertical > 0 ? DIR_DOWN : DIR_UP;
            mSpeed[dir] = Math.min(MAX_SPEED, mSpeed[dir] + ACCEL * dt);
            float stepSpeed = BASE_SPEED + mSpeed[dir];
            if (horizontal != 0f) {
                stepSpeed *= DIAGONAL;
            }
            mY += vertical * stepSpeed * dt;
        }

        clampAndScroll(now);
        applyPosition();
    }

    private void clampAndScroll(long now) {
        boolean atLeft = mX <= EDGE;
        boolean atRight = mX >= mWidth - EDGE;
        boolean atTop = mY <= EDGE;
        boolean atBottom = mY >= mHeight - EDGE;

        mX = Math.max(EDGE, Math.min(mWidth - EDGE, mX));
        mY = Math.max(EDGE, Math.min(mHeight - EDGE, mY));

        int edgeDir = -1;
        if (atLeft) {
            edgeDir = DIR_LEFT;
        } else if (atRight) {
            edgeDir = DIR_RIGHT;
        } else if (atTop) {
            edgeDir = DIR_UP;
        } else if (atBottom) {
            edgeDir = DIR_DOWN;
        }

        if (edgeDir >= 0) {
            if (mLastEdgeDir != edgeDir) {
                mLastEdgeDir = edgeDir;
                mLastEdge = now;
                dispatchEdgeScroll(edgeDir);
            } else if (now - mLastEdge >= EDGE_INTERVAL) {
                mLastEdge = now;
                dispatchEdgeScroll(edgeDir);
            }
        } else {
            mLastEdgeDir = -1;
        }
    }

    private void dispatchEdgeScroll(int dir) {
        int keyCode = (dir == DIR_UP || dir == DIR_LEFT)
                ? android.view.KeyEvent.KEYCODE_PAGE_UP
                : android.view.KeyEvent.KEYCODE_PAGE_DOWN;
        injectKey(keyCode);
    }

    private void injectKey(int keyCode) {
        long now = SystemClock.uptimeMillis();
        mRoot.dispatchKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0));
        mRoot.dispatchKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0));
    }

    private void applyPosition() {
        mCursor.setX(mX);
        mCursor.setY(mY);
    }

    public void click() {
        mHandler.removeCallbacks(mHideRunnable);
        show();
        float hotSpotX = mX + dp(2);
        float hotSpotY = mY + dp(2);
        long downTime = SystemClock.uptimeMillis();
        dispatchTouch(downTime, downTime, MotionEvent.ACTION_DOWN, hotSpotX, hotSpotY);
        long upTime = SystemClock.uptimeMillis() + 16;
        dispatchTouch(downTime, upTime, MotionEvent.ACTION_UP, hotSpotX, hotSpotY);
        scheduleIdleHide();
    }

    public void longPress() {
        mHandler.removeCallbacks(mHideRunnable);
        show();
        float hotSpotX = mX + dp(2);
        float hotSpotY = mY + dp(2);
        long downTime = SystemClock.uptimeMillis();
        dispatchTouch(downTime, downTime, MotionEvent.ACTION_DOWN, hotSpotX, hotSpotY);
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                long upTime = SystemClock.uptimeMillis();
                dispatchTouch(downTime, upTime, MotionEvent.ACTION_UP, hotSpotX, hotSpotY);
                scheduleIdleHide();
            }
        }, 480);
    }

    private void dispatchTouch(long downTime, long eventTime, int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(downTime, eventTime, action, x, y, 0);
        event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        mTarget.dispatchTouchEvent(event);
        event.recycle();
    }

    public void show() {
        mCursor.setVisibility(View.VISIBLE);
        mCursor.bringToFront();
    }

    public void hide() {
        mCursor.setVisibility(View.GONE);
    }

    public boolean isVisible() {
        return mCursor.getVisibility() == View.VISIBLE;
    }

    private int dp(int value) {
        return Math.round(value * mRoot.getResources().getDisplayMetrics().density);
    }
}
