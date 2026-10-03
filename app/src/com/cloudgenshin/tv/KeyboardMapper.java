package com.cloudgenshin.tv;

import android.view.InputDevice;
import android.view.KeyEvent;
import android.webkit.WebView;

public class KeyboardMapper {

    private final WebView webView;
    private boolean physicalKeyboardConnected;

    public KeyboardMapper(WebView webView) {
        this.webView = webView;
        this.physicalKeyboardConnected = detectPhysicalKeyboard();
    }

    private boolean detectPhysicalKeyboard() {
        int[] deviceIds = InputDevice.getDeviceIds();
        if (deviceIds == null) return false;
        for (int id : deviceIds) {
            InputDevice device = InputDevice.getDevice(id);
            if (device == null) continue;
            int sources = device.getSources();
            boolean isKeyboard = (sources & InputDevice.SOURCE_KEYBOARD) == InputDevice.SOURCE_KEYBOARD;
            boolean isDpad = (sources & InputDevice.SOURCE_DPAD) == InputDevice.SOURCE_DPAD;
            if (isKeyboard && !isDpad) {
                String name = device.getName();
                if (name != null && !name.contains("sim-") && !name.contains("aml_keypad")
                        && !name.contains("adc_keypad") && !name.contains("cec")) {
                    return true;
                }
            }
        }
        return false;
    }

    public void refreshKeyboardState() {
        this.physicalKeyboardConnected = detectPhysicalKeyboard();
    }

    public boolean isPhysicalKeyboardConnected() {
        return physicalKeyboardConnected;
    }

    public boolean handleKeyDown(int keyCode, KeyEvent event) {
        if (isGameKey(keyCode)) {
            return dispatchKeyToWebView(event);
        }
        if (isNavigationKey(keyCode)) {
            return false;
        }
        if (event.getSource() == InputDevice.SOURCE_KEYBOARD
                && !isRemoteControlKey(keyCode)) {
            return dispatchKeyToWebView(event);
        }
        return false;
    }

    public boolean handleKeyUp(int keyCode, KeyEvent event) {
        if (isGameKey(keyCode)) {
            return dispatchKeyToWebView(event);
        }
        if (isNavigationKey(keyCode)) {
            return false;
        }
        if (event.getSource() == InputDevice.SOURCE_KEYBOARD
                && !isRemoteControlKey(keyCode)) {
            return dispatchKeyToWebView(event);
        }
        return false;
    }

    private boolean dispatchKeyToWebView(KeyEvent event) {
        try {
            return webView.dispatchKeyEvent(event);
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean isGameKey(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_W:
            case KeyEvent.KEYCODE_A:
            case KeyEvent.KEYCODE_S:
            case KeyEvent.KEYCODE_D:
            case KeyEvent.KEYCODE_SPACE:
            case KeyEvent.KEYCODE_SHIFT_LEFT:
            case KeyEvent.KEYCODE_SHIFT_RIGHT:
            case KeyEvent.KEYCODE_E:
            case KeyEvent.KEYCODE_Q:
            case KeyEvent.KEYCODE_R:
            case KeyEvent.KEYCODE_F:
            case KeyEvent.KEYCODE_1:
            case KeyEvent.KEYCODE_2:
            case KeyEvent.KEYCODE_3:
            case KeyEvent.KEYCODE_4:
            case KeyEvent.KEYCODE_ESCAPE:
            case KeyEvent.KEYCODE_TAB:
            case KeyEvent.KEYCODE_M:
            case KeyEvent.KEYCODE_B:
            case KeyEvent.KEYCODE_C:
            case KeyEvent.KEYCODE_ALT_LEFT:
            case KeyEvent.KEYCODE_ALT_RIGHT:
            case KeyEvent.KEYCODE_CTRL_LEFT:
            case KeyEvent.KEYCODE_CTRL_RIGHT:
                return true;
            default:
                return false;
        }
    }

    private boolean isNavigationKey(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_HOME:
                return true;
            default:
                return false;
        }
    }

    private boolean isRemoteControlKey(int keyCode) {
        return isNavigationKey(keyCode);
    }

    public boolean shouldUseVirtualMouse(int keyCode) {
        return isNavigationKey(keyCode) && keyCode != KeyEvent.KEYCODE_BACK
                && keyCode != KeyEvent.KEYCODE_MENU && keyCode != KeyEvent.KEYCODE_HOME;
    }
}
