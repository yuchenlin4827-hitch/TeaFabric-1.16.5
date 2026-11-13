package teafabric.web;

import java.io.File;
import org.teavm.jso.JSBody;
import org.teavm.jso.typedarrays.Int8Array;
import teafabric.web.log.LogSink;
import teafabric.web.vfs.WebFileSystem;

/** Start-up: mounts the archives boot.js downloaded and builds Minecraft's command line. */
public final class Boot {
    private Boot() {
    }

    @JSBody(script = "return Object.keys(teafabric.archives).length;")
    private static native int archiveCount();

    @JSBody(params = "i", script = "return Object.keys(teafabric.archives)[i];")
    private static native String archiveMount(int i);

    @JSBody(params = "i", script = "var k = Object.keys(teafabric.archives)[i]; var a = teafabric.archives[k];"
            + "delete teafabric.archives[k]; return new Int8Array(a);")
    private static native Int8Array takeArchive(int i);

    @JSBody(params = "key", script = "var v = teafabric.config[key]; return v === undefined || v === null ? null : '' + v;")
    public static native String config(String key);

    static void mountFileSystem() throws java.io.IOException {
        int n = archiveCount();
        String[] mounts = new String[n];
        for (int i = 0; i < n; i++) {
            mounts[i] = archiveMount(i);
        }
        for (String mount : mounts) {
            long start = System.currentTimeMillis();
            // after each take the remaining keys shift, so always take index 0
            byte[] data = takeArchive(0).copyToJavaArray();
            int files = WebFileSystem.mountZip(data, mount);
            LogSink.log(1, "INFO", "TeaFabric", "Mounted " + files + " files at " + mount + " ("
                    + data.length / 1024 + " KiB, " + (System.currentTimeMillis() - start) + " ms)", null);
        }
        new File(WebFileSystem.GAME_DIR + "/resourcepacks").mkdirs();
        new File(WebFileSystem.GAME_DIR + "/saves").mkdirs();
        new File(WebFileSystem.GAME_DIR + "/config").mkdirs();
        // Worlds, options and the server list from earlier visits (IndexedDB), then keep saving changes.
        teafabric.web.vfs.WebPersistence.restore();
        teafabric.web.vfs.WebPersistence.start();
    }

    public static boolean probe() {
        return config("probe") != null;
    }

    static String username() {
        String user = config("username");
        return user == null || user.isEmpty() ? "Player" : user;
    }

    static String[] gameArguments() {
        String user = config("username");
        if (user == null || user.isEmpty()) {
            user = "Player";
        }
        return new String[] {
            "--username", user,
            "--version", "1.16.5",
            "--accessToken", "0",
            "--userType", "legacy",
            "--gameDir", WebFileSystem.GAME_DIR,
            "--assetsDir", WebFileSystem.GAME_DIR + "/assets",
            "--assetIndex", "1.16",
            "--width", String.valueOf(WebWindow.width()),
            "--height", String.valueOf(WebWindow.height())
        };
    }
}
