package org.apache.logging.log4j;

import java.util.HashMap;
import java.util.Map;
import org.apache.logging.log4j.spi.LoggerContext;

/**
 * Browser replacement for Log4j 2. The real library depends on class loading, service discovery,
 * reflection and JMX; this one formats messages log4j-style and forwards them to the console sink.
 */
public final class LogManager {
    public static final String ROOT_LOGGER_NAME = "";
    private static final Map<String, Logger> LOGGERS = new HashMap<>();
    private static final LoggerContext CONTEXT = () -> null;
    private static volatile Level threshold = Level.INFO;

    private LogManager() {
    }

    public static void setThreshold(Level level) {
        threshold = level;
    }

    static Level threshold() {
        return threshold;
    }

    public static LoggerContext getContext() {
        return CONTEXT;
    }

    public static LoggerContext getContext(boolean currentContext) {
        return CONTEXT;
    }

    public static Logger getLogger() {
        return getLogger(callerName());
    }

    public static Logger getLogger(Class<?> clazz) {
        return getLogger(clazz == null ? "" : clazz.getName());
    }

    public static Logger getLogger(Object value) {
        return getLogger(value == null ? "" : value.getClass().getName());
    }

    public static Logger getLogger(String name) {
        String key = name == null ? "" : name;
        Logger logger = LOGGERS.get(key);
        if (logger == null) {
            logger = new SimpleLogger(key);
            LOGGERS.put(key, logger);
        }
        return logger;
    }

    public static Logger getRootLogger() {
        return getLogger(ROOT_LOGGER_NAME);
    }

    public static Logger getFormatterLogger(Class<?> clazz) {
        return getLogger(clazz);
    }

    public static Logger getFormatterLogger(String name) {
        return getLogger(name);
    }

    /** Stack walking is unavailable in Wasm; every caller-sensitive logger shares this name. */
    private static String callerName() {
        return "minecraft";
    }

    public static void shutdown() {
    }
}
