package teafabric.web.compat;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import net.minecraft.class_156;
import org.teavm.jso.JSBody;
import org.teavm.jso.typedarrays.Int8Array;
import teafabric.web.log.LogSink;
import teafabric.web.vfs.WebFileSystem;

/**
 * Desktop integration in a page. Minecraft "opens" folders and links with xdg-open / explorer
 * ({@code Util.getOperatingSystem().open(...)}): here a folder is downloaded as a zip (Open World Folder,
 * Open Backups Folder after Make Backup, Open Pack Folder), a file is downloaded, and a web link opens in a
 * new tab. Worlds come back in by dropping their zip on the Select World screen ({@link #importDropped}).
 */
public final class DesktopCompat {
    private DesktopCompat() {
    }

    @JSBody(params = {"name", "data"}, script = "teafabric.download(name, data);")
    private static native void download(String name, Int8Array data);

    @JSBody(params = "url", script = "window.open(url, '_blank', 'noopener');")
    private static native void openLink(String url);

    // ---- Util.OperatingSystem (class_156.class_158) redirect targets

    public static void method_672(class_156.class_158 os, File file) {
        open(file);
    }

    public static void method_669(class_156.class_158 os, URL url) {
        if ("file".equals(url.getProtocol())) {
            try {
                open(new File(java.net.URLDecoder.decode(url.getPath(), "UTF-8")));
            } catch (java.io.UnsupportedEncodingException e) {
                open(new File(url.getPath()));
            }
        } else {
            openLink(url.toString());
        }
    }

    public static void method_673(class_156.class_158 os, URI uri) {
        if ("file".equals(uri.getScheme())) {
            open(new File(uri.getPath()));
        } else {
            openLink(uri.toString());
        }
    }

    public static void method_670(class_156.class_158 os, String target) {
        try {
            method_673(os, new URI(target));
        } catch (URISyntaxException e) {
            LogSink.log(2, "WARN", "Desktop", "Cannot open " + target, e);
        }
    }

    /** Minecraft passes e.g. {@code saves/New World/.}: drop "." segments and trailing separators. */
    private static File normalize(File file) {
        String path = file.getPath().replace('\\', '/');
        while (path.endsWith("/.") || (path.endsWith("/") && path.length() > 1)) {
            path = path.substring(0, path.length() - (path.endsWith("/.") ? 2 : 1));
        }
        return new File(path.replace("/./", "/"));
    }

    private static void open(File file) {
        file = normalize(file);
        try {
            if (file.isDirectory()) {
                File[] children = file.listFiles();
                if (children == null || children.length == 0) {
                    LogSink.log(1, "INFO", "Desktop", file + " is empty; nothing to download", null);
                    return;
                }
                if (children.length == 1 && children[0].isFile() && children[0].getName().endsWith(".zip")) {
                    open(children[0]); // e.g. the backups folder after one Make Backup
                    return;
                }
                download(file.getName() + ".zip", Int8Array.copyFromJavaArray(zip(file)));
            } else if (file.isFile()) {
                download(file.getName(), Int8Array.copyFromJavaArray(readFile(file)));
            } else {
                LogSink.log(2, "WARN", "Desktop", "Nothing to open at " + file, null);
            }
        } catch (IOException e) {
            LogSink.log(2, "WARN", "Desktop", "Cannot download " + file, e);
        }
    }

    private static byte[] zip(File dir) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            addToZip(zip, dir, dir.getName() + "/");
        }
        return bytes.toByteArray();
    }

    private static void addToZip(ZipOutputStream zip, File dir, String prefix) throws IOException {
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                addToZip(zip, child, prefix + child.getName() + "/");
            } else if (!child.getName().equals("session.lock")) {
                zip.putNextEntry(new ZipEntry(prefix + child.getName()));
                zip.write(readFile(child));
                zip.closeEntry();
            }
        }
    }

    // ---- world import

    /**
     * Files dropped on the page while the Select World screen is open: zips containing a world (a level.dat)
     * are unpacked into the saves folder and the list is reloaded. Returns true when the drop was consumed.
     */
    public static boolean importDropped(List<String> paths) {
        net.minecraft.class_310 client = net.minecraft.class_310.method_1551();
        if (client == null || !(client.field_1755 instanceof net.minecraft.class_526)) {
            return false;
        }
        boolean any = false;
        for (String path : paths) {
            if (!path.toLowerCase().endsWith(".zip")) {
                continue;
            }
            try {
                String name = importWorld(new File(path));
                if (name != null) {
                    LogSink.log(1, "INFO", "Desktop", "Imported world \"" + name + "\"", null);
                    any = true;
                }
            } catch (IOException e) {
                LogSink.log(2, "WARN", "Desktop", "Cannot import " + path, e);
            }
            new File(path).delete();
        }
        if (any) {
            client.method_1507(new net.minecraft.class_526(new net.minecraft.class_442()));
        }
        return any;
    }

    private static String importWorld(File zipFile) throws IOException {
        byte[] data = readFile(zipFile);
        // The world is the folder holding level.dat: the zip root or one folder down.
        String root = null;
        try (ZipInputStream in = new ZipInputStream(new java.io.ByteArrayInputStream(data))) {
            ZipEntry e;
            while ((e = in.getNextEntry()) != null) {
                String n = e.getName();
                if (n.equals("level.dat") || n.endsWith("/level.dat")) {
                    String candidate = n.substring(0, n.length() - "level.dat".length());
                    if (root == null || candidate.length() < root.length()) {
                        root = candidate;
                    }
                }
            }
        }
        if (root == null) {
            LogSink.log(2, "WARN", "Desktop", zipFile.getName() + " has no level.dat: not a world", null);
            return null;
        }
        String base = root.isEmpty() ? zipFile.getName().replaceAll("(?i)\\.zip$", "")
                : root.substring(0, root.length() - 1).replaceAll(".*/", "");
        base = base.replaceAll("[/\\\\:*?\"<>|]", "_");
        File saves = new File(WebFileSystem.GAME_DIR + "/saves");
        File target = new File(saves, base);
        for (int i = 2; target.exists(); i++) {
            target = new File(saves, base + " (" + i + ")");
        }
        List<String> written = new ArrayList<>();
        try (ZipInputStream in = new ZipInputStream(new java.io.ByteArrayInputStream(data))) {
            ZipEntry e;
            byte[] buf = new byte[65536];
            while ((e = in.getNextEntry()) != null) {
                String n = e.getName();
                if (e.isDirectory() || !n.startsWith(root) || n.contains("..")) {
                    continue;
                }
                File out = new File(target, n.substring(root.length()));
                out.getParentFile().mkdirs();
                try (OutputStream o = new java.io.FileOutputStream(out)) {
                    int r;
                    while ((r = in.read(buf)) > 0) {
                        o.write(buf, 0, r);
                    }
                }
                written.add(out.getPath());
            }
        }
        return written.isEmpty() ? null : target.getName();
    }

    static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[65536];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    static byte[] readFile(File f) throws IOException {
        try (InputStream in = new FileInputStream(f)) {
            return readAll(in);
        }
    }
}
