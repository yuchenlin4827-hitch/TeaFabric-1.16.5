package org.apache.logging.log4j;

import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.util.Supplier;

/**
 * Log4j 2 {@code Logger} API surface used by Minecraft, its libraries and Fabric mods.
 * Generated overload matrix; everything funnels into {@link #logMessage}.
 */
public interface Logger {
    String getName();

    boolean isEnabled(Level level);

    void logMessage(Level level, Marker marker, String formatted, Throwable t);

    default boolean isEnabled(Level level, Marker marker) {
        return isEnabled(level);
    }

    default boolean isTraceEnabled() {
        return isEnabled(Level.TRACE);
    }

    default boolean isDebugEnabled() {
        return isEnabled(Level.DEBUG);
    }

    default boolean isInfoEnabled() {
        return isEnabled(Level.INFO);
    }

    default boolean isWarnEnabled() {
        return isEnabled(Level.WARN);
    }

    default boolean isErrorEnabled() {
        return isEnabled(Level.ERROR);
    }

    default boolean isFatalEnabled() {
        return isEnabled(Level.FATAL);
    }

    default Level getLevel() {
        return LogManager.threshold();
    }

    /**
     * Exceptions logged at DEBUG are shown even when DEBUG is off: Minecraft reports swallowed failures
     * that way (e.g. the exception that makes it drop a connection), and here they are what one needs.
     */
    default boolean isShown(Level level, Throwable t) {
        return isEnabled(level) || (t != null && level == Level.DEBUG);
    }

    default void doLog(Level level, Marker marker, String format, Object[] params) {
        Throwable t = null;
        if (params != null && params.length > 0 && params[params.length - 1] instanceof Throwable
                && params.length > LogFormat.countPlaceholders(format)) {
            t = (Throwable) params[params.length - 1];
        }
        if (!isShown(level, t)) {
            return;
        }
        logMessage(level, marker, LogFormat.format(format, params), t);
    }

    default void doLogSuppliers(Level level, Marker marker, String format, Supplier<?>[] suppliers) {
        if (!isEnabled(level)) {
            return;
        }
        Object[] params = new Object[suppliers == null ? 0 : suppliers.length];
        for (int i = 0; i < params.length; i++) {
            params[i] = suppliers[i] == null ? null : suppliers[i].get();
        }
        doLog(level, marker, format, params);
    }

    default void log(Level level, String message) {
        doLog(level, null, message, null);
    }

    default void log(Level level, String message, Throwable t) {
        if (isShown(level, t)) {
            logMessage(level, null, message, t);
        }
    }

    default void log(Level level, String message, Object... params) {
        doLog(level, null, message, params);
    }

    default void log(Level level, String message, Object p0) {
        doLog(level, null, message, new Object[] {p0});
    }

    default void log(Level level, String message, Object p0, Object p1) {
        doLog(level, null, message, new Object[] {p0, p1});
    }

    default void log(Level level, Object message) {
        doLog(level, null, String.valueOf(message), null);
    }

    default void log(Level level, Object message, Throwable t) {
        if (isEnabled(level)) {
            logMessage(level, null, String.valueOf(message), t);
        }
    }

    default void log(Level level, Marker marker, String message) {
        doLog(level, marker, message, null);
    }

    default void log(Level level, Marker marker, String message, Object... params) {
        doLog(level, marker, message, params);
    }

    default void log(Level level, Marker marker, String message, Throwable t) {
        if (isEnabled(level)) {
            logMessage(level, marker, message, t);
        }
    }

    default void log(Level level, Message message) {
        if (isEnabled(level)) {
            logMessage(level, null, message.getFormattedMessage(), message.getThrowable());
        }
    }

    default void log(Level level, Supplier<?> supplier) {
        if (isEnabled(level)) {
            logMessage(level, null, String.valueOf(supplier.get()), null);
        }
    }

    default void catching(Throwable t) {
        log(Level.ERROR, "Catching", t);
    }

    default void catching(Level level, Throwable t) {
        log(level, "Catching", t);
    }

    default <T extends Throwable> T throwing(T t) {
        log(Level.ERROR, "Throwing", t);
        return t;
    }

    default void entry() {
    }

    default void traceEntry() {
    }

    default void exit() {
    }

    default void printf(Level level, String format, Object... params) {
        if (isEnabled(level)) {
            logMessage(level, null, String.format(format, params), null);
        }
    }
    default void trace(String message) {
        doLog(Level.TRACE, null, message, null);
    }
    default void trace(Object message) {
        doLog(Level.TRACE, null, String.valueOf(message), null);
    }
    default void trace(String message, Throwable t) {
        if (isEnabled(Level.TRACE)) { logMessage(Level.TRACE, null, message, t); }
    }
    default void trace(Object message, Throwable t) {
        if (isEnabled(Level.TRACE)) { logMessage(Level.TRACE, null, String.valueOf(message), t); }
    }
    default void trace(String message, Object... params) {
        doLog(Level.TRACE, null, message, params);
    }
    default void trace(String message, Supplier<?>... params) {
        doLogSuppliers(Level.TRACE, null, message, params);
    }
    default void trace(Supplier<?> supplier) {
        if (isEnabled(Level.TRACE)) { logMessage(Level.TRACE, null, String.valueOf(supplier.get()), null); }
    }
    default void trace(Supplier<?> supplier, Throwable t) {
        if (isEnabled(Level.TRACE)) { logMessage(Level.TRACE, null, String.valueOf(supplier.get()), t); }
    }
    default void trace(Message message) {
        if (isEnabled(Level.TRACE)) { logMessage(Level.TRACE, null, message.getFormattedMessage(), message.getThrowable()); }
    }
    default void trace(Message message, Throwable t) {
        if (isEnabled(Level.TRACE)) { logMessage(Level.TRACE, null, message.getFormattedMessage(), t); }
    }
    default void trace(Marker marker, String message) {
        doLog(Level.TRACE, marker, message, null);
    }
    default void trace(Marker marker, Object message) {
        doLog(Level.TRACE, marker, String.valueOf(message), null);
    }
    default void trace(Marker marker, String message, Throwable t) {
        if (isEnabled(Level.TRACE)) { logMessage(Level.TRACE, marker, message, t); }
    }
    default void trace(Marker marker, String message, Object... params) {
        doLog(Level.TRACE, marker, message, params);
    }
    default void trace(Marker marker, String message, Supplier<?>... params) {
        doLogSuppliers(Level.TRACE, marker, message, params);
    }
    default void trace(String message, Object p0) {
        doLog(Level.TRACE, null, message, new Object[] {p0});
    }
    default void trace(Marker marker, String message, Object p0) {
        doLog(Level.TRACE, marker, message, new Object[] {p0});
    }
    default void trace(String message, Object p0, Object p1) {
        doLog(Level.TRACE, null, message, new Object[] {p0, p1});
    }
    default void trace(Marker marker, String message, Object p0, Object p1) {
        doLog(Level.TRACE, marker, message, new Object[] {p0, p1});
    }
    default void trace(String message, Object p0, Object p1, Object p2) {
        doLog(Level.TRACE, null, message, new Object[] {p0, p1, p2});
    }
    default void trace(Marker marker, String message, Object p0, Object p1, Object p2) {
        doLog(Level.TRACE, marker, message, new Object[] {p0, p1, p2});
    }
    default void trace(String message, Object p0, Object p1, Object p2, Object p3) {
        doLog(Level.TRACE, null, message, new Object[] {p0, p1, p2, p3});
    }
    default void trace(Marker marker, String message, Object p0, Object p1, Object p2, Object p3) {
        doLog(Level.TRACE, marker, message, new Object[] {p0, p1, p2, p3});
    }
    default void trace(String message, Object p0, Object p1, Object p2, Object p3, Object p4) {
        doLog(Level.TRACE, null, message, new Object[] {p0, p1, p2, p3, p4});
    }
    default void trace(Marker marker, String message, Object p0, Object p1, Object p2, Object p3, Object p4) {
        doLog(Level.TRACE, marker, message, new Object[] {p0, p1, p2, p3, p4});
    }
    default void trace(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5) {
        doLog(Level.TRACE, null, message, new Object[] {p0, p1, p2, p3, p4, p5});
    }
    default void trace(Marker marker, String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5) {
        doLog(Level.TRACE, marker, message, new Object[] {p0, p1, p2, p3, p4, p5});
    }
    default void trace(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6) {
        doLog(Level.TRACE, null, message, new Object[] {p0, p1, p2, p3, p4, p5, p6});
    }
    default void trace(Marker marker, String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6) {
        doLog(Level.TRACE, marker, message, new Object[] {p0, p1, p2, p3, p4, p5, p6});
    }
    default void trace(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7) {
        doLog(Level.TRACE, null, message, new Object[] {p0, p1, p2, p3, p4, p5, p6, p7});
    }
    default void trace(Marker marker, String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7) {
        doLog(Level.TRACE, marker, message, new Object[] {p0, p1, p2, p3, p4, p5, p6, p7});
    }
    default void trace(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7, Object p8) {
        doLog(Level.TRACE, null, message, new Object[] {p0, p1, p2, p3, p4, p5, p6, p7, p8});
    }
    default void trace(Marker marker, String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7, Object p8) {
        doLog(Level.TRACE, marker, message, new Object[] {p0, p1, p2, p3, p4, p5, p6, p7, p8});
    }
    default void trace(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7, Object p8, Object p9) {
        doLog(Level.TRACE, null, message, new Object[] {p0, p1, p2, p3, p4, p5, p6, p7, p8, p9});
    }
    default void trace(Marker marker, String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7, Object p8, Object p9) {
        doLog(Level.TRACE, marker, message, new Object[] {p0, p1, p2, p3, p4, p5, p6, p7, p8, p9});
    }
    default void debug(String message) {
        doLog(Level.DEBUG, null, message, null);
    }
    default void debug(Object message) {
        doLog(Level.DEBUG, null, String.valueOf(message), null);
    }
    default void debug(String message, Throwable t) {
        if (isShown(Level.DEBUG, t)) { logMessage(Level.DEBUG, null, message, t); }
    }
    default void debug(Object message, Throwable t) {
        if (isShown(Level.DEBUG, t)) { logMessage(Level.DEBUG, null, String.valueOf(message), t); }
    }
    default void debug(String message, Object... params) {
        doLog(Level.DEBUG, null, message, params);
    }
    default void debug(String message, Supplier<?>... params) {
        doLogSuppliers(Level.DEBUG, null, message, params);
    }
    default void debug(Supplier<?> supplier) {
        if (isEnabled(Level.DEBUG)) { logMessage(Level.DEBUG, null, String.valueOf(supplier.get()), null); }
    }
    default void debug(Supplier<?> supplier, Throwable t) {
        if (isShown(Level.DEBUG, t)) { logMessage(Level.DEBUG, null, String.valueOf(supplier.get()), t); }
    }
    default void debug(Message message) {
        if (isEnabled(Level.DEBUG)) { logMessage(Level.DEBUG, null, message.getFormattedMessage(), message.getThrowable()); }
    }
    default void debug(Message message, Throwable t) {
        if (isShown(Level.DEBUG, t)) { logMessage(Level.DEBUG, null, message.getFormattedMessage(), t); }
    }
    default void debug(Marker marker, String message) {
        doLog(Level.DEBUG, marker, message, null);
    }
    default void debug(Marker marker, Object message) {
        doLog(Level.DEBUG, marker, String.valueOf(message), null);
    }
    default void debug(Marker marker, String message, Throwable t) {
        if (isShown(Level.DEBUG, t)) { logMessage(Level.DEBUG, marker, message, t); }
    }
    default void debug(Marker marker, String message, Object... params) {
        doLog(Level.DEBUG, marker, message, params);
    }
    default void debug(Marker marker, String message, Supplier<?>... params) {
        doLogSuppliers(Level.DEBUG, marker, message, params);
    }
    default void debug(String message, Object p0) {
        doLog(Level.DEBUG, null, message, new Object[] {p0});
    }
    default void debug(Marker marker, String message, Object p0) {
        doLog(Level.DEBUG, marker, message, new Object[] {p0});
    }
    default void debug(String message, Object p0, Object p1) {
        doLog(Level.DEBUG, null, message, new Object[] {p0, p1});
    }
    default void debug(Marker marker, String message, Object p0, Object p1) {
        doLog(Level.DEBUG, marker, message, new Object[] {p0, p1});
    }
    default void debug(String message, Object p0, Object p1, Object p2) {
        doLog(Level.DEBUG, null, message, new Object[] {p0, p1, p2});
    }
    default void debug(Marker marker, String message, Object p0, Object p1, Object p2) {
        doLog(Level.DEBUG, marker, message, new Object[] {p0, p1, p2});
    }
    default void debug(String message, Object p0, Object p1, Object p2, Object p3) {
        doLog(Level.DEBUG, null, message, new Object[] {p0, p1, p2, p3});
    }
    default void debug(Marker marker, String message, Object p0, Object p1, Object p2, Object p3) {
        doLog(Level.DEBUG, marker, message, new Object[] {p0, p1, p2, p3});
    }
    default void debug(String message, Object p0, Object p1, Object p2, Object p3, Object p4) {
        doLog(Level.DEBUG, null, message, new Object[] {p0, p1, p2, p3, p4});
    }
    default void debug(Marker marker, String message, Object p0, Object p1, Object p2, Object p3, Object p4) {
        doLog(Level.DEBUG, marker, message, new Object[] {p0, p1, p2, p3, p4});
    }
    default void debug(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5) {
        doLog(Level.DEBUG, null, message, new Object[] {p0, p1, p2, p3, p4, p5});
    }
    default void debug(Marker marker, String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5) {
        doLog(Level.DEBUG, marker, message, new Object[] {p0, p1, p2, p3, p4, p5});
    }
    default void debug(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6) {
        doLog(Level.DEBUG, null, message, new Object[] {p0, p1, p2, p3, p4, p5, p6});
    }
    default void debug(Marker marker, String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6) {
        doLog(Level.DEBUG, marker, message, new Object[] {p0, p1, p2, p3, p4, p5, p6});
    }
    default void debug(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7) {
        doLog(Level.DEBUG, null, message, new Object[] {p0, p1, p2, p3, p4, p5, p6, p7});
    }
    default void debug(Marker marker, String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7) {
        doLog(Level.DEBUG, marker, message, new Object[] {p0, p1, p2, p3, p4, p5, p6, p7});
    }
    default void debug(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7, Object p8) {
        doLog(Level.DEBUG, null, message, new Object[] {p0, p1, p2, p3, p4, p5, p6, p7, p8});
    }
    default void debug(Marker marker, String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7, Object p8) {
        doLog(Level.DEBUG, marker, message, new Object[] {p0, p1, p2, p3, p4, p5, p6, p7, p8});
    }
    default void debug(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7, Object p8, Object p9) {
        doLog(Level.DEBUG, null, message, new Object[] {p0, p1, p2, p3, p4, p5, p6, p7, p8, p9});
    }
    default void debug(Marker marker, String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7, Object p8, Object p9) {
        doLog(Level.DEBUG, marker, message, new Object[] {p0, p1, p2, p3, p4, p5, p6, p7, p8, p9});
    }
    default void info(String message) {
        doLog(Level.INFO, null, message, null);
    }
    default void info(Object message) {
        doLog(Level.INFO, null, String.valueOf(message), null);
    }
    default void info(String message, Throwable t) {
        if (isEnabled(Level.INFO)) { logMessage(Level.INFO, null, message, t); }
    }
    default void info(Object message, Throwable t) {
        if (isEnabled(Level.INFO)) { logMessage(Level.INFO, null, String.valueOf(message), t); }
    }
    default void info(String message, Object... params) {
        doLog(Level.INFO, null, message, params);
    }
    default void info(String message, Supplier<?>... params) {
        doLogSuppliers(Level.INFO, null, message, params);
    }
    default void info(Supplier<?> supplier) {
        if (isEnabled(Level.INFO)) { logMessage(Level.INFO, null, String.valueOf(supplier.get()), null); }
    }
    default void info(Supplier<?> supplier, Throwable t) {
        if (isEnabled(Level.INFO)) { logMessage(Level.INFO, null, String.valueOf(supplier.get()), t); }
    }
    default void info(Message message) {
        if (isEnabled(Level.INFO)) { logMessage(Level.INFO, null, message.getFormattedMessage(), message.getThrowable()); }
    }
    default void info(Message message, Throwable t) {
        if (isEnabled(Level.INFO)) { logMessage(Level.INFO, null, message.getFormattedMessage(), t); }
    }
    default void info(Marker marker, String message) {
        doLog(Level.INFO, marker, message, null);
    }
    default void info(Marker marker, Object message) {
        doLog(Level.INFO, marker, String.valueOf(message), null);
    }
    default void info(Marker marker, String message, Throwable t) {
        if (isEnabled(Level.INFO)) { logMessage(Level.INFO, marker, message, t); }
    }
    default void info(Marker marker, String message, Object... params) {
        doLog(Level.INFO, marker, message, params);
    }
    default void info(Marker marker, String message, Supplier<?>... params) {
        doLogSuppliers(Level.INFO, marker, message, params);
    }
    default void info(String message, Object p0) {
        doLog(Level.INFO, null, message, new Object[] {p0});
    }
    default void info(Marker marker, String message, Object p0) {
        doLog(Level.INFO, marker, message, new Object[] {p0});
    }
    default void info(String message, Object p0, Object p1) {
        doLog(Level.INFO, null, message, new Object[] {p0, p1});
    }
    default void info(Marker marker, String message, Object p0, Object p1) {
        doLog(Level.INFO, marker, message, new Object[] {p0, p1});
    }
    default void info(String message, Object p0, Object p1, Object p2) {
        doLog(Level.INFO, null, message, new Object[] {p0, p1, p2});
    }
    default void info(Marker marker, String message, Object p0, Object p1, Object p2) {
        doLog(Level.INFO, marker, message, new Object[] {p0, p1, p2});
    }
    default void info(String message, Object p0, Object p1, Object p2, Object p3) {
        doLog(Level.INFO, null, message, new Object[] {p0, p1, p2, p3});
    }
    default void info(Marker marker, String message, Object p0, Object p1, Object p2, Object p3) {
        doLog(Level.INFO, marker, message, new Object[] {p0, p1, p2, p3});
    }
    default void info(String message, Object p0, Object p1, Object p2, Object p3, Object p4) {
        doLog(Level.INFO, null, message, new Object[] {p0, p1, p2, p3, p4});
    }
    default void info(Marker marker, String message, Object p0, Object p1, Object p2, Object p3, Object p4) {
        doLog(Level.INFO, marker, message, new Object[] {p0, p1, p2, p3, p4});
    }
    default void info(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5) {
        doLog(Level.INFO, null, message, new Object[] {p0, p1, p2, p3, p4, p5});
    }
    default void info(Marker marker, String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5) {
        doLog(Level.INFO, marker, message, new Object[] {p0, p1, p2, p3, p4, p5});
    }
    default void info(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6) {
        doLog(Level.INFO, null, message, new Object[] {p0, p1, p2, p3, p4, p5, p6});
    }
    default void info(Marker marker, String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6) {
        doLog(Level.INFO, marker, message, new Object[] {p0, p1, p2, p3, p4, p5, p6});
    }
    default void info(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7) {
        doLog(Level.INFO, null, message, new Object[] {p0, p1, p2, p3, p4, p5, p6, p7});
    }
    default void info(Marker marker, String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7) {
        doLog(Level.INFO, marker, message, new Object[] {p0, p1, p2, p3, p4, p5, p6, p7});
    }
    default void info(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7, Object p8) {
        doLog(Level.INFO, null, message, new Object[] {p0, p1, p2, p3, p4, p5, p6, p7, p8});
    }
    default void info(Marker marker, String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7, Object p8) {
        doLog(Level.INFO, marker, message, new Object[] {p0, p1, p2, p3, p4, p5, p6, p7, p8});
    }
    default void info(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7, Object p8, Object p9) {
        doLog(Level.INFO, null, message, new Object[] {p0, p1, p2, p3, p4, p5, p6, p7, p8, p9});
    }
    default void info(Marker marker, String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7, Object p8, Object p9) {
        doLog(Level.INFO, marker, message, new Object[] {p0, p1, p2, p3, p4, p5, p6, p7, p8, p9});
    }
    default void warn(String message) {
        doLog(Level.WARN, null, message, null);
    }
    default void warn(Object message) {
        doLog(Level.WARN, null, String.valueOf(message), null);
    }
    default void warn(String message, Throwable t) {
        if (isEnabled(Level.WARN)) { logMessage(Level.WARN, null, message, t); }
    }
    default void warn(Object message, Throwable t) {
        if (isEnabled(Level.WARN)) { logMessage(Level.WARN, null, String.valueOf(message), t); }
    }
    default void warn(String message, Object... params) {
        doLog(Level.WARN, null, message, params);
    }
    default void warn(String message, Supplier<?>... params) {
        doLogSuppliers(Level.WARN, null, message, params);
    }
    default void warn(Supplier<?> supplier) {
        if (isEnabled(Level.WARN)) { logMessage(Level.WARN, null, String.valueOf(supplier.get()), null); }
    }
    default void warn(Supplier<?> supplier, Throwable t) {
        if (isEnabled(Level.WARN)) { logMessage(Level.WARN, null, String.valueOf(supplier.get()), t); }
    }
    default void warn(Message message) {
        if (isEnabled(Level.WARN)) { logMessage(Level.WARN, null, message.getFormattedMessage(), message.getThrowable()); }
    }
    default void warn(Message message, Throwable t) {
        if (isEnabled(Level.WARN)) { logMessage(Level.WARN, null, message.getFormattedMessage(), t); }
    }
    default void warn(Marker marker, String message) {
        doLog(Level.WARN, marker, message, null);
    }
    default void warn(Marker marker, Object message) {
        doLog(Level.WARN, marker, String.valueOf(message), null);
    }
    default void warn(Marker marker, String message, Throwable t) {
        if (isEnabled(Level.WARN)) { logMessage(Level.WARN, marker, message, t); }
    }
    default void warn(Marker marker, String message, Object... params) {
        doLog(Level.WARN, marker, message, params);
    }
    default void warn(Marker marker, String message, Supplier<?>... params) {
        doLogSuppliers(Level.WARN, marker, message, params);
    }
    default void warn(String message, Object p0) {
        doLog(Level.WARN, null, message, new Object[] {p0});
    }
    default void warn(Marker marker, String message, Object p0) {
        doLog(Level.WARN, marker, message, new Object[] {p0});
    }
    default void warn(String message, Object p0, Object p1) {
        doLog(Level.WARN, null, message, new Object[] {p0, p1});
    }
    default void warn(Marker marker, String message, Object p0, Object p1) {
        doLog(Level.WARN, marker, message, new Object[] {p0, p1});
    }
    default void warn(String message, Object p0, Object p1, Object p2) {
        doLog(Level.WARN, null, message, new Object[] {p0, p1, p2});
    }
    default void warn(Marker marker, String message, Object p0, Object p1, Object p2) {
        doLog(Level.WARN, marker, message, new Object[] {p0, p1, p2});
    }
    default void warn(String message, Object p0, Object p1, Object p2, Object p3) {
        doLog(Level.WARN, null, message, new Object[] {p0, p1, p2, p3});
    }
    default void warn(Marker marker, String message, Object p0, Object p1, Object p2, Object p3) {
        doLog(Level.WARN, marker, message, new Object[] {p0, p1, p2, p3});
    }
    default void warn(String message, Object p0, Object p1, Object p2, Object p3, Object p4) {
        doLog(Level.WARN, null, message, new Object[] {p0, p1, p2, p3, p4});
    }
    default void warn(Marker marker, String message, Object p0, Object p1, Object p2, Object p3, Object p4) {
        doLog(Level.WARN, marker, message, new Object[] {p0, p1, p2, p3, p4});
    }
    default void warn(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5) {
        doLog(Level.WARN, null, message, new Object[] {p0, p1, p2, p3, p4, p5});
    }
    default void warn(Marker marker, String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5) {
        doLog(Level.WARN, marker, message, new Object[] {p0, p1, p2, p3, p4, p5});
    }
    default void warn(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6) {
        doLog(Level.WARN, null, message, new Object[] {p0, p1, p2, p3, p4, p5, p6});
    }
    default void warn(Marker marker, String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6) {
        doLog(Level.WARN, marker, message, new Object[] {p0, p1, p2, p3, p4, p5, p6});
    }
    default void warn(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7) {
        doLog(Level.WARN, null, message, new Object[] {p0, p1, p2, p3, p4, p5, p6, p7});
    }
    default void warn(Marker marker, String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7) {
        doLog(Level.WARN, marker, message, new Object[] {p0, p1, p2, p3, p4, p5, p6, p7});
    }
    default void warn(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7, Object p8) {
        doLog(Level.WARN, null, message, new Object[] {p0, p1, p2, p3, p4, p5, p6, p7, p8});
    }
    default void warn(Marker marker, String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7, Object p8) {
        doLog(Level.WARN, marker, message, new Object[] {p0, p1, p2, p3, p4, p5, p6, p7, p8});
    }
    default void warn(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7, Object p8, Object p9) {
        doLog(Level.WARN, null, message, new Object[] {p0, p1, p2, p3, p4, p5, p6, p7, p8, p9});
    }
    default void warn(Marker marker, String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7, Object p8, Object p9) {
        doLog(Level.WARN, marker, message, new Object[] {p0, p1, p2, p3, p4, p5, p6, p7, p8, p9});
    }
    default void error(String message) {
        doLog(Level.ERROR, null, message, null);
    }
    default void error(Object message) {
        doLog(Level.ERROR, null, String.valueOf(message), null);
    }
    default void error(String message, Throwable t) {
        if (isEnabled(Level.ERROR)) { logMessage(Level.ERROR, null, message, t); }
    }
    default void error(Object message, Throwable t) {
        if (isEnabled(Level.ERROR)) { logMessage(Level.ERROR, null, String.valueOf(message), t); }
    }
    default void error(String message, Object... params) {
        doLog(Level.ERROR, null, message, params);
    }
    default void error(String message, Supplier<?>... params) {
        doLogSuppliers(Level.ERROR, null, message, params);
    }
    default void error(Supplier<?> supplier) {
        if (isEnabled(Level.ERROR)) { logMessage(Level.ERROR, null, String.valueOf(supplier.get()), null); }
    }
    default void error(Supplier<?> supplier, Throwable t) {
        if (isEnabled(Level.ERROR)) { logMessage(Level.ERROR, null, String.valueOf(supplier.get()), t); }
    }
    default void error(Message message) {
        if (isEnabled(Level.ERROR)) { logMessage(Level.ERROR, null, message.getFormattedMessage(), message.getThrowable()); }
    }
    default void error(Message message, Throwable t) {
        if (isEnabled(Level.ERROR)) { logMessage(Level.ERROR, null, message.getFormattedMessage(), t); }
    }
    default void error(Marker marker, String message) {
        doLog(Level.ERROR, marker, message, null);
    }
    default void error(Marker marker, Object message) {
        doLog(Level.ERROR, marker, String.valueOf(message), null);
    }
    default void error(Marker marker, String message, Throwable t) {
        if (isEnabled(Level.ERROR)) { logMessage(Level.ERROR, marker, message, t); }
    }
    default void error(Marker marker, String message, Object... params) {
        doLog(Level.ERROR, marker, message, params);
    }
    default void error(Marker marker, String message, Supplier<?>... params) {
        doLogSuppliers(Level.ERROR, marker, message, params);
    }
    default void error(String message, Object p0) {
        doLog(Level.ERROR, null, message, new Object[] {p0});
    }
    default void error(Marker marker, String message, Object p0) {
        doLog(Level.ERROR, marker, message, new Object[] {p0});
    }
    default void error(String message, Object p0, Object p1) {
        doLog(Level.ERROR, null, message, new Object[] {p0, p1});
    }
    default void error(Marker marker, String message, Object p0, Object p1) {
        doLog(Level.ERROR, marker, message, new Object[] {p0, p1});
    }
    default void error(String message, Object p0, Object p1, Object p2) {
        doLog(Level.ERROR, null, message, new Object[] {p0, p1, p2});
    }
    default void error(Marker marker, String message, Object p0, Object p1, Object p2) {
        doLog(Level.ERROR, marker, message, new Object[] {p0, p1, p2});
    }
    default void error(String message, Object p0, Object p1, Object p2, Object p3) {
        doLog(Level.ERROR, null, message, new Object[] {p0, p1, p2, p3});
    }
    default void error(Marker marker, String message, Object p0, Object p1, Object p2, Object p3) {
        doLog(Level.ERROR, marker, message, new Object[] {p0, p1, p2, p3});
    }
    default void error(String message, Object p0, Object p1, Object p2, Object p3, Object p4) {
        doLog(Level.ERROR, null, message, new Object[] {p0, p1, p2, p3, p4});
    }
    default void error(Marker marker, String message, Object p0, Object p1, Object p2, Object p3, Object p4) {
        doLog(Level.ERROR, marker, message, new Object[] {p0, p1, p2, p3, p4});
    }
    default void error(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5) {
        doLog(Level.ERROR, null, message, new Object[] {p0, p1, p2, p3, p4, p5});
    }
    default void error(Marker marker, String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5) {
        doLog(Level.ERROR, marker, message, new Object[] {p0, p1, p2, p3, p4, p5});
    }
    default void error(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6) {
        doLog(Level.ERROR, null, message, new Object[] {p0, p1, p2, p3, p4, p5, p6});
    }
    default void error(Marker marker, String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6) {
        doLog(Level.ERROR, marker, message, new Object[] {p0, p1, p2, p3, p4, p5, p6});
    }
    default void error(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7) {
        doLog(Level.ERROR, null, message, new Object[] {p0, p1, p2, p3, p4, p5, p6, p7});
    }
    default void error(Marker marker, String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7) {
        doLog(Level.ERROR, marker, message, new Object[] {p0, p1, p2, p3, p4, p5, p6, p7});
    }
    default void error(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7, Object p8) {
        doLog(Level.ERROR, null, message, new Object[] {p0, p1, p2, p3, p4, p5, p6, p7, p8});
    }
    default void error(Marker marker, String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7, Object p8) {
        doLog(Level.ERROR, marker, message, new Object[] {p0, p1, p2, p3, p4, p5, p6, p7, p8});
    }
    default void error(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7, Object p8, Object p9) {
        doLog(Level.ERROR, null, message, new Object[] {p0, p1, p2, p3, p4, p5, p6, p7, p8, p9});
    }
    default void error(Marker marker, String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7, Object p8, Object p9) {
        doLog(Level.ERROR, marker, message, new Object[] {p0, p1, p2, p3, p4, p5, p6, p7, p8, p9});
    }
    default void fatal(String message) {
        doLog(Level.FATAL, null, message, null);
    }
    default void fatal(Object message) {
        doLog(Level.FATAL, null, String.valueOf(message), null);
    }
    default void fatal(String message, Throwable t) {
        if (isEnabled(Level.FATAL)) { logMessage(Level.FATAL, null, message, t); }
    }
    default void fatal(Object message, Throwable t) {
        if (isEnabled(Level.FATAL)) { logMessage(Level.FATAL, null, String.valueOf(message), t); }
    }
    default void fatal(String message, Object... params) {
        doLog(Level.FATAL, null, message, params);
    }
    default void fatal(String message, Supplier<?>... params) {
        doLogSuppliers(Level.FATAL, null, message, params);
    }
    default void fatal(Supplier<?> supplier) {
        if (isEnabled(Level.FATAL)) { logMessage(Level.FATAL, null, String.valueOf(supplier.get()), null); }
    }
    default void fatal(Supplier<?> supplier, Throwable t) {
        if (isEnabled(Level.FATAL)) { logMessage(Level.FATAL, null, String.valueOf(supplier.get()), t); }
    }
    default void fatal(Message message) {
        if (isEnabled(Level.FATAL)) { logMessage(Level.FATAL, null, message.getFormattedMessage(), message.getThrowable()); }
    }
    default void fatal(Message message, Throwable t) {
        if (isEnabled(Level.FATAL)) { logMessage(Level.FATAL, null, message.getFormattedMessage(), t); }
    }
    default void fatal(Marker marker, String message) {
        doLog(Level.FATAL, marker, message, null);
    }
    default void fatal(Marker marker, Object message) {
        doLog(Level.FATAL, marker, String.valueOf(message), null);
    }
    default void fatal(Marker marker, String message, Throwable t) {
        if (isEnabled(Level.FATAL)) { logMessage(Level.FATAL, marker, message, t); }
    }
    default void fatal(Marker marker, String message, Object... params) {
        doLog(Level.FATAL, marker, message, params);
    }
    default void fatal(Marker marker, String message, Supplier<?>... params) {
        doLogSuppliers(Level.FATAL, marker, message, params);
    }
    default void fatal(String message, Object p0) {
        doLog(Level.FATAL, null, message, new Object[] {p0});
    }
    default void fatal(Marker marker, String message, Object p0) {
        doLog(Level.FATAL, marker, message, new Object[] {p0});
    }
    default void fatal(String message, Object p0, Object p1) {
        doLog(Level.FATAL, null, message, new Object[] {p0, p1});
    }
    default void fatal(Marker marker, String message, Object p0, Object p1) {
        doLog(Level.FATAL, marker, message, new Object[] {p0, p1});
    }
    default void fatal(String message, Object p0, Object p1, Object p2) {
        doLog(Level.FATAL, null, message, new Object[] {p0, p1, p2});
    }
    default void fatal(Marker marker, String message, Object p0, Object p1, Object p2) {
        doLog(Level.FATAL, marker, message, new Object[] {p0, p1, p2});
    }
    default void fatal(String message, Object p0, Object p1, Object p2, Object p3) {
        doLog(Level.FATAL, null, message, new Object[] {p0, p1, p2, p3});
    }
    default void fatal(Marker marker, String message, Object p0, Object p1, Object p2, Object p3) {
        doLog(Level.FATAL, marker, message, new Object[] {p0, p1, p2, p3});
    }
    default void fatal(String message, Object p0, Object p1, Object p2, Object p3, Object p4) {
        doLog(Level.FATAL, null, message, new Object[] {p0, p1, p2, p3, p4});
    }
    default void fatal(Marker marker, String message, Object p0, Object p1, Object p2, Object p3, Object p4) {
        doLog(Level.FATAL, marker, message, new Object[] {p0, p1, p2, p3, p4});
    }
    default void fatal(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5) {
        doLog(Level.FATAL, null, message, new Object[] {p0, p1, p2, p3, p4, p5});
    }
    default void fatal(Marker marker, String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5) {
        doLog(Level.FATAL, marker, message, new Object[] {p0, p1, p2, p3, p4, p5});
    }
    default void fatal(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6) {
        doLog(Level.FATAL, null, message, new Object[] {p0, p1, p2, p3, p4, p5, p6});
    }
    default void fatal(Marker marker, String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6) {
        doLog(Level.FATAL, marker, message, new Object[] {p0, p1, p2, p3, p4, p5, p6});
    }
    default void fatal(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7) {
        doLog(Level.FATAL, null, message, new Object[] {p0, p1, p2, p3, p4, p5, p6, p7});
    }
    default void fatal(Marker marker, String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7) {
        doLog(Level.FATAL, marker, message, new Object[] {p0, p1, p2, p3, p4, p5, p6, p7});
    }
    default void fatal(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7, Object p8) {
        doLog(Level.FATAL, null, message, new Object[] {p0, p1, p2, p3, p4, p5, p6, p7, p8});
    }
    default void fatal(Marker marker, String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7, Object p8) {
        doLog(Level.FATAL, marker, message, new Object[] {p0, p1, p2, p3, p4, p5, p6, p7, p8});
    }
    default void fatal(String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7, Object p8, Object p9) {
        doLog(Level.FATAL, null, message, new Object[] {p0, p1, p2, p3, p4, p5, p6, p7, p8, p9});
    }
    default void fatal(Marker marker, String message, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7, Object p8, Object p9) {
        doLog(Level.FATAL, marker, message, new Object[] {p0, p1, p2, p3, p4, p5, p6, p7, p8, p9});
    }
}
