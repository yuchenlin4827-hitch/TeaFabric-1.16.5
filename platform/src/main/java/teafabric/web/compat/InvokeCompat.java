package teafabric.web.compat;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Redirect targets for java.lang.invoke. Method handles cannot be created at run time in an
 * ahead-of-time compiled program: lookups behave as if the member did not exist, which is how
 * Minecraft's optional-feature probes (e.g. GLFW raw mouse motion in InputUtil) expect to fail.
 */
public final class InvokeCompat {
    private InvokeCompat() {
    }

    public static MethodHandles.Lookup lookup() {
        return null;
    }

    public static MethodHandles.Lookup publicLookup() {
        return null;
    }

    public static MethodType methodType(Class<?> rtype) {
        return null;
    }

    public static MethodType methodType(Class<?> rtype, Class<?> ptype0) {
        return null;
    }

    public static MethodType methodType(Class<?> rtype, Class<?>[] ptypes) {
        return null;
    }

    public static MethodHandle findStatic(MethodHandles.Lookup lookup, Class<?> refc, String name, MethodType type)
            throws NoSuchMethodException {
        throw new NoSuchMethodException(refc.getName() + "." + name);
    }

    public static MethodHandle findVirtual(MethodHandles.Lookup lookup, Class<?> refc, String name,
            MethodType type) throws NoSuchMethodException {
        throw new NoSuchMethodException(refc.getName() + "." + name);
    }

    public static MethodHandle findStaticGetter(MethodHandles.Lookup lookup, Class<?> refc, String name,
            Class<?> type) throws NoSuchFieldException {
        throw new NoSuchFieldException(refc.getName() + "." + name);
    }

    public static MethodHandle findGetter(MethodHandles.Lookup lookup, Class<?> refc, String name, Class<?> type)
            throws NoSuchFieldException {
        throw new NoSuchFieldException(refc.getName() + "." + name);
    }

    public static boolean invokeExact(MethodHandle h) {
        throw new UnsupportedOperationException("MethodHandle.invokeExact");
    }

    public static void invoke(MethodHandle h, long arg) {
        throw new UnsupportedOperationException("MethodHandle.invoke");
    }

    /**
     * ViaVersion's {@code PipelineUtil.privateHandleUnchecked}: looks codec methods up with getDeclaredMethod in
     * its static initialiser, which fails without reflection; the methods using the handles are redirected to
     * {@code io.netty.handler.codec.TeaFabricCodecs} instead.
     */
    public static MethodHandle privateHandleUnchecked(Class<?> owner, String name, Class<?>... parameterTypes) {
        return null;
    }
}
