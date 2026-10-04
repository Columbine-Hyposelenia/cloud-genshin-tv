package com.cloudgenshin.tv;

import android.view.KeyEvent;

import java.util.HashSet;
import java.util.Set;

public class KeyboardRouter {
    private final VirtualMouse mMouse;
    private final Set<Integer> mKeyboardDevices = new HashSet<Integer>();
    private long mCenterDownTime;

    public KeyboardRouter(VirtualMouse mouse) {
        mMouse = mouse;
    }

    public boolean route(KeyEvent event) {
        int keyCode = event.getKeyCode();
        int deviceId = event.getDeviceId();

        if (isKeyboardSignal(keyCode) && deviceId >= 0) {
            registerKeyboard(deviceId);
        }

        if (isKeyboardDevice(deviceId)) {
            return false;
        }

        if (isSystemKey(keyCode)) {
            return false;
        }

        if (isDpadDirection(keyCode)) {
            handleDpadDirection(event, keyCode);
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            handleCenter(event);
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_BACK) {
            handleBack(event);
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_MENU
                || keyCode == KeyEvent.KEYCODE_SETTINGS
                || keyCode == KeyEvent.KEYCODE_INFO) {
            if (event.getAction() == KeyEvent.ACTION_UP) {
                onMenuRequested.run();
            }
            return true;
        }

        return true;
    }

    private void handleDpadDirection(KeyEvent event, int keyCode) {
        int dir = directionOf(keyCode);
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getRepeatCount() == 0) {
                mMouse.startDirection(dir);
            }
        } else if (event.getAction() == KeyEvent.ACTION_UP) {
            mMouse.stopDirection(dir);
        }
    }

    private void handleCenter(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            mCenterDownTime = event.getEventTime();
        } else if (event.getAction() == KeyEvent.ACTION_UP) {
            long held = event.getEventTime() - mCenterDownTime;
            if (held >= 480) {
                mMouse.longPress();
            } else {
                mMouse.click();
            }
        }
    }

    private void handleBack(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_UP) {
            onBackRequested.run();
        }
    }

    private void registerKeyboard(int deviceId) {
        if (mKeyboardDevices.add(deviceId)) {
            mMouse.stopAll();
            mMouse.hide();
        }
    }

    private boolean isKeyboardDevice(int deviceId) {
        if (deviceId < 0) {
            return false;
        }
        return mKeyboardDevices.contains(deviceId);
    }

    private boolean isKeyboardSignal(int keyCode) {
        if (keyCode >= KeyEvent.KEYCODE_A && keyCode <= KeyEvent.KEYCODE_Z) {
            return true;
        }
        return keyCode == KeyEvent.KEYCODE_CTRL_LEFT
                || keyCode == KeyEvent.KEYCODE_CTRL_RIGHT
                || keyCode == KeyEvent.KEYCODE_ALT_LEFT
                || keyCode == KeyEvent.KEYCODE_ALT_RIGHT
                || keyCode == KeyEvent.KEYCODE_SHIFT_LEFT
                || keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT
                || keyCode == KeyEvent.KEYCODE_TAB
                || keyCode == KeyEvent.KEYCODE_CAPS_LOCK;
    }

    private boolean isDpadDirection(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_UP
                || keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                || keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT;
    }

    private int directionOf(int keyCode) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            return VirtualMouse.DIR_LEFT;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            return VirtualMouse.DIR_RIGHT;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            return VirtualMouse.DIR_UP;
        }
        return VirtualMouse.DIR_DOWN;
    }

    private boolean isSystemKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_VOLUME_UP
                || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
                || keyCode == KeyEvent.KEYCODE_VOLUME_MUTE
                || keyCode == KeyEvent.KEYCODE_HOME
                || keyCode == KeyEvent.KEYCODE_POWER
                || keyCode == KeyEvent.KEYCODE_SEARCH
                || keyCode == KeyEvent.KEYCODE_ASSIST
                || keyCode == KeyEvent.KEYCODE_VOICE_ASSIST
                || keyCode == KeyEvent.KEYCODE_TV_INPUT
                || keyCode == KeyEvent.KEYCODE_TV_POWER
                || keyCode == KeyEvent.KEYCODE_STB_INPUT
                || keyCode == KeyEvent.KEYCODE_STB_POWER;
    }

    public Runnable onMenuRequested = new Runnable() {
        @Override
        public void run() {
        }
    };

    public Runnable onBackRequested = new Runnable() {
        @Override
        public void run() {
        }
    };
}
