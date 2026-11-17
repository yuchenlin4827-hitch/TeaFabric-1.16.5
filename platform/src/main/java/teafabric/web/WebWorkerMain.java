package teafabric.web;

import io.netty.channel.ChannelHandler;
import java.io.File;
import net.minecraft.class_2966;
import org.teavm.jso.JSBody;
import teafabric.web.fabric.WebFabricLoader;
import teafabric.web.log.LogSink;
import teafabric.web.net.MemoryChannel;
import teafabric.web.net.WebSocketChannel;
import teafabric.web.vfs.WebFileSystem;

/**
 * Entry point of the Web Worker that runs singleplayer worlds (world-worker.js calls main with "--worker"): the same
 * program as the page, without a window, GL or sound. Minecraft is bootstrapped (registries, mods' main
 * entrypoints) and then the loop hands the page's commands to {@code teafabric.game.worker.WorkerHost} (webmod),
 * accepts the page's connections to the running world and moves their data.
 */
final class WebWorkerMain {
    private WebWorkerMain() {
    }

    /** The next connection the page opened to the worker (world-worker.js socket id), or 0. */
    @JSBody(script = "return teafabric.ws.accept();")
    private static native int accept();

    static void run() throws Exception {
        Thread.currentThread().setName("Worker main");
        File gameDir = new File(WebFileSystem.GAME_DIR);
        new File(gameDir, "config").mkdirs();
        WebLauncher.reflective = teafabric.generated.Reflective.classes(false);
        WebLauncher.phase("CrashReport.initCrashReport", net.minecraft.class_128::method_24305);
        WebLauncher.phase("Bootstrap.initialize", class_2966::method_12851);
        WebLauncher.phase("Bootstrap.logMissing", class_2966::method_17598);
        WebLauncher.phase("Mod initialisers (main)", () -> WebFabricLoader.INSTANCE.startServer(gameDir, null));
        LogSink.log(1, "INFO", "Worker", "Ready for worlds", null);
        teafabric.game.WorkerBridge.post("{\"t\":\"booted\"}");
        for (;;) {
            String cmd;
            while ((cmd = teafabric.game.WorkerBridge.poll()) != null) {
                teafabric.game.worker.WorkerHost.command(cmd);
            }
            int socket;
            while ((socket = accept()) > 0) {
                accept(socket);
            }
            WebSocketChannel.pumpAll();
            teafabric.game.worker.WorkerHost.poll();
            Thread.sleep(4);
        }
    }

    /** A connection from the page to the world: the server's local binding builds its pipeline. */
    private static void accept(int socket) {
        ChannelHandler child = MemoryChannel.lastBoundServer();
        if (child == null) {
            LogSink.log(2, "WARN", "Worker", "Connection " + socket + " with no world running", null);
            WebSocketChannel.closeSocket(socket);
            return;
        }
        WebSocketChannel channel = new WebSocketChannel(socket, "worker:" + socket, "page", socket);
        channel.pipeline().addLast(child); // ServerNetworkIo's local initialiser: the connection, a handshake handler
        teafabric.game.worker.WorkerHost.addCodecs(channel.pipeline());
        channel.pipeline().fireChannelActive();
        channel.register();
        LogSink.log(1, "INFO", "Worker", "The page connected (" + socket + ")", null);
    }
}
