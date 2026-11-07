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
    /** Util.startTimerHack: a Windows timer-resolution workaround (a thread sleeping forever). */
    public static void method_29476() {
    }

    public static DataFixer build(DataFixerBuilder builder, Executor executor) {
        return builder.build(task -> { });
    }
}
