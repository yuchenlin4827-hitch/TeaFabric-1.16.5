package teafabric.web.compat;

import com.mojang.datafixers.DataFixer;
import com.mojang.datafixers.DataFixerBuilder;
import java.util.concurrent.Executor;

/** Redirect targets for Minecraft-library calls that are adapted for the browser. */
public final class MinecraftCompat {
    private MinecraftCompat() {
    }

    /**
     * DataFixerUpper eagerly optimises every type rewrite rule on worker threads at start-up (tens of
     * seconds of CPU on green threads, and it exits the process on failure). The fixer works without
     * the pre-optimisation: rules are then built lazily when old data is actually upgraded.
     */
    /**
     * GLX.make(Supplier): used for optional native probes (e.g. Untracker looks up LWJGL's debug
     * allocator via reflection). A failing probe yields null instead of aborting class init.
     */
    public static <T> T make(java.util.function.Supplier<T> factory) {
        try {
            return factory.get();
        } catch (RuntimeException e) {
            teafabric.web.log.LogSink.log(2, "WARN", "TeaFabric", "Optional native probe unavailable: " + e, null);
            return null;
        }
    }

    /**
     * Fabric Indigo's VanillaAoHelper.initialize(BlockModelRenderer) reflects on a private inner
     * class constructor; without run-time reflection Indigo uses its own AO calculator instead.
     */
    public static void initialize(net.minecraft.class_778 blockRenderer) {
    }

    /**
     * Minecraft's worker pools (Util.getMainWorkerExecutor / getIoWorkerExecutor /
     * getBootstrapExecutor). With {@code ?threads} the pools run on green threads; by default tasks
     * run inline on the submitting thread: a browser page has one CPU thread anyway, and inline
     * execution avoids thousands of fiber switches during resource reloads.
     */
    private static final java.util.concurrent.Executor INLINE = Runnable::run;

    /** The render thread (set by the launcher): the only thread whose worker tasks run inline. */
    public static volatile Thread renderThread;

    private static boolean pooled() {
        return teafabric.web.Boot.config("threads") != null;
    }

    /**
     * Inline on the render thread (resource reloads), queued for a worker green thread otherwise. The
     * integrated server submits world generation from its own thread: inline, one chunk's dependencies ran
     * each other recursively and the page froze for minutes; on the worker every task ends with a yield.
     */
    private static final java.util.concurrent.Executor MIXED = task -> {
        if (Thread.currentThread() == renderThread) {
            task.run();
        } else {
            YieldingWorker.INSTANCE.execute(task);
        }
    };

    /** One green thread running queued tasks, giving the browser a turn regularly (Thread.yield time-slices). */
    static final class YieldingWorker implements java.util.concurrent.Executor {
        static final YieldingWorker INSTANCE = new YieldingWorker();
        private final java.util.concurrent.LinkedBlockingQueue<Runnable> queue =
                new java.util.concurrent.LinkedBlockingQueue<>();
        private Thread thread;

        @Override
        public void execute(Runnable task) {
            queue.add(task);
            if (thread == null) {
                thread = new Thread(this::run, "Worker-Main");
                thread.setDaemon(true);
                thread.start();
            }
        }

        private void run() {
            while (true) {
                Runnable task;
                try {
                    task = queue.take();
                } catch (InterruptedException e) {
                    continue;
                }
                try {
                    task.run();
                } catch (Throwable t) {
                    teafabric.web.log.LogSink.log(3, "ERROR", "Worker", "Uncaught exception in worker task", t);
                }
                Thread.yield();
            }
        }
    }

    /**
     * {@code ThreadExecutor.runTasks(stopCondition)}: the integrated server runs queued tasks in this loop until
     * the condition holds (preparing the spawn area: until all its chunks exist). A yield per iteration,
     * time-sliced by TeaVM, keeps the page rendering meanwhile.
     */
    public static void method_18857(net.minecraft.class_1255<?> executor, java.util.function.BooleanSupplier stop) {
        if (Thread.currentThread() == renderThread) {
            executor.method_18857(stop);
            return;
        }
        executor.method_18857(() -> {
            Thread.yield();
            return stop.getAsBoolean();
        });
    }

    public static java.util.concurrent.Executor method_18349() {
        return pooled() ? net.minecraft.class_156.method_18349() : MIXED;
    }

    public static java.util.concurrent.Executor method_27958() {
        return pooled() ? net.minecraft.class_156.method_27958() : MIXED;
    }

    public static java.util.concurrent.Executor method_28124() {
        return pooled() ? net.minecraft.class_156.method_28124() : MIXED;
    }

    /**
     * {@code new MinecraftVersion(versionJson)}: parses the build date with java.time, which TeaVM lacks.
     * The built-in default describes the same release (1.16.5, world 2586, protocol 754, pack 6).
     */
    public static net.minecraft.class_3797 constructclass_3797(com.google.gson.JsonObject json) {
        return (net.minecraft.class_3797) net.minecraft.class_3797.field_25319;
    }

    /**
     * {@code NetworkEncryptionUtils.generateServerKeyPair()}: RSA is not available in the page. The integrated
     * server only needs the key pair for online-mode logins over the network; the client's own connection is
     * local and never encrypted, and "Open to LAN" is impossible in a browser.
     */
    public static java.security.KeyPair method_15237() {
        return null;
    }

    /** Util.startTimerHack: a Windows timer-resolution workaround (a thread sleeping forever). */
    public static void method_29476() {
    }

    public static DataFixer build(DataFixerBuilder builder, Executor executor) {
        return builder.build(task -> { });
    }

    /**
     * {@code new CrashException(report)}: logs the crash report right away. MinecraftClient.run() only
     * logs a CrashException after cleanUpAfterCrash(), which can block (sound thread join), so the
     * report would otherwise never be seen.
     */
    public static net.minecraft.class_148 constructclass_148(net.minecraft.class_128 report) {
        String text;
        try {
            text = report.method_568(); // CrashReport.asString()
        } catch (Throwable t) {
            text = String.valueOf(report.method_564()); // getCause()
        }
        teafabric.web.log.LogSink.log(3, "ERROR", "TeaFabric", "CrashException created:\n" + text,
                report.method_564());
        return new net.minecraft.class_148(report);
    }

    /**
     * {@code ParentElement.setInitialFocus(element)}. Vanilla toggles the element's focus, which
     * <em>unfocuses</em> text fields that were focused just before (Direct Connection, Add Server): the
     * player has to click the field before typing. Here the element always ends up focused.
     */
    public static void method_20085(net.minecraft.class_4069 parent, net.minecraft.class_364 element) {
        parent.method_25395(element); // setFocused
        if (element == null) {
            return;
        }
        boolean focused = element.method_25407(true); // changeFocus
        if (!focused && element instanceof net.minecraft.class_342) {
            ((net.minecraft.class_342) element).method_1876(true); // TextFieldWidget.setTextFieldFocused
        }
    }
}
