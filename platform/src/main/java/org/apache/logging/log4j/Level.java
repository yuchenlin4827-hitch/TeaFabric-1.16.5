package org.apache.logging.log4j;

/** Minimal Log4j 2 API replacement for the browser build (see LogManager). */
public final class Level implements Comparable<Level> {
    public static final Level OFF = new Level("OFF", 0);
    public static final Level FATAL = new Level("FATAL", 100);
    public static final Level ERROR = new Level("ERROR", 200);
    public static final Level WARN = new Level("WARN", 300);
    public static final Level INFO = new Level("INFO", 400);
    public static final Level DEBUG = new Level("DEBUG", 500);
    public static final Level TRACE = new Level("TRACE", 600);
    public static final Level ALL = new Level("ALL", Integer.MAX_VALUE);

    private final String name;
    private final int intLevel;

    private Level(String name, int intLevel) {
        this.name = name;
        this.intLevel = intLevel;
    }

    public int intLevel() {
        return intLevel;
    }

    public String name() {
        return name;
    }

    public boolean isMoreSpecificThan(Level level) {
        return intLevel <= level.intLevel;
    }

    public boolean isLessSpecificThan(Level level) {
        return intLevel >= level.intLevel;
    }

    public static Level toLevel(String name, Level defaultLevel) {
        if (name == null) {
            return defaultLevel;
        }
        switch (name.toUpperCase()) {
            case "OFF": return OFF;
            case "FATAL": return FATAL;
            case "ERROR": return ERROR;
            case "WARN": return WARN;
            case "INFO": return INFO;
            case "DEBUG": return DEBUG;
            case "TRACE": return TRACE;
            case "ALL": return ALL;
            default: return defaultLevel;
        }
    }

    public static Level getLevel(String name) {
        return toLevel(name, null);
    }

    public static Level valueOf(String name) {
        Level l = toLevel(name, null);
        if (l == null) {
            throw new IllegalArgumentException("Unknown level " + name);
        }
        return l;
    }

    @Override
    public int compareTo(Level o) {
        return Integer.compare(intLevel, o.intLevel);
    }

    @Override
    public String toString() {
        return name;
    }
}
