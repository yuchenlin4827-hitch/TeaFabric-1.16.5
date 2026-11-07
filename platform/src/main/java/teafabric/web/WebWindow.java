package teafabric.web;

import org.teavm.interop.Async;
import org.teavm.interop.AsyncCallback;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;
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

    @JSFunctor
    interface Callback extends JSObject {
        void run();
    }

    @JSBody(params = "f", script = "teafabric.rafRequests = (teafabric.rafRequests || 0) + 1;"
            + " requestAnimationFrame(function() { teafabric.rafCallbacks = (teafabric.rafCallbacks || 0) + 1; f(); });")
    private static native void requestAnimationFrame(Callback f);

    @JSBody(params = {"f", "ms"}, script = "setTimeout(function() { f(); }, ms);")
    private static native void setTimeout(Callback f, int ms);

    /** Suspends the calling green thread until the browser is ready for the next frame. */
    @Async
    public static native void waitForFrame();

    private static void waitForFrame(AsyncCallback<Void> callback) {
        try {
            requestAnimationFrame(() -> callback.complete(null));
        } catch (Throwable t) {
            teafabric.web.log.LogSink.log(3, "ERROR", "TeaFabric", "waitForFrame failed", t);
            callback.error(t);
        }
    }

    /** Suspends the calling green thread for {@code ms} milliseconds, letting the page process events. */
    @Async
    public static native void yieldMillis(int ms);

    private static void yieldMillis(int ms, AsyncCallback<Void> callback) {
        try {
            setTimeout(() -> callback.complete(null), ms);
        } catch (Throwable t) {
            teafabric.web.log.LogSink.log(3, "ERROR", "TeaFabric", "yieldMillis failed", t);
            callback.error(t);
        }
    }
}
