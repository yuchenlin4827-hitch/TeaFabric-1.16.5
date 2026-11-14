package org.teavm.classlib.java.lang.invoke;

/** Declared so code that catches it compiles; nothing here throws it. */
public class TWrongMethodTypeException extends RuntimeException {
    private static final long serialVersionUID = 292L;

    public TWrongMethodTypeException() {
    }

    public TWrongMethodTypeException(String s) {
        super(s);
    }
}
