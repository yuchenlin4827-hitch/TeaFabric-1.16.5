package teafabric.web.compat;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import teafabric.web.log.LogSink;

/** file: URLs resolve against the virtual file system (TeaVM's URL only knows http(s)). */
public final class UrlCompat {
    private UrlCompat() {
    }

    public static InputStream openStream(URL url) throws IOException {
        if ("file".equals(url.getProtocol())) {
            return new FileInputStream(new File(url.getPath()));
        }
        return url.openStream();
    }

    public static URLConnection openConnection(URL url) throws IOException {
        if ("file".equals(url.getProtocol())) {
            return new FileConnection(url);
        }
        return url.openConnection();
    }

    static final class FileConnection extends URLConnection {
        FileConnection(URL url) {
            super(url);
        }

        @Override
        public void connect() {
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return new FileInputStream(new File(getURL().getPath()));
        }

        @Override
        public long getContentLengthLong() {
            return new File(getURL().getPath()).length();
        }

        @Override
        public int getContentLength() {
            return (int) getContentLengthLong();
        }
    }

    /** Paths.get(URI) / Path.of(URI) for file: URIs (TeaVM's provider rejects them). */
    public static java.nio.file.Path get(java.net.URI uri) {
        if ("file".equals(uri.getScheme())) {
            return java.nio.file.Paths.get(uri.getPath());
        }
        return java.nio.file.Paths.get(uri);
    }

    public static java.nio.file.Path of(java.net.URI uri) {
        return get(uri);
    }

    /** Diagnostic: every thread start is logged (green threads are cheap but not free). */
    public static void start(Thread t) {
        LogSink.log(0, "DEBUG", "Threads", "start " + t.getName(), null);
        t.start();
    }
}
