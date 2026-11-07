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

    /** Util.startTimerHack: a Windows timer-resolution workaround (a thread sleeping forever). */
    public static void method_29476() {
    }

    public static DataFixer build(DataFixerBuilder builder, Executor executor) {
        return builder.build(task -> { });
    }
}
