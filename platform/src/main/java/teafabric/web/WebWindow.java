package teafabric.web;

import org.teavm.interop.Async;
import org.teavm.interop.Export;
import org.teavm.interop.Import;
import org.teavm.interop.AsyncCallback;
import org.teavm.jso.JSBody;
import org.teavm.jso.webgl.WebGL2RenderingContext;

/**
 * Java side of the page bridge ({@code teafabric} object installed by boot.js): canvas, input
 * event queue, pointer lock, clipboard, fullscreen and frame pacing.
 */
public final class WebWindow {
    private WebWindow() {
    }

    @JSBody(script = "return teafabric.createGL();")
    public static native WebGL2RenderingContext createGL();

    @JSBody(script = "return teafabric.canvas.width;")
    public static native int width();

    @JSBody(script = "return teafabric.canvas.height;")
    public static native int height();

    @JSBody(script = "return (screen.width * (window.devicePixelRatio || 1)) | 0;")
    public static native int screenWidth();

    @JSBody(script = "return (screen.height * (window.devicePixelRatio || 1)) | 0;")
    public static native int screenHeight();

    @JSBody(script = "return teafabric.events.length;")
    public static native int eventCount();

    @JSBody(params = {"i", "j"}, script = "var e = teafabric.events[i]; return j < e.length ? +e[j] : 0;")
    public static native double eventField(int i, int j);

    @JSBody(params = "n", script = "teafabric.events.splice(0, n);")
    public static native void consumeEvents(int n);

    @JSBody(script = "return teafabric.dropNames.length;")
    public static native int dropCount();

    @JSBody(params = "i", script = "return teafabric.dropNames[i];")
    public static native String dropName(int i);

    @JSBody(params = "i", script = "return teafabric.dropData[i];")
    public static native org.teavm.jso.typedarrays.Int8Array dropData(int i);

    @JSBody(script = "teafabric.dropNames = []; teafabric.dropData = [];")
    public static native void clearDrops();

    @JSBody(params = "locked", script = "teafabric.setPointerLock(locked);")
    public static native void setPointerLock(boolean locked);

    @JSBody(params = "title", script = "document.title = title;")
    public static native void setTitle(String title);

    @JSBody(params = "on", script = "teafabric.setFullscreen(on);")
    public static native void setFullscreen(boolean on);

    @JSBody(script = "return teafabric.clipboard || '';")
    public static native String getClipboard();

    @JSBody(params = "text", script = "teafabric.setClipboard(text);")
    public static native void setClipboard(String text);

    @JSBody(params = "status", script = "teafabric.exit(status);")
    public static native void exit(int status);

    @JSBody(script = "return performance.now();")
    public static native double now();


    /**
     * Suspends the calling green thread until the browser is ready for the next frame.
     *
     * <p>The async implementation must not contain any call TeaVM may consider suspendable:
     * {@code Fiber.suspend} marks the fiber as suspending before running it, so a coroutine-transformed
     * implementation returns early at its first such call. The previous version marshalled a JSFunctor
     * lambda ({@code JSWrapper.unwrap}, classified as suspendable in Minecraft's call graph) and therefore
     * never reached {@code requestAnimationFrame}: the game hung on its first frame. Now the callback is
     * kept in a field, a plain Wasm import asks boot.js for an animation frame, and boot.js answers through
     * the exported {@link #frameReady()}, which resumes the thread (like TeaVM's own timers).
     */
    @Async
    public static native void waitForFrame();

    private static void waitForFrame(AsyncCallback<Void> callback) {
        if (frameWaiters == null) {
            frameWaiters = new AsyncCallback[4];
        } else if (frameWaiterCount == frameWaiters.length) {
            AsyncCallback<?>[] grown = new AsyncCallback[frameWaiterCount * 2];
            System.arraycopy(frameWaiters, 0, grown, 0, frameWaiterCount);
            frameWaiters = grown;
        }
        frameWaiters[frameWaiterCount++] = callback;
        if (frameWaiterCount == 1) {
            requestFrame();
        }
    }

    private static AsyncCallback<?>[] frameWaiters;
    private static int frameWaiterCount;

    @Import(module = "teafabricFrame", name = "request")
    private static native void requestFrame();

    /** Called by boot.js from requestAnimationFrame. */
    @Export(name = "teafabricFrameReady")
    @SuppressWarnings("unchecked")
    public static void frameReady() {
        int count = frameWaiterCount;
        AsyncCallback<?>[] waiters = frameWaiters;
        frameWaiters = null;
        frameWaiterCount = 0;
        for (int i = 0; i < count; i++) {
            ((AsyncCallback<Void>) waiters[i]).complete(null);
        }
    }

    /** Suspends the calling green thread for {@code ms} milliseconds, letting the page process events. */
    public static void yieldMillis(int ms) {
        try {
            Thread.sleep(Math.max(0, ms));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
