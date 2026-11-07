package org.lwjgl.glfw;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import org.lwjgl.PointerBuffer;
import org.lwjgl.opengl.WebGLAccess;
import org.teavm.jso.webgl.WebGL2RenderingContext;
import teafabric.web.WebWindow;
import teafabric.web.gl.GLEmu;
import teafabric.web.mem.NativeMemory;

/**
 * GLFW 3.3 on the browser: the window is the page's canvas, the context its WebGL 2 context.
 * DOM input is queued by boot.js and delivered from {@link #glfwPollEvents()} on the game thread,
 * like desktop GLFW; {@link #glfwSwapBuffers(long)} hands the frame to the compositor and suspends
 * the game thread until the next animation frame.
 */
public class GLFW {
    public static final int GLFW_TRUE = 1;
    public static final int GLFW_FALSE = 0;
    public static final int GLFW_RELEASE = 0;
    public static final int GLFW_PRESS = 1;
    public static final int GLFW_REPEAT = 2;
    public static final int GLFW_CURSOR = 0x00033001;
    public static final int GLFW_STICKY_KEYS = 0x00033002;
    public static final int GLFW_RAW_MOUSE_MOTION = 0x00033005;
    public static final int GLFW_CURSOR_NORMAL = 0x00034001;
    public static final int GLFW_CURSOR_HIDDEN = 0x00034002;
    public static final int GLFW_CURSOR_DISABLED = 0x00034003;
    public static final int GLFW_KEY_UNKNOWN = -1;
    public static final int GLFW_KEY_LAST = 348;
    public static final int GLFW_MOUSE_BUTTON_LEFT = 0;
    public static final int GLFW_MOUSE_BUTTON_RIGHT = 1;
    public static final int GLFW_MOUSE_BUTTON_MIDDLE = 2;
    public static final int GLFW_CONNECTED = 0x00040001;
    public static final int GLFW_DISCONNECTED = 0x00040002;
    public static final int GLFW_NO_ERROR = 0;

    static final long WINDOW = 1L;
    static final long MONITOR = 2L;

    private static GLFWKeyCallbackI keyCallback;
    private static GLFWCharModsCallbackI charModsCallback;
    private static GLFWCursorPosCallbackI cursorPosCallback;
    private static GLFWCursorEnterCallbackI cursorEnterCallback;
    private static GLFWMouseButtonCallbackI mouseButtonCallback;
    private static GLFWScrollCallbackI scrollCallback;
    private static GLFWDropCallbackI dropCallback;
    private static GLFWFramebufferSizeCallbackI framebufferSizeCallback;
    private static GLFWWindowSizeCallbackI windowSizeCallback;
    private static GLFWWindowPosCallbackI windowPosCallback;
    private static GLFWWindowFocusCallbackI windowFocusCallback;
    private static GLFWErrorCallbackI errorCallback;

    private static final byte[] keyState = new byte[GLFW_KEY_LAST + 1];
    private static boolean created;
    private static boolean shouldClose;
    private static int cursorMode = GLFW_CURSOR_NORMAL;
    private static double startTime = -1;
    private static long monitorsPtr;

    protected GLFW() {
    }

    static void clearCallbacks() {
        keyCallback = null;
        charModsCallback = null;
        cursorPosCallback = null;
        cursorEnterCallback = null;
        mouseButtonCallback = null;
        scrollCallback = null;
        dropCallback = null;
        framebufferSizeCallback = null;
        windowSizeCallback = null;
        windowPosCallback = null;
        windowFocusCallback = null;
    }

    // ------------------------------------------------------------------ init / windows

    public static boolean glfwInit() {
        if (startTime < 0) {
            startTime = WebWindow.now();
        }
        return true;
    }

    public static void glfwTerminate() {
    }

    public static void glfwDefaultWindowHints() {
    }

    public static void glfwWindowHint(int hint, int value) {
    }

    public static long glfwCreateWindow(int width, int height, CharSequence title, long monitor, long share) {
        created = true;
        WebWindow.setTitle(title.toString());
        return WINDOW;
    }

    public static long glfwCreateWindow(int width, int height, ByteBuffer title, long monitor, long share) {
        return glfwCreateWindow(width, height, org.lwjgl.system.MemoryUtil.memUTF8(title), monitor, share);
    }

    public static void glfwDestroyWindow(long window) {
        created = false;
    }

    public static void glfwMakeContextCurrent(long window) {
        if (window == 0 || WebGLAccess.isSet()) {
            return;
        }
        WebGL2RenderingContext gl = WebWindow.createGL();
        WebGLAccess.set(gl);
        GLEmu.init(gl);
    }

    public static long glfwGetCurrentContext() {
        return created ? WINDOW : 0;
    }

    public static void glfwSwapInterval(int interval) {
    }

    public static void glfwSwapBuffers(long window) {
        WebWindow.waitForFrame();
    }

    public static boolean glfwWindowShouldClose(long window) {
        return shouldClose;
    }

    public static void glfwSetWindowShouldClose(long window, boolean value) {
        shouldClose = value;
    }

    public static void glfwSetWindowTitle(long window, CharSequence title) {
        WebWindow.setTitle(title.toString());
    }

    public static void glfwSetWindowTitle(long window, ByteBuffer title) {
        WebWindow.setTitle(org.lwjgl.system.MemoryUtil.memUTF8(title));
    }

    public static void glfwSetWindowIcon(long window, GLFWImage.Buffer images) {
    }

    public static void glfwSetWindowMonitor(long window, long monitor, int x, int y, int w, int h, int refresh) {
        WebWindow.setFullscreen(monitor != 0);
    }

    public static long glfwGetWindowMonitor(long window) {
        return 0;
    }

    public static void glfwGetFramebufferSize(long window, int[] width, int[] height) {
        width[0] = WebWindow.width();
        height[0] = WebWindow.height();
    }

    public static void glfwGetFramebufferSize(long window, java.nio.IntBuffer width, java.nio.IntBuffer height) {
        width.put(width.position(), WebWindow.width());
        height.put(height.position(), WebWindow.height());
    }

    public static void glfwGetWindowSize(long window, int[] width, int[] height) {
        glfwGetFramebufferSize(window, width, height);
    }

    public static void glfwGetWindowPos(long window, int[] x, int[] y) {
        x[0] = 0;
        y[0] = 0;
    }

    public static void glfwSetWindowSize(long window, int w, int h) {
    }

    public static void glfwSetWindowPos(long window, int x, int y) {
    }

    // ------------------------------------------------------------------ monitors

    public static long glfwGetPrimaryMonitor() {
        return MONITOR;
    }

    public static PointerBuffer glfwGetMonitors() {
        if (monitorsPtr == 0) {
            monitorsPtr = NativeMemory.malloc(8);
            NativeMemory.putLong(monitorsPtr, MONITOR);
        }
        return PointerBuffer.create(NativeMemory.wrap(monitorsPtr, 8));
    }

    public static void glfwGetMonitorPos(long monitor, int[] x, int[] y) {
        x[0] = 0;
        y[0] = 0;
    }

    public static GLFWVidMode glfwGetVideoMode(long monitor) {
        return new GLFWVidMode(WebWindow.screenWidth(), WebWindow.screenHeight(), 60);
    }

    public static GLFWVidMode.Buffer glfwGetVideoModes(long monitor) {
        return new GLFWVidMode.Buffer(new GLFWVidMode[] {glfwGetVideoMode(monitor)});
    }

    public static String glfwGetMonitorName(long monitor) {
        return "Browser";
    }

    // ------------------------------------------------------------------ input

    public static void glfwSetInputMode(long window, int mode, int value) {
        if (mode == GLFW_CURSOR) {
            cursorMode = value;
            WebWindow.setPointerLock(value == GLFW_CURSOR_DISABLED);
        }
    }

    public static int glfwGetInputMode(long window, int mode) {
        return mode == GLFW_CURSOR ? cursorMode : 0;
    }

    public static boolean glfwRawMouseMotionSupported() {
        return false;
    }

    public static int glfwGetKey(long window, int key) {
        return key >= 0 && key < keyState.length ? keyState[key] : GLFW_RELEASE;
    }

    public static int glfwGetMouseButton(long window, int button) {
        return GLFW_RELEASE;
    }

    public static void glfwSetCursorPos(long window, double x, double y) {
    }

    public static String glfwGetKeyName(int key, int scancode) {
        if (key >= 65 && key <= 90) {
            return String.valueOf((char) (key + 32));
        }
        if ((key >= 44 && key <= 57) || key == 39 || key == 59 || key == 61 || (key >= 91 && key <= 93)
                || key == 96) {
            return String.valueOf((char) key);
        }
        return null;
    }

    public static int glfwGetKeyScancode(int key) {
        return key;
    }

    public static String glfwGetClipboardString(long window) {
        return WebWindow.getClipboard();
    }

    public static void glfwSetClipboardString(long window, CharSequence text) {
        WebWindow.setClipboard(text.toString());
    }

    public static void glfwSetClipboardString(long window, ByteBuffer text) {
        WebWindow.setClipboard(org.lwjgl.system.MemoryUtil.memUTF8(text));
    }

    public static double glfwGetTime() {
        if (startTime < 0) {
            startTime = WebWindow.now();
        }
        return (WebWindow.now() - startTime) / 1000.0;
    }

    public static void glfwSetTime(double time) {
        startTime = WebWindow.now() - time * 1000.0;
    }

    public static int glfwGetError(PointerBuffer description) {
        if (description != null) {
            description.put(description.position(), 0L);
        }
        return GLFW_NO_ERROR;
    }

    public static void glfwWaitEventsTimeout(double timeout) {
        glfwPollEvents();
    }

    public static void glfwWaitEvents() {
        glfwPollEvents();
    }

    public static void glfwPostEmptyEvent() {
    }

    /** Delivers queued DOM events as GLFW callbacks. Event layout: [type, a, b, c, d]. */
    public static void glfwPollEvents() {
        int n = WebWindow.eventCount();
        for (int i = 0; i < n; i++) {
            int type = (int) WebWindow.eventField(i, 0);
            double a = WebWindow.eventField(i, 1);
            double b = WebWindow.eventField(i, 2);
            int c = (int) WebWindow.eventField(i, 3);
            int d = (int) WebWindow.eventField(i, 4);
            try {
                dispatch(type, a, b, c, d);
            } catch (RuntimeException e) {
                e.printStackTrace();
            }
        }
        if (n > 0) {
            WebWindow.consumeEvents(n);
        }
    }

    private static void dispatch(int type, double a, double b, int c, int d) {
        switch (type) {
            case 1: { // key: glfwKey, scancode, action, mods
                int key = (int) a;
                if (key >= 0 && key < keyState.length) {
                    keyState[key] = (byte) (c == GLFW_RELEASE ? GLFW_RELEASE : GLFW_PRESS);
                }
                if (keyCallback != null) {
                    keyCallback.invoke(WINDOW, key, (int) b, c, d);
                }
                break;
            }
            case 2: // char: codepoint, mods
                if (charModsCallback != null) {
                    charModsCallback.invoke(WINDOW, (int) a, (int) b);
                }
                break;
            case 3: // cursor position (framebuffer pixels)
                if (cursorPosCallback != null) {
                    cursorPosCallback.invoke(WINDOW, a, b);
                }
                break;
            case 4: // mouse button: button, action, mods
                if (mouseButtonCallback != null) {
                    mouseButtonCallback.invoke(WINDOW, (int) a, (int) b, c);
                }
                break;
            case 5: // scroll
                if (scrollCallback != null) {
                    scrollCallback.invoke(WINDOW, a, b);
                }
                break;
            case 6: // resize
                if (framebufferSizeCallback != null) {
                    framebufferSizeCallback.invoke(WINDOW, (int) a, (int) b);
                }
                if (windowSizeCallback != null) {
                    windowSizeCallback.invoke(WINDOW, (int) a, (int) b);
                }
                break;
            case 7: // focus
                if (windowFocusCallback != null) {
                    windowFocusCallback.invoke(WINDOW, a != 0);
                }
                if (a == 0) {
                    java.util.Arrays.fill(keyState, (byte) 0);
                }
                break;
            case 8: // cursor enter
                if (cursorEnterCallback != null) {
                    cursorEnterCallback.invoke(WINDOW, a != 0);
                }
                break;
            case 9: // pointer lock lost (Esc pressed by the user): GLFW has no equivalent, report focus
                cursorMode = GLFW_CURSOR_NORMAL;
                break;
            case 10: // files dropped
                deliverDrop();
                break;
            default:
                break;
        }
    }

    private static void deliverDrop() {
        int count = WebWindow.dropCount();
        if (count == 0 || dropCallback == null) {
            WebWindow.clearDrops();
            return;
        }
        long names = NativeMemory.malloc(8L * count);
        long[] strings = new long[count];
        for (int i = 0; i < count; i++) {
            String path = teafabric.web.vfs.WebFileSystem.GAME_DIR + "/dropped/" + WebWindow.dropName(i);
            try {
                teafabric.web.vfs.WebFileSystem.writeFile(path, WebWindow.dropData(i).copyToJavaArray());
            } catch (java.io.IOException e) {
                e.printStackTrace();
            }
            byte[] utf8 = path.getBytes(StandardCharsets.UTF_8);
            strings[i] = NativeMemory.malloc(utf8.length + 1);
            NativeMemory.copyFromArray(utf8, 0, strings[i], utf8.length);
            NativeMemory.putByte(strings[i] + utf8.length, (byte) 0);
            NativeMemory.putLong(names + 8L * i, strings[i]);
        }
        WebWindow.clearDrops();
        try {
            dropCallback.invoke(WINDOW, count, names);
        } finally {
            for (long s : strings) {
                NativeMemory.free(s);
            }
            NativeMemory.free(names);
        }
    }

    // ------------------------------------------------------------------ callback registration

    public static GLFWErrorCallback glfwSetErrorCallback(GLFWErrorCallbackI cb) {
        errorCallback = cb;
        return null;
    }

    public static GLFWKeyCallback glfwSetKeyCallback(long window, GLFWKeyCallbackI cb) {
        keyCallback = cb;
        return null;
    }

    public static GLFWCharModsCallback glfwSetCharModsCallback(long window, GLFWCharModsCallbackI cb) {
        charModsCallback = cb;
        return null;
    }

    public static GLFWCursorPosCallback glfwSetCursorPosCallback(long window, GLFWCursorPosCallbackI cb) {
        cursorPosCallback = cb;
        return null;
    }

    public static GLFWCursorEnterCallback glfwSetCursorEnterCallback(long window, GLFWCursorEnterCallbackI cb) {
        cursorEnterCallback = cb;
        return null;
    }

    public static GLFWMouseButtonCallback glfwSetMouseButtonCallback(long window, GLFWMouseButtonCallbackI cb) {
        mouseButtonCallback = cb;
        return null;
    }

    public static GLFWScrollCallback glfwSetScrollCallback(long window, GLFWScrollCallbackI cb) {
        scrollCallback = cb;
        return null;
    }

    public static GLFWDropCallback glfwSetDropCallback(long window, GLFWDropCallbackI cb) {
        dropCallback = cb;
        return null;
    }

    public static GLFWFramebufferSizeCallback glfwSetFramebufferSizeCallback(long window,
            GLFWFramebufferSizeCallbackI cb) {
        framebufferSizeCallback = cb;
        return null;
    }

    public static GLFWWindowSizeCallback glfwSetWindowSizeCallback(long window, GLFWWindowSizeCallbackI cb) {
        windowSizeCallback = cb;
        return null;
    }

    public static GLFWWindowPosCallback glfwSetWindowPosCallback(long window, GLFWWindowPosCallbackI cb) {
        windowPosCallback = cb;
        return null;
    }

    public static GLFWWindowFocusCallback glfwSetWindowFocusCallback(long window, GLFWWindowFocusCallbackI cb) {
        windowFocusCallback = cb;
        return null;
    }

    public static GLFWMonitorCallback glfwSetMonitorCallback(GLFWMonitorCallbackI cb) {
        return null;
    }
}
