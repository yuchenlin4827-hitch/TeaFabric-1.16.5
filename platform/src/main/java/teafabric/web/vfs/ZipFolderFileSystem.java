package teafabric.web.vfs;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.WatchService;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.spi.FileSystemProvider;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * {@code FileSystems.newFileSystem(zipPath, loader)} for the browser build, which has no zip file system
 * provider: the archive is extracted once into {@code /zipfs/<n>} of the in-memory file system and the returned
 * file system resolves paths inside that folder (Iris opens zipped shader packs this way).
 */
public final class ZipFolderFileSystem extends FileSystem {
    private static final Map<String, String> EXTRACTED = new HashMap<>(); // zip path + size -> folder
    private static int counter;

    private final Path root;
    private final FileSystem base;

    private ZipFolderFileSystem(Path root) {
        this.root = root;
        this.base = root.getFileSystem();
    }

    public static FileSystem open(Path zip) throws IOException {
        if (!Files.isRegularFile(zip)) {
            throw new java.nio.file.NoSuchFileException(zip.toString());
        }
        String key = zip.toAbsolutePath() + "#" + Files.size(zip);
        String folder;
        synchronized (EXTRACTED) {
            folder = EXTRACTED.get(key);
            if (folder == null) {
                folder = "/zipfs/" + (++counter);
                byte[] data = Files.readAllBytes(zip);
                try {
                    WebFileSystem.mountZip(data, folder);
                } catch (IOException e) {
                    throw new java.util.zip.ZipException("not a zip archive: " + zip + " (" + e.getMessage() + ")");
                }
                EXTRACTED.put(key, folder);
            }
        }
        return new ZipFolderFileSystem(Paths.get(folder));
    }

    @Override
    public Path getPath(String first, String... more) {
        StringBuilder b = new StringBuilder(first);
        for (String m : more) {
            if (!m.isEmpty()) {
                b.append('/').append(m);
            }
        }
        String p = b.toString();
        while (p.startsWith("/")) {
            p = p.substring(1);
        }
        return p.isEmpty() ? root : root.resolve(p);
    }

    @Override
    public Iterable<Path> getRootDirectories() {
        return Collections.singletonList(root);
    }

    @Override
    public boolean isOpen() {
        return true;
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public String getSeparator() {
        return "/";
    }

    @Override
    public void close() {
        // the extracted files stay cached for the next open of the same archive
    }

    @Override
    public FileSystemProvider provider() {
        return base.provider();
    }

    @Override
    public Set<String> supportedFileAttributeViews() {
        return Collections.singleton("basic");
    }

    @Override
    public Iterable<FileStore> getFileStores() {
        return Collections.emptyList();
    }

    @Override
    public PathMatcher getPathMatcher(String syntaxAndPattern) {
        throw new UnsupportedOperationException();
    }

    @Override
    public UserPrincipalLookupService getUserPrincipalLookupService() {
        throw new UnsupportedOperationException();
    }

    @Override
    public WatchService newWatchService() {
        throw new UnsupportedOperationException();
    }

    static boolean safeEntry(String name) {
        for (String part : name.replace('\\', '/').split("/")) {
            if (part.equals("..")) {
                return false;
            }
        }
        return !new File(name).isAbsolute();
    }
}
