package teafabric.web.vfs;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;

/** Class-path resource lookup against {@code /classpath} in the virtual file system. */
public final class WebResources {
    private WebResources() {
    }

    static String resolve(Class<?> cls, String name) {
        if (name.startsWith("/")) {
            return name.substring(1);
        }
        if (cls == null) {
            return name;
        }
        String cn = cls.getName();
        int dot = cn.lastIndexOf('.');
        return dot < 0 ? name : cn.substring(0, dot).replace('.', '/') + "/" + name;
    }

    private static String[] modRoots;

    /**
     * Like a class loader over Minecraft plus every mod jar: Minecraft's resources sit at the root of
     * {@code /classpath}, each other mod's under {@code teafabric/mods/<id>/} (see the baker), so a
     * name missing at the root is looked up in the mod roots too (ViaVersion's mapping data, config
     * defaults bundled in mods, ...).
     */
    public static File file(Class<?> cls, String name) {
        String rel = resolve(cls, name);
        File f = new File(WebFileSystem.CLASSPATH_DIR + "/" + rel);
        if (f.exists() || rel.isEmpty()) {
            return f;
        }
        for (String root : modRoots()) {
            File g = new File(root + "/" + rel);
            if (g.exists()) {
                return g;
            }
        }
        return f;
    }

    private static String[] modRoots() {
        if (modRoots == null) {
            File[] dirs = new File(WebFileSystem.CLASSPATH_DIR + "/teafabric/mods").listFiles();
            if (dirs == null) {
                return new String[0]; // not mounted yet: don't cache
            }
            java.util.Arrays.sort(dirs);
            String[] roots = new String[dirs.length];
            for (int i = 0; i < dirs.length; i++) {
                roots[i] = dirs[i].getPath();
            }
            modRoots = roots;
        }
        return modRoots;
    }

    public static InputStream getResourceAsStream(Class<?> cls, String name) {
        File f = file(cls, name);
        if (!f.isFile()) {
            return null;
        }
        try {
            return new FileInputStream(f);
        } catch (IOException e) {
            return null;
        }
    }

    public static URL getResource(Class<?> cls, String name) {
        File f = file(cls, name);
        if (!f.exists()) {
            return null;
        }
        try {
            return new URL("file", "", f.getPath());
        } catch (MalformedURLException e) {
            return null;
        }
    }
}
