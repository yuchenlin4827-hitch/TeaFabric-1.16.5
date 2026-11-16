package org.teavm.classlib.java.security;

/** Thrown by Custom Entity Models for invalid model files. */
public class TInvalidParameterException extends IllegalArgumentException {
    private static final long serialVersionUID = -857968536935667808L;

    public TInvalidParameterException() {
    }

    public TInvalidParameterException(String msg) {
        super(msg);
    }
}
