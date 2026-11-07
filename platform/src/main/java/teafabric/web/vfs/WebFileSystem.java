package teafabric.web.vfs;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * File system layout of the browser build. TeaVM backs {@code java.io.File} and
 * {@code java.nio.file} with an in-memory virtual file system; this class populates it.
 *
 * <ul>
 *   <li>{@code /classpath} - non-class resources of the baked jar: vanilla assets/data at the root,
 *       each other mod under {@code teafabric/mods/<id>/};</li>
 *   <li>{@code /minecraft} - the game directory (options, saves, resource packs, config) and
 *       {@code /minecraft/assets} (asset index + hashed objects).</li>
 * </ul>
 */
public final class WebFileSystem {
    public static final String GAME_DIR = "/minecraft";
    public static final String CLASSPATH_DIR = "/classpath";

    private WebFileSystem() {
    }

    public static Path classpathRoot() {
        return Paths.get(CLASSPATH_DIR);
    }

    /** Extracts a zip archive held in memory into {@code targetDir}. Returns the number of files. */
    public static int mountZip(byte[] zip, String targetDir) throws IOException {
        int count = 0;
        byte[] buf = new byte[65536];
        File root = new File(targetDir);
        root.mkdirs();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry e;
            while ((e = in.getNextEntry()) != null) {
                File f = new File(root, e.getName());
                if (e.isDirectory()) {
                    f.mkdirs();
                    continue;
                }
                File parent = f.getParentFile();
                if (parent != null) {
                    parent.mkdirs();
                }
                try (OutputStream out = new FileOutputStream(f)) {
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        out.write(buf, 0, n);
                    }
                }
                count++;
            }
        }
        return count;
    }

    public static void writeFile(String path, byte[] data) throws IOException {
        File f = new File(path);
        File parent = f.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        try (OutputStream out = new FileOutputStream(f)) {
            out.write(data);
        }
    }

    public static byte[] readFile(String path) throws IOException {
        try (InputStream in = new java.io.FileInputStream(path)) {
            return in.readAllBytes();
        }
    }
}
