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

    static void launch(String username, int width, int height) {
        File gameDir = new File(WebFileSystem.GAME_DIR);
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
                new class_542.class_546(null, 25565));

        Thread.currentThread().setName("Render thread");
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
