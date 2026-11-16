package teafabric.game;

import org.teavm.jso.JSBody;
import org.teavm.jso.typedarrays.Int8Array;

/**
 * Messages between the page (the game client) and the Web Worker that runs singleplayer worlds.
 *
 * <p>Control messages are JSON strings; files travel next to them. Packets do not pass here: the client
 * connects to the worker's server like to a multiplayer server, through boot.js' socket registry
 * ({@code ws://teafabric.worker/}, see boot.js and world-worker.js).
 */
public final class WorkerBridge {
    private WorkerBridge() {
    }

    // ------------------------------------------------------------------ page side (boot.js: teafabric.worker)

    /** A worker can be used (Web Workers available, not turned off with ?noworker). */
    @JSBody(script = "return !!(globalThis.teafabric && teafabric.worker && teafabric.worker.available);")
    public static native boolean available();

    /** Sends a control message to the worker, with the files added since the last one. */
    @JSBody(params = "json", script = "teafabric.worker.send(json);")
    public static native void send(String json);

    @JSBody(params = {"path", "data"}, script = "teafabric.worker.addFile(path, data);")
    private static native void addFile(String path, Int8Array data);

    public static void addFile(String path, byte[] data) {
        addFile(path, Int8Array.copyFromJavaArray(data));
    }

    /** A file the worker sent (its path relative to the game directory), or null; then {@link #takenFileData()}. */
    @JSBody(script = "return teafabric.worker.takeFile();")
    public static native String takeFile();

    @JSBody(script = "return teafabric.worker.takenFile;")
    private static native Int8Array takenFileData0();

    public static byte[] takenFileData() {
        return takenFileData0().copyToJavaArray();
    }

    /** The worker's latest status message (JSON), or null when none arrived since the last take. */
    @JSBody(script = "return teafabric.worker.takeStatus();")
    public static native String takeStatus();

    // ------------------------------------------------------------------ worker side (world-worker.js: teafabric.host)

    /** The next command from the page (JSON), or null. Its files are then available until the next poll. */
    @JSBody(script = "return teafabric.host.poll();")
    public static native String poll();

    @JSBody(script = "return teafabric.host.files.length;")
    public static native int fileCount();

    @JSBody(params = "i", script = "return teafabric.host.files[i][0];")
    public static native String filePath(int i);

    @JSBody(params = "i", script = "return teafabric.host.files[i][1];")
    private static native Int8Array fileData0(int i);

    public static byte[] fileData(int i) {
        return fileData0(i).copyToJavaArray();
    }

    @JSBody(params = {"path", "data"}, script = "teafabric.host.postFile(path, data);")
    private static native void postFile(String path, Int8Array data);

    /** Sends a file (path relative to the game directory) to the page's file system. */
    public static void postFile(String path, byte[] data) {
        postFile(path, Int8Array.copyFromJavaArray(data));
    }

    /** Sends a status message (JSON) to the page. */
    @JSBody(params = "json", script = "teafabric.host.post(json);")
    public static native void post(String json);
}
