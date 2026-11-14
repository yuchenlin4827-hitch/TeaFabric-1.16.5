package teafabric.web.compat;

import net.minecraft.class_5365;

/**
 * Graphics modes in the browser: Fast and Fancy only. Fabulous! renders translucency through a chain of
 * post-processing framebuffers that the WebGL emulation does not support (worlds turn black), and it is
 * too heavy for the devices people play this on anyway. The Graphics button cycles Fast/Fancy, and
 * Fabulous saved in options.txt loads as Fancy.
 */
public final class GraphicsCompat {
    private GraphicsCompat() {
    }

    /** GraphicsMode.next() (redirect target). */
    public static class_5365 method_29595(class_5365 mode) {
        return mode == class_5365.field_25427 ? class_5365.field_25428 : class_5365.field_25427;
    }

    /** GraphicsMode.byId(int) (redirect target). */
    public static class_5365 method_29592(int id) {
        class_5365 mode = class_5365.method_29592(id);
        return mode == class_5365.field_25429 ? class_5365.field_25428 : mode;
    }
}
