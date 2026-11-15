package teafabric.web.vfs;

import org.teavm.runtime.fs.VirtualFile;
import org.teavm.runtime.fs.VirtualFileSystem;

/**
 * TeaVM's file system with relative paths resolved against the working directory. java.io.File makes a
 * path absolute before it reaches the file system, but java.nio.file passes a relative path as it is, and
 * the in-memory file system then looks it up from the root: LambdaBetterGrass and LambDynamicLights
 * (Paths.get("config/....toml")) could not create their config files.
 */
public final class WorkingDirFileSystem implements VirtualFileSystem {
    private final VirtualFileSystem inner;

    public WorkingDirFileSystem(VirtualFileSystem inner) {
        this.inner = inner;
    }

    @Override
    public String getUserDir() {
        return inner.getUserDir();
    }

    @Override
    public VirtualFile getFile(String path) {
        return inner.getFile(absolute(path));
    }

    private String absolute(String path) {
        if (path.startsWith("/")) {
            return path;
        }
        String dir = inner.getUserDir();
        while (path.startsWith("./")) {
            path = path.substring(2);
        }
        if (path.isEmpty() || path.equals(".")) {
            return dir;
        }
        return dir.endsWith("/") ? dir + path : dir + "/" + path;
    }

    @Override
    public boolean isWindows() {
        return inner.isWindows();
    }

    @Override
    public boolean isCaseSensitive() {
        return inner.isCaseSensitive();
    }

    @Override
    public String canonicalize(String path) {
        return inner.canonicalize(path);
    }

    @Override
    public String[] getRoots() {
        return inner.getRoots();
    }
}
