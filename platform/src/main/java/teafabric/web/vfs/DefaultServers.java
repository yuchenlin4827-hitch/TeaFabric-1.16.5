package teafabric.web.vfs;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.class_2487;
import net.minecraft.class_2499;
import net.minecraft.class_2507;
import org.teavm.jso.JSBody;
import teafabric.web.log.LogSink;

/**
 * Puts the site's own servers into the multiplayer list (servers.dat), from servers.json next to the
 * page ({@code [{"name": ..., "ip": "wss://..."}]}, written by the deploy scripts). Each server is added
 * once, at the top: a player who removes it does not get it back (the added addresses are remembered
 * in teafabric-default-servers.txt).
 */
public final class DefaultServers {
    private DefaultServers() {
    }

    @JSBody(script = "return teafabric.config.defaultServers || null;")
    private static native String configJs();

    public static void apply() {
        String json = configJs();
        if (json == null || json.trim().isEmpty()) {
            return;
        }
        try {
            JsonArray servers = new JsonParser().parse(json).getAsJsonArray();
            File marker = new File(WebFileSystem.GAME_DIR, "teafabric-default-servers.txt");
            Set<String> added = new LinkedHashSet<>();
            if (marker.exists()) {
                added.addAll(Files.readAllLines(marker.toPath(), StandardCharsets.UTF_8));
            }
            File dat = new File(WebFileSystem.GAME_DIR, "servers.dat");
            class_2487 root = dat.exists() ? class_2507.method_10633(dat) : null;
            if (root == null) {
                root = new class_2487();
            }
            class_2499 list = root.method_10554("servers", 10);
            boolean changed = false;
            int insertAt = 0;
            for (JsonElement e : servers) {
                JsonObject s = e.getAsJsonObject();
                String ip = s.get("ip").getAsString();
                String name = s.has("name") ? s.get("name").getAsString() : ip;
                if (!added.add(ip)) {
                    continue;
                }
                boolean present = false;
                for (int i = 0; i < list.size(); i++) {
                    if (ip.equals(list.method_10602(i).method_10558("ip"))) {
                        present = true;
                        break;
                    }
                }
                if (!present) {
                    class_2487 entry = new class_2487();
                    entry.method_10582("name", name);
                    entry.method_10582("ip", ip);
                    list.method_10531(insertAt++, entry);
                    changed = true;
                }
            }
            if (changed) {
                root.method_10566("servers", list);
                File parent = dat.getParentFile();
                if (parent != null) {
                    parent.mkdirs();
                }
                class_2507.method_10630(root, dat);
                LogSink.log(1, "INFO", "Servers", "Added the site's server(s) to the multiplayer list", null);
            }
            Files.write(marker.toPath(), String.join("\n", added).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            LogSink.log(2, "WARN", "Servers", "Cannot add the default servers", e);
        }
    }
}
