package teafabric.web;

import teafabric.web.log.LogSink;

/** Browser entry point. boot.js mounts the virtual file system before calling {@code main}. */
public final class WebMain {
    private static volatile boolean exitRequested;

    private WebMain() {
    }

    /** Thrown to unwind the game thread when Minecraft calls System.exit. */
    public static final class ExitError extends Error {
        private static final long serialVersionUID = 1L;
        public final int status;

        ExitError(int status) {
            super("System.exit(" + status + ")");
            this.status = status;
        }
    }

    public static boolean isExitRequested() {
        return exitRequested;
    }

    public static ExitError requestExit(int status) {
        exitRequested = true;
        WebWindow.exit(status);
        return new ExitError(status);
    }

    public static void main(String[] args) {
        try {
            if (Boot.probe()) {
                LogSink.log(1, "INFO", "TeaFabric", "early sleep ...", null);
                Thread.sleep(1);
                LogSink.log(1, "INFO", "TeaFabric", "early sleep done", null);
            }
            Boot.mountFileSystem();
            LogSink.log(1, "INFO", "TeaFabric", "Starting Minecraft 1.16.5 (Fabric, TeaVM Wasm GC)", null);
            if (Boot.config("vanillaMain") != null) {
                net.minecraft.client.main.Main.main(Boot.gameArguments());
            } else {
                WebLauncher.launch(Boot.username(), WebWindow.width(), WebWindow.height());
            }
            LogSink.log(2, "WARN", "TeaFabric", "WebMain.main finished", null);
        } catch (ExitError e) {
            LogSink.log(1, "INFO", "TeaFabric", "Game exited with status " + e.status, null);
        } catch (Throwable t) {
            LogSink.log(3, "FATAL", "TeaFabric", "Unhandled exception on the main thread", t);
            WebWindow.exit(-1);
        }
    }
}
