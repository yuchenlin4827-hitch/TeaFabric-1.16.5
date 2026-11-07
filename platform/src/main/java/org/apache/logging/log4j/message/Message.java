package org.apache.logging.log4j.message;

public interface Message {
    String getFormattedMessage();

    String getFormat();

    Object[] getParameters();

    Throwable getThrowable();
}
