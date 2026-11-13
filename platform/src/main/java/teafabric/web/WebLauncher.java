package teafabric.web;

import com.mojang.authlib.properties.PropertyMap;
import com.mojang.blaze3d.systems.RenderSystem;
import java.io.File;
import java.net.Proxy;
import java.util.OptionalInt;
import net.minecraft.class_1657;
import net.minecraft.class_2966;
import net.minecraft.class_310;
import net.minecraft.class_320;
import net.minecraft.class_542;
import net.minecraft.class_543;
import teafabric.web.log.LogSink;
import teafabric.web.vfs.WebFileSystem;

/**
 * Browser launcher: the start-up sequence of {@code net.minecraft.client.main.Main} without
 * command-line parsing, desktop-only hooks (shutdown threads, Windows timer hack, separate game
 * thread) and with timing for each phase. Names are intermediary (Yarn names in comments).
 */
final class WebLauncher {
    private WebLauncher() {
    }

    static void phase(String name, Runnable r) {
        long t = System.currentTimeMillis();
        LogSink.log(1, "INFO", "TeaFabric", name + " ...", null);
        r.run();
        LogSink.log(1, "INFO", "TeaFabric", name + " done in " + (System.currentTimeMillis() - t) + " ms", null);
    }

    private static void probe(String name, Runnable r) {
        LogSink.log(1, "INFO", "Probe", name + " ...", null);
        try {
            r.run();
            LogSink.log(1, "INFO", "Probe", name + ": ok", null);
        } catch (Throwable t) {
            LogSink.log(1, "INFO", "Probe", name + ": threw " + t, null);
        }
    }

    /** {@code ?server=wss://...}: connect right after start-up, like the vanilla {@code --server} argument. */
    private static class_542.class_546 autoConnect() {
        String server = Boot.config("server");
        if (server == null || server.trim().isEmpty()) {
            return new class_542.class_546(null, 25565);
        }
        // ServerAddress.parse (redirected to NetCompat for WebSocket URLs)
        net.minecraft.class_639 address = net.minecraft.class_639.method_2950(server.trim());
        return new class_542.class_546(address.method_2952(), address.method_2954());
    }

    /** Protocol numbers of the releases ViaVersion can translate a 1.16.5 client down to. */
    private static final String[][] PROTOCOLS = {
        {"1.8", "47"}, {"1.9", "107"}, {"1.9.1", "108"}, {"1.9.2", "109"}, {"1.9.4", "110"}, {"1.10", "210"},
        {"1.11", "315"}, {"1.11.2", "316"}, {"1.12", "335"}, {"1.12.1", "338"}, {"1.12.2", "340"},
        {"1.13", "393"}, {"1.13.1", "401"}, {"1.13.2", "404"}, {"1.14", "477"}, {"1.14.1", "480"},
        {"1.14.2", "485"}, {"1.14.3", "490"}, {"1.14.4", "498"}, {"1.15", "573"}, {"1.15.1", "575"},
        {"1.15.2", "578"}, {"1.16", "735"}, {"1.16.1", "736"}, {"1.16.2", "751"}, {"1.16.3", "753"},
        {"1.16.4", "754"}, {"1.16.5", "754"},
    };

    /**
     * {@code ?protocol=1.12.2} (or a protocol number, or {@code auto}): preset ViaFabric's client-side
     * protocol, as its button on the multiplayer screen would. The game directory lives in memory, so the
     * in-game setting does not survive a page reload; this switch does.
     */
    private static void presetViaProtocol() {
        String wanted = Boot.config("protocol");
        if (wanted == null || wanted.trim().isEmpty()) {
            return;
        }
        wanted = wanted.trim();
        String number = null;
        if (wanted.equalsIgnoreCase("auto")) {
            number = "-1";
        } else if (wanted.matches("-?[0-9]+")) {
            number = wanted;
        } else {
            for (String[] p : PROTOCOLS) {
                if (p[0].equals(wanted)) {
                    number = p[1];
                }
            }
        }
        if (number == null) {
            LogSink.log(2, "WARN", "TeaFabric", "?protocol=" + wanted + ": unknown version (use e.g. 1.12.2)", null);
            return;
        }
        boolean enabled = !number.equals("754");
        File file = new File(WebFileSystem.GAME_DIR + "/config/ViaFabric/viafabric.yml");
        file.getParentFile().mkdirs();
        try (java.io.Writer w = new java.io.OutputStreamWriter(new java.io.FileOutputStream(file), "UTF-8")) {
            w.write("enable-client-side: " + enabled + "\nclient-side-version: " + number + "\n");
        } catch (java.io.IOException e) {
            LogSink.log(2, "WARN", "TeaFabric", "Cannot write " + file, e);
            return;
        }
        LogSink.log(1, "INFO", "TeaFabric", "ViaFabric protocol preset: " + wanted + " (" + number + ")", null);
    }

    static void launch(String username, int width, int height) {
        File gameDir = new File(WebFileSystem.GAME_DIR);
        presetViaProtocol();
        phase("CrashReport.initCrashReport", net.minecraft.class_128::method_24305);
        phase("Bootstrap.initialize", class_2966::method_12851);
        phase("Bootstrap.logMissing", class_2966::method_17598);

        // Session(username, uuid, accessToken, accountType); offline UUID as in vanilla
        String uuid = class_1657.method_7310(username).toString();
        class_320 session = new class_320(username, uuid, "0", "legacy");
        // RunArgs(Network, WindowSettings, Directories, Game, AutoConnect)
        class_542 args = new class_542(
                new class_542.class_547(session, new PropertyMap(), new PropertyMap(), Proxy.NO_PROXY),
                new class_543(width, height, OptionalInt.empty(), OptionalInt.empty(), false),
                new class_542.class_544(gameDir, new File(gameDir, "resourcepacks"), new File(gameDir, "assets"),
                        "1.16"),
                new class_542.class_545(false, "1.16.5", "release", false, false),
                autoConnect());

        Thread.currentThread().setName("Render thread");
        teafabric.web.compat.MinecraftCompat.renderThread = Thread.currentThread();
        phase("RenderSystem.initRenderThread", RenderSystem::initRenderThread);
        phase("RenderSystem.beginInitialization", RenderSystem::beginInitialization);
        class_310[] client = new class_310[1];
        phase("new MinecraftClient", () -> client[0] = new class_310(args));
        phase("RenderSystem.finishInitialization", RenderSystem::finishInitialization);
        // No separate game thread in the browser: the render thread runs the game loop, yielding
        // to the page once per frame in glfwSwapBuffers.
        RenderSystem.initGameThread(false);
        LogSink.log(1, "INFO", "TeaFabric", "Entering the game loop", null);
        client[0].method_1514(); // MinecraftClient.run()
        LogSink.log(2, "WARN", "TeaFabric", "MinecraftClient.run() returned (game loop ended)", null);
    }
}
