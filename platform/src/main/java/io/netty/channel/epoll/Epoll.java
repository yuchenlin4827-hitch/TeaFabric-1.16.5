package io.netty.channel.epoll;

public final class Epoll {
    private static final Throwable UNAVAILABILITY_CAUSE =
            new UnsupportedOperationException("epoll is not available in the browser");

    private Epoll() {
    }

    public static boolean isAvailable() {
        return false;
    }

    public static void ensureAvailability() {
        throw (Error) new UnsatisfiedLinkError("epoll is not available in the browser").initCause(UNAVAILABILITY_CAUSE);
    }

    public static Throwable unavailabilityCause() {
        return UNAVAILABILITY_CAUSE;
    }
}
