package teafabric.web.vfs;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import teafabric.web.log.LogSink;

/**
 * Lighter video settings for the browser, where the client, chunk building and (in singleplayer) the
 * server share one thread: applied once per player, and only to options still at vanilla's default, so
 * anything a player chose is kept. The options screen can raise them again.
 */
public final class WebDefaults {
    private static final String VERSION = "1";

    /** key -> {vanilla default, web default} */
    private static final String[][] DEFAULTS = {
        {"renderDistance", "8", "6"},       // 44% fewer chunks to build and draw
        {"biomeBlendRadius", "2", "1"},     // 3x3 instead of 5x5 colour blending in chunk building
        {"mipmapLevels", "4", "2"},         // 3 texture levels per animated sprite upload instead of 5
        {"renderClouds", "true", "fast"},
        {"particles", "0", "1"},            // decreased
    };

    private WebDefaults() {
    }

    public static void apply() {
        try {
            File marker = new File(WebFileSystem.GAME_DIR, "teafabric-web-defaults.txt");
            if (marker.exists() && VERSION.equals(new String(Files.readAllBytes(marker.toPath()),
                    StandardCharsets.UTF_8).trim())) {
                return;
            }
            File options = new File(WebFileSystem.GAME_DIR, "options.txt");
            Map<String, String> values = new LinkedHashMap<>();
            List<String> other = new ArrayList<>();
            if (options.exists()) {
                for (String line : Files.readAllLines(options.toPath(), StandardCharsets.UTF_8)) {
                    int colon = line.indexOf(':');
                    if (colon > 0) {
                        values.put(line.substring(0, colon), line.substring(colon + 1));
                    } else if (!line.isEmpty()) {
                        other.add(line);
                    }
                }
            } else {
                values.put("version", "2586"); // 1.16.5: no option upgrades to run on this file
            }
            int changed = 0;
            for (String[] d : DEFAULTS) {
                String current = values.get(d[0]);
                if (current == null || current.equals(d[1])) {
                    values.put(d[0], d[2]);
                    changed++;
                }
            }
            StringBuilder out = new StringBuilder();
            for (Map.Entry<String, String> e : values.entrySet()) {
                out.append(e.getKey()).append(':').append(e.getValue()).append('\n');
            }
            for (String line : other) {
                out.append(line).append('\n');
            }
            Files.write(options.toPath(), out.toString().getBytes(StandardCharsets.UTF_8));
            Files.write(marker.toPath(), VERSION.getBytes(StandardCharsets.UTF_8));
            LogSink.log(1, "INFO", "Options", "Lighter video settings for the browser (" + changed + " changed)", null);
        } catch (Exception e) {
            LogSink.log(2, "WARN", "Options", "Cannot apply the browser video settings", e);
        }
    }
}
