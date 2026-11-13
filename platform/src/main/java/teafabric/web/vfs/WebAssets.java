package teafabric.web.vfs;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.teavm.interop.Async;
import org.teavm.interop.AsyncCallback;
import org.teavm.interop.Export;
import org.teavm.jso.JSBody;
import org.teavm.jso.typedarrays.Int8Array;

/**
 * Game assets that are not in assets.zip but next to the page, fetched when first needed (e.g. the
 * translations of a language: lang/&lt;hash&gt;.json). assets.zip holds a placeholder for each of them,
 * {@code TEAFABRIC-ASSET\n<hash>\n} (build.gradle, webAssetPlaceholders), so Minecraft sees the asset.
 */
public final class WebAssets {
    private static final byte[] MARKER = "TEAFABRIC-ASSET\n".getBytes(StandardCharsets.UTF_8);
    private static final Map<Integer, AsyncCallback<Boolean>> waiting = new HashMap<>();
    private static int nextId = 1;

    private WebAssets() {
    }

    /** The object hash if {@code data} is a placeholder, else null. */
    public static String placeholderHash(byte[] data) {
        if (data.length < MARKER.length) {
            return null;
        }
        for (int i = 0; i < MARKER.length; i++) {
            if (data[i] != MARKER[i]) {
                return null;
            }
        }
        String hash = new String(data, MARKER.length, data.length - MARKER.length, StandardCharsets.UTF_8).trim();
        return hash.matches("[0-9a-f]{40}") ? hash : null;
    }

    /** Fetches a file next to the page; null on failure. Suspends the calling thread meanwhile. */
    public static byte[] fetch(String url) {
        int id = nextId++;
        if (!fetch(id, url)) {
            return null;
        }
        Int8Array data = takeJs(id);
        return data == null ? null : data.copyToJavaArray();
    }

    @Async
    private static native boolean fetch(int id, String url);

    private static void fetch(int id, String url, AsyncCallback<Boolean> callback) {
        waiting.put(id, callback);
        if (!startJs(id, url)) {
            waiting.remove(id);
            callback.complete(false);
        }
    }

    /** Called by boot.js when a fetch has finished (ok = 1) or failed (ok = 0). */
    @Export(name = "teafabricAssetFetched")
    public static void fetched(int id, int ok) {
        AsyncCallback<Boolean> callback = waiting.remove(id);
        if (callback != null) {
            callback.complete(ok != 0);
        }
    }

    @JSBody(params = {"id", "url"}, script = "return !!(teafabric.fetchAsset && teafabric.fetchAsset(id, url));")
    private static native boolean startJs(int id, String url);

    @JSBody(params = "id", script = "return teafabric.takeAsset(id);")
    private static native Int8Array takeJs(int id);
}
