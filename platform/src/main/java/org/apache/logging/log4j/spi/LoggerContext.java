package org.apache.logging.log4j.spi;

/** Only exists so that {@code LogManager.getContext(boolean)} has something to return. */
public interface LoggerContext {
    Object getExternalContext();
}
