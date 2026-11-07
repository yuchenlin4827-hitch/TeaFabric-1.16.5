package org.lwjgl.opengl;

import org.teavm.jso.webgl.WebGL2RenderingContext;

/** Access to the current WebGL 2 context for direct pass-through calls. */
public final class WebGLAccess {
    private static WebGL2RenderingContext gl;

    private WebGLAccess() {
    }

    public static void set(WebGL2RenderingContext context) {
        gl = context;
    }

    public static boolean isSet() {
        return gl != null;
    }

    static WebGL2RenderingContext gl() {
        return gl;
    }
}
