package teafabric.web.vfs;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import org.teavm.jso.JSBody;
import org.teavm.jso.typedarrays.Int8Array;
import teafabric.web.log.LogSink;

/**
 * Keeps the game directory (worlds, options, server list, config, resource packs) across page loads.
 *
 * <p>The game runs on TeaVM's in-memory file system. boot.js reads the files saved in IndexedDB before
 * the game starts; {@link #restore()} writes them back into {@code /minecraft}. A background green thread
 * then compares the directory every few seconds with what was saved (by length and modification time)
 * and hands changed and deleted files to boot.js, which writes them to IndexedDB in one transaction.
 * Minecraft saves a singleplayer world when the game is paused and when it is left, so a world is in
 * the browser's storage a few seconds after either.
 */
public final class WebPersistence {
    private static final long INTERVAL_MILLIS = 3000;

    /** Directories under the game directory that are not kept: the asset index (from assets.zip) and logs. */
    private static final String[] SKIPPED = {"assets", "logs", "crash-reports", "debug", "dropped"};

    /** Saved state: path -> {length, lastModified}. */
    private static final Map<String, long[]> saved = new HashMap<>();
    private static boolean started;

    private WebPersistence() {
    }

    @JSBody(script = "return !!(teafabric.store && teafabric.store.available);")
    private static native boolean available();

    @JSBody(script = "return teafabric.store.restored.length;")
    private static native int restoredCount();

    @JSBody(params = "i", script = "return teafabric.store.restored[i][0];")
    private static native String restoredPath(int i);

    @JSBody(params = "i", script = "return teafabric.store.restored[i][1];")
    private static native Int8Array restoredData(int i);

    @JSBody(script = "teafabric.store.restored = [];")
    private static native void releaseRestored();

    @JSBody(params = {"path", "data"}, script = "teafabric.store.put(path, data);")
    private static native void put(String path, Int8Array data);

    @JSBody(params = "path", script = "teafabric.store.remove(path);")
    private static native void remove(String path);

    @JSBody(script = "teafabric.store.flush();")
    private static native void flushStore();

    /** Writes the files saved in IndexedDB into the game directory. Called before Minecraft starts. */
    public static void restore() {
        if (!available()) {
            return;
        }
        int n = restoredCount();
        long bytes = 0;
        for (int i = 0; i < n; i++) {
            String path = restoredPath(i);
            if (!path.startsWith(WebFileSystem.GAME_DIR + "/")) {
                continue;
            }
            byte[] data = restoredData(i).copyToJavaArray();
            File file = new File(path);
            File parent = file.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            try (OutputStream out = new FileOutputStream(file)) {
                out.write(data);
            } catch (IOException e) {
                LogSink.log(2, "WARN", "Storage", "Cannot restore " + path, e);
                continue;
            }
            saved.put(path, new long[] {file.length(), file.lastModified()});
            bytes += data.length;
        }
        releaseRestored();
        if (n > 0) {
            LogSink.log(1, "INFO", "Storage", "Restored " + n + " files (" + (bytes >> 10) + " KiB) from browser storage",
                    null);
        }
    }

    /** Starts the background thread that saves changes. */
    public static void start() {
        if (started || !available()) {
            return;
        }
        started = true;
        Thread thread = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(INTERVAL_MILLIS);
                    sync();
                } catch (InterruptedException e) {
                    return;
                } catch (Throwable t) {
                    LogSink.log(2, "WARN", "Storage", "Saving to browser storage failed", t);
                }
            }
        }, "Browser storage");
        thread.setDaemon(true);
        thread.start();
    }

    /** Hands every file changed or deleted since the last call to IndexedDB. */
    public static synchronized void sync() throws IOException {
        Set<String> seen = new HashSet<>();
        int changed = collect(new File(WebFileSystem.GAME_DIR), true, seen);
        int removed = 0;
        for (Iterator<String> it = saved.keySet().iterator(); it.hasNext();) {
            String path = it.next();
            if (!seen.contains(path)) {
                it.remove();
                remove(path);
                removed++;
            }
        }
        if (changed > 0 || removed > 0) {
            flushStore();
        }
    }

    private static int collect(File dir, boolean top, Set<String> seen) throws IOException {
        File[] children = dir.listFiles();
        if (children == null) {
            return 0;
        }
        int changed = 0;
        for (File child : children) {
            if (child.isDirectory()) {
                if (top && skipped(child.getName())) {
                    continue;
                }
                changed += collect(child, false, seen);
                continue;
            }
            String path = child.getPath();
            seen.add(path);
            long length = child.length();
            long modified = child.lastModified();
            long[] state = saved.get(path);
            if (state != null && state[0] == length && state[1] == modified) {
                continue;
            }
            put(path, Int8Array.copyFromJavaArray(read(child)));
            saved.put(path, new long[] {length, modified});
            changed++;
        }
        return changed;
    }

    private static boolean skipped(String name) {
        for (String s : SKIPPED) {
            if (s.equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static byte[] read(File file) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream((int) Math.max(16, file.length()));
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        }
    }
}
