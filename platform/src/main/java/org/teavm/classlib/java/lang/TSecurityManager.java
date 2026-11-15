package org.teavm.classlib.java.lang;

/**
 * Replaces TeaVM's empty SecurityManager so subclasses compile (SLF4J's ClassContextSecurityManager calls
 * getClassContext()); there is no security manager or call-stack inspection in the browser.
 */
public class TSecurityManager {
    public TSecurityManager() {
    }

    protected Class<?>[] getClassContext() {
        return new Class<?>[0];
    }

    public ThreadGroup getThreadGroup() {
        return Thread.currentThread().getThreadGroup();
    }

    public void checkPermission(java.security.Permission perm) {
    }

    public void checkExit(int status) {
    }
}
