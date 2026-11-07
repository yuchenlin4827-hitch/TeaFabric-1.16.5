package org.lwjgl.util.tinyfd;

import org.lwjgl.PointerBuffer;
import org.teavm.jso.JSBody;

/** Native dialogs map to the browser's alert/confirm; file pickers are not available synchronously. */
public final class TinyFileDialogs {
    private TinyFileDialogs() {
    }

    @JSBody(params = {"title", "message", "confirm"}, script = "if (confirm) return window.confirm(title + '\\n\\n' "
            + "+ message); window.alert(title + '\\n\\n' + message); return true;")
    private static native boolean dialog(String title, String message, boolean confirm);

    public static boolean tinyfd_messageBox(CharSequence title, CharSequence message, CharSequence dialogType,
            CharSequence iconType, boolean defaultButton) {
        String type = dialogType == null ? "ok" : dialogType.toString();
        return dialog(String.valueOf(title), String.valueOf(message), !type.equals("ok"));
    }

    public static String tinyfd_openFileDialog(CharSequence title, CharSequence defaultPath, PointerBuffer filters,
            CharSequence filterDescription, boolean allowMultiple) {
        return null;
    }
}
