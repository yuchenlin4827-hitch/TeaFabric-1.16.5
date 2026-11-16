package org.slf4j.impl;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.slf4j.helpers.MarkerIgnoringBase;

/** An SLF4J logger that writes to the platform's Log4j logger of the same name. Generated: one block per level. */
final class Log4jForwardingLogger extends MarkerIgnoringBase {
    private static final long serialVersionUID = 1L;
    private final transient org.apache.logging.log4j.Logger log;

    Log4jForwardingLogger(String name) {
        this.name = name;
        this.log = LogManager.getLogger(name);
    }

    @Override
    public boolean isTraceEnabled() {
        return log.isEnabled(Level.TRACE);
    }

    @Override
    public void trace(String msg) {
        log.log(Level.TRACE, msg);
    }

    @Override
    public void trace(String format, Object arg) {
        log.log(Level.TRACE, format, arg);
    }

    @Override
    public void trace(String format, Object arg1, Object arg2) {
        log.log(Level.TRACE, format, arg1, arg2);
    }

    @Override
    public void trace(String format, Object... arguments) {
        log.log(Level.TRACE, format, arguments);
    }

    @Override
    public void trace(String msg, Throwable t) {
        log.log(Level.TRACE, msg, t);
    }

    @Override
    public boolean isDebugEnabled() {
        return log.isEnabled(Level.DEBUG);
    }

    @Override
    public void debug(String msg) {
        log.log(Level.DEBUG, msg);
    }

    @Override
    public void debug(String format, Object arg) {
        log.log(Level.DEBUG, format, arg);
    }

    @Override
    public void debug(String format, Object arg1, Object arg2) {
        log.log(Level.DEBUG, format, arg1, arg2);
    }

    @Override
    public void debug(String format, Object... arguments) {
        log.log(Level.DEBUG, format, arguments);
    }

    @Override
    public void debug(String msg, Throwable t) {
        log.log(Level.DEBUG, msg, t);
    }

    @Override
    public boolean isInfoEnabled() {
        return log.isEnabled(Level.INFO);
    }

    @Override
    public void info(String msg) {
        log.log(Level.INFO, msg);
    }

    @Override
    public void info(String format, Object arg) {
        log.log(Level.INFO, format, arg);
    }

    @Override
    public void info(String format, Object arg1, Object arg2) {
        log.log(Level.INFO, format, arg1, arg2);
    }

    @Override
    public void info(String format, Object... arguments) {
        log.log(Level.INFO, format, arguments);
    }

    @Override
    public void info(String msg, Throwable t) {
        log.log(Level.INFO, msg, t);
    }

    @Override
    public boolean isWarnEnabled() {
        return log.isEnabled(Level.WARN);
    }

    @Override
    public void warn(String msg) {
        log.log(Level.WARN, msg);
    }

    @Override
    public void warn(String format, Object arg) {
        log.log(Level.WARN, format, arg);
    }

    @Override
    public void warn(String format, Object arg1, Object arg2) {
        log.log(Level.WARN, format, arg1, arg2);
    }

    @Override
    public void warn(String format, Object... arguments) {
        log.log(Level.WARN, format, arguments);
    }

    @Override
    public void warn(String msg, Throwable t) {
        log.log(Level.WARN, msg, t);
    }

    @Override
    public boolean isErrorEnabled() {
        return log.isEnabled(Level.ERROR);
    }

    @Override
    public void error(String msg) {
        log.log(Level.ERROR, msg);
    }

    @Override
    public void error(String format, Object arg) {
        log.log(Level.ERROR, format, arg);
    }

    @Override
    public void error(String format, Object arg1, Object arg2) {
        log.log(Level.ERROR, format, arg1, arg2);
    }

    @Override
    public void error(String format, Object... arguments) {
        log.log(Level.ERROR, format, arguments);
    }

    @Override
    public void error(String msg, Throwable t) {
        log.log(Level.ERROR, msg, t);
    }
}
