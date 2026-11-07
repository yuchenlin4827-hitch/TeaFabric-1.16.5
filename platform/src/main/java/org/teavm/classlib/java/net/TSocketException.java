package org.teavm.classlib.java.net;

public class TSocketException extends java.io.IOException {
    private static final long serialVersionUID = 1L;

    public TSocketException() {
    }

    public TSocketException(String message) {
        super(message);
    }

    public TSocketException(String message, Throwable cause) {
        super(message, cause);
    }
}
