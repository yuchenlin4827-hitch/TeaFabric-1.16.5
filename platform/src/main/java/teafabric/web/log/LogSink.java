package teafabric.web.log;

import java.io.PrintWriter;
import java.io.StringWriter;
import org.teavm.jso.JSBody;

/** Single sink for all logging in the browser build: the JS console plus an optional page overlay. */
public final class LogSink {
    private LogSink() {
    }

    /**
     * @param severity 0 = debug/trace, 1 = info, 2 = warn, 3 = error/fatal
     */
    public static void log(int severity, String level, String logger, String message, Throwable t) {
        StringBuilder sb = new StringBuilder();
        sb.append('[').append(level).append("] [").append(logger).append("] ").append(message);
        if (t != null) {
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            sb.append('\n').append(sw);
        }
        emit(severity, sb.toString());
    }

    @JSBody(params = {"severity", "text"}, script = ""
            + "var c = globalThis.console;"
            + "if (severity >= 3) c.error(text); else if (severity == 2) c.warn(text);"
            + "else if (severity == 1) c.info(text); else c.debug(text);"
            + "if (globalThis.teafabricLog) globalThis.teafabricLog(severity, text);")
    private static native void emit(int severity, String text);
}
