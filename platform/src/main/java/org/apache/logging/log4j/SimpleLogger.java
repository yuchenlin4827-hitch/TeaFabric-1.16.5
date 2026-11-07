package org.apache.logging.log4j;

import teafabric.web.log.LogSink;

final class SimpleLogger implements Logger {
    private final String name;
    private final String shortName;

    SimpleLogger(String name) {
        this.name = name;
        int dot = name.lastIndexOf('.');
        this.shortName = dot >= 0 ? name.substring(dot + 1) : name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean isEnabled(Level level) {
        return level.intLevel() <= LogManager.threshold().intLevel();
    }

    @Override
    public void logMessage(Level level, Marker marker, String message, Throwable t) {
        LogSink.log(level.intLevel() <= Level.ERROR.intLevel() ? 3 : level == Level.WARN ? 2
                : level == Level.INFO ? 1 : 0, level.name(), shortName, message, t);
    }
}
