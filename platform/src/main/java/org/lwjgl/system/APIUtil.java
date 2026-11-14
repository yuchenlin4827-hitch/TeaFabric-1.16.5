package org.lwjgl.system;

/** The one APIUtil helper mods call. */
public final class APIUtil {
    private APIUtil() {
    }

    /**
     * Frees an array of strings from MemoryUtil.memUTF8. Those are garbage-collected direct buffers here,
     * so there is nothing to free (and freeing them as native memory would be wrong).
     */
    public static void apiArrayFree(long pointers, int count) {
    }
}
