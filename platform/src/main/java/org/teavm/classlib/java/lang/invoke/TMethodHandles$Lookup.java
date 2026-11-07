package org.teavm.classlib.java.lang.invoke;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;

/**
 * java.lang.invoke.MethodHandles.Lookup: method handles cannot be created at run time in an
 * ahead-of-time compiled program, so every lookup reports the member as missing. Callers in
 * Minecraft (optional GLFW features) handle that gracefully.
 */
public final class TMethodHandles$Lookup {
    public static final TMethodHandles$Lookup INSTANCE = new TMethodHandles$Lookup();

    private TMethodHandles$Lookup() {
    }

    public MethodHandle findStatic(Class<?> refc, String name, MethodType type) throws NoSuchMethodException {
        throw new NoSuchMethodException(refc.getName() + "." + name);
    }

    public MethodHandle findVirtual(Class<?> refc, String name, MethodType type) throws NoSuchMethodException {
        throw new NoSuchMethodException(refc.getName() + "." + name);
    }

    public MethodHandle findConstructor(Class<?> refc, MethodType type) throws NoSuchMethodException {
        throw new NoSuchMethodException(refc.getName() + ".<init>");
    }

    public MethodHandle findSpecial(Class<?> refc, String name, MethodType type, Class<?> caller)
            throws NoSuchMethodException {
        throw new NoSuchMethodException(refc.getName() + "." + name);
    }

    public MethodHandle findGetter(Class<?> refc, String name, Class<?> type) throws NoSuchFieldException {
        throw new NoSuchFieldException(refc.getName() + "." + name);
    }

    public MethodHandle findSetter(Class<?> refc, String name, Class<?> type) throws NoSuchFieldException {
        throw new NoSuchFieldException(refc.getName() + "." + name);
    }

    public MethodHandle findStaticGetter(Class<?> refc, String name, Class<?> type) throws NoSuchFieldException {
        throw new NoSuchFieldException(refc.getName() + "." + name);
    }

    public MethodHandle findStaticSetter(Class<?> refc, String name, Class<?> type) throws NoSuchFieldException {
        throw new NoSuchFieldException(refc.getName() + "." + name);
    }

    public Class<?> lookupClass() {
        return Object.class;
    }
}
