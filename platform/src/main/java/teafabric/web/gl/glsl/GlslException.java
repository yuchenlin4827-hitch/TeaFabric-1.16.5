package teafabric.web.gl.glsl;

/** A shader the translator cannot handle; the caller falls back to passing it through. */
public final class GlslException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    GlslException(int line, String message) {
        super("line " + line + ": " + message);
    }
}
