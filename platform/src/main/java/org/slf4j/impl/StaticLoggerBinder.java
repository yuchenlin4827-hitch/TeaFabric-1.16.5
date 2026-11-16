package org.slf4j.impl;

import java.util.HashMap;
import java.util.Map;
import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;
import org.slf4j.spi.LoggerFactoryBinder;

/**
 * SLF4J 1.7 binding for mods that bundle slf4j-api without a backend (Iris's shader preprocessor, JCPP).
 * SLF4J finds its backend through this class name; on the JVM it would fall back to a no-op logger when the
 * class is missing, which relies on NoClassDefFoundError and does not work here. Messages go to Log4j.
 */
public final class StaticLoggerBinder implements LoggerFactoryBinder {
    // Not final, as in SLF4J's own bindings, so the compiler does not inline it into LoggerFactory.
    public static String REQUESTED_API_VERSION = "1.6.99";

    private static final StaticLoggerBinder SINGLETON = new StaticLoggerBinder();

    private final ILoggerFactory factory = new ILoggerFactory() {
        private final Map<String, Logger> loggers = new HashMap<>();

        @Override
        public synchronized Logger getLogger(String name) {
            return loggers.computeIfAbsent(name, Log4jForwardingLogger::new);
        }
    };

    private StaticLoggerBinder() {
    }

    public static StaticLoggerBinder getSingleton() {
        return SINGLETON;
    }

    @Override
    public ILoggerFactory getLoggerFactory() {
        return factory;
    }

    @Override
    public String getLoggerFactoryClassStr() {
        return Log4jForwardingLogger.class.getName();
    }
}
