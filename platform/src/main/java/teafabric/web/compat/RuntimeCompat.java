package teafabric.web.compat;

import java.util.ArrayList;
import java.util.List;

/** Redirect targets for {@link Runtime} methods TeaVM's class library lacks. */
public final class RuntimeCompat {
    private static final List<Thread> HOOKS = new ArrayList<>();

    private RuntimeCompat() {
    }

    public static void addShutdownHook(Runtime runtime, Thread hook) {
        synchronized (HOOKS) {
            HOOKS.add(hook);
        }
    }

    public static boolean removeShutdownHook(Runtime runtime, Thread hook) {
        synchronized (HOOKS) {
            return HOOKS.remove(hook);
        }
    }

    /** Called from the page's unload handler. */
    public static void runShutdownHooks() {
        List<Thread> hooks;
        synchronized (HOOKS) {
            hooks = new ArrayList<>(HOOKS);
            HOOKS.clear();
        }
        for (Thread t : hooks) {
            try {
                t.run();
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
    }

    public static long maxMemory(Runtime runtime) {
        return 2048L * 1024 * 1024;
    }

    public static void halt(Runtime runtime, int status) {
        runtime.exit(status);
    }
}
