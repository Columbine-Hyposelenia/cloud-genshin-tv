package com.cloudgenshin.tv;

import android.opengl.GLES20;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;

public final class GpuInfo {
    public final String vendor;
    public final String renderer;
    public final String version;
    public final String extensions;
    public final boolean extExternal;
    public final boolean extExternalEssl3;
    public final boolean extYuvTarget;
    public final boolean extSrgbWrite;

    private GpuInfo(String vendor, String renderer, String version, String extensions) {
        this.vendor = vendor;
        this.renderer = renderer;
        this.version = version;
        this.extensions = extensions == null ? "" : extensions;
        this.extExternal = has("GL_OES_EGL_image_external");
        this.extExternalEssl3 = has("GL_OES_EGL_image_external_essl3");
        this.extYuvTarget = has("GL_EXT_YUV_target");
        this.extSrgbWrite = has("GL_EXT_sRGB_write_control");
    }

    private boolean has(String extension) {
        int length = extensions.length();
        int index = extensions.indexOf(extension);
        while (index >= 0) {
            boolean start = index == 0 || extensions.charAt(index - 1) == ' ';
            int end = index + extension.length();
            boolean stop = end == length || extensions.charAt(end) == ' ';
            if (start && stop) {
                return true;
            }
            index = extensions.indexOf(extension, index + 1);
        }
        return false;
    }

    public static GpuInfo collect() {
        EGL10 egl = (EGL10) javax.microedition.khronos.egl.EGLContext.getEGL();
        EGLDisplay display = egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
        if (display == EGL10.EGL_NO_DISPLAY) {
            return null;
        }
        int[] version = new int[2];
        if (!egl.eglInitialize(display, version)) {
            return null;
        }

        EGLConfig config = chooseConfig(egl, display);
        EGLSurface surface = EGL10.EGL_NO_SURFACE;
        EGLContext context = EGL10.EGL_NO_CONTEXT;
        GpuInfo result = null;
        try {
            if (config != null) {
                surface = egl.eglCreatePbufferSurface(display, config,
                        new int[] {EGL10.EGL_WIDTH, 16, EGL10.EGL_HEIGHT, 16, EGL10.EGL_NONE});
                context = egl.eglCreateContext(display, config, EGL10.EGL_NO_CONTEXT,
                        new int[] {0x3098, 2, EGL10.EGL_NONE});
                if (surface != EGL10.EGL_NO_SURFACE && context != EGL10.EGL_NO_CONTEXT
                        && egl.eglMakeCurrent(display, surface, surface, context)) {
                    result = new GpuInfo(
                            GLES20.glGetString(GLES20.GL_VENDOR),
                            GLES20.glGetString(GLES20.GL_RENDERER),
                            GLES20.glGetString(GLES20.GL_VERSION),
                            GLES20.glGetString(GLES20.GL_EXTENSIONS));
                }
            }
        } finally {
            try {
                egl.eglMakeCurrent(display, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE,
                        EGL10.EGL_NO_CONTEXT);
                if (context != EGL10.EGL_NO_CONTEXT) {
                    egl.eglDestroyContext(display, context);
                }
                if (surface != EGL10.EGL_NO_SURFACE) {
                    egl.eglDestroySurface(display, surface);
                }
            } catch (Exception ignored) {
            }
            egl.eglTerminate(display);
        }
        return result;
    }

    private static EGLConfig chooseConfig(EGL10 egl, EGLDisplay display) {
        int[] attributes = {
                EGL10.EGL_SURFACE_TYPE, EGL10.EGL_PBUFFER_BIT,
                EGL10.EGL_RENDERABLE_TYPE, 0x0004,
                EGL10.EGL_NONE
        };
        EGLConfig[] configs = new EGLConfig[8];
        int[] count = new int[1];
        if (egl.eglChooseConfig(display, attributes, configs, configs.length, count)
                && count[0] > 0) {
            return configs[0];
        }
        EGLConfig[] fallback = new EGLConfig[1];
        if (egl.eglChooseConfig(display, new int[] {EGL10.EGL_NONE}, fallback, 1, count)
                && count[0] > 0) {
            return fallback[0];
        }
        return null;
    }
}
