package teafabric.web.compat;

import java.lang.invoke.MethodHandle;
import org.teavm.classlib.java.lang.invoke.TMethodHandles$Lookup;

/** Redirect targets for java.lang.invoke (no run-time method handles in AOT-compiled Wasm). */
public final class InvokeCompat {
    private InvokeCompat() {
    }

    // The JDK Lookup type maps to TMethodHandles$Lookup when TeaVM compiles this class.
    public static java.lang.invoke.MethodHandles.Lookup lookup() {
        return (java.lang.invoke.MethodHandles.Lookup) (Object) TMethodHandles$Lookup.INSTANCE;
    }

    public static java.lang.invoke.MethodHandles.Lookup publicLookup() {
        return lookup();
    }

    public static boolean invokeExact(MethodHandle h) {
        throw new UnsupportedOperationException("MethodHandle.invokeExact");
    }

    public static void invoke(MethodHandle h, long arg) {
        throw new UnsupportedOperationException("MethodHandle.invoke");
    }
}
