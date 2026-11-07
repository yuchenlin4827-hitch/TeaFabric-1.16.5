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

    public static File file(Class<?> cls, String name) {
        return new File(WebFileSystem.CLASSPATH_DIR + "/" + resolve(cls, name));
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
