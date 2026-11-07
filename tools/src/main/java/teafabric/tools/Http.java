package teafabric.tools;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

/** Small HTTP helper with SHA-1 verification, caching and retry with exponential backoff. */
public final class Http {
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    private static final String USER_AGENT = "TeaFabric-setup/1.0";

    private Http() {
    }

    public static String getString(String url) throws IOException {
        return new String(getBytes(url), java.nio.charset.StandardCharsets.UTF_8);
    }

    public static byte[] getBytes(String url) throws IOException {
        IOException last = null;
        for (int attempt = 0; attempt < 5; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                        .header("User-Agent", USER_AGENT)
                        .timeout(Duration.ofMinutes(5))
                        .GET().build();
                HttpResponse<byte[]> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
                int code = response.statusCode();
                if (code == 200) {
                    return response.body();
                }
                last = new IOException("HTTP " + code + " for " + url);
                if (code == 404) {
                    throw last;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException(e);
            } catch (IOException e) {
                if (e.getMessage() != null && e.getMessage().startsWith("HTTP 404")) {
                    throw e;
                }
                last = e;
            }
            sleepBackoff(attempt);
        }
        throw last;
    }

    /**
     * Downloads {@code url} into {@code target} unless a file with the expected SHA-1 is already there.
     *
     * @param sha1 expected lowercase hex SHA-1 or {@code null} to skip verification
     *             (existing files are then reused as-is)
     * @return {@code true} if a download happened
     */
    public static boolean download(String url, Path target, String sha1) throws IOException {
        if (Files.isRegularFile(target)) {
            if (sha1 == null || sha1.equalsIgnoreCase(sha1(target))) {
                return false;
            }
        }
        Files.createDirectories(target.getParent());
        byte[] data = getBytes(url);
        if (sha1 != null) {
            String actual = sha1(data);
            if (!actual.equalsIgnoreCase(sha1)) {
                throw new IOException("SHA-1 mismatch for " + url + ": expected " + sha1 + ", got " + actual);
            }
        }
        Path tmp = target.resolveSibling(target.getFileName() + ".part");
        Files.write(tmp, data);
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        return true;
    }

    public static String sha1(Path file) throws IOException {
        MessageDigest md = sha1Digest();
        byte[] buf = new byte[1 << 16];
        try (InputStream in = Files.newInputStream(file)) {
            int n;
            while ((n = in.read(buf)) > 0) {
                md.update(buf, 0, n);
            }
        }
        return HexFormat.of().formatHex(md.digest());
    }

    public static String sha1(byte[] data) {
        return HexFormat.of().formatHex(sha1Digest().digest(data));
    }

    private static MessageDigest sha1Digest() {
        try {
            return MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void sleepBackoff(int attempt) {
        try {
            Thread.sleep(2000L << attempt);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
