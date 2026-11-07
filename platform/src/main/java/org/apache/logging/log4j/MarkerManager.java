package org.apache.logging.log4j;

import java.util.HashMap;
import java.util.Map;

public final class MarkerManager {
    private static final Map<String, Marker> MARKERS = new HashMap<>();

    private MarkerManager() {
    }

    public static Marker getMarker(String name) {
        return MARKERS.computeIfAbsent(name, n -> new Log4jMarker(n, new Marker[0]));
    }

    public static Marker getMarker(String name, Marker parent) {
        return MARKERS.computeIfAbsent(name, n -> new Log4jMarker(n, parent == null ? new Marker[0]
                : new Marker[] {parent}));
    }

    public static final class Log4jMarker implements Marker {
        private final String name;
        private final Marker[] parents;

        Log4jMarker(String name, Marker[] parents) {
            this.name = name;
            this.parents = parents;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Marker[] getParents() {
            return parents.clone();
        }

        @Override
        public boolean isInstanceOf(Marker m) {
            if (m == this) {
                return true;
            }
            for (Marker p : parents) {
                if (p.isInstanceOf(m)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean isInstanceOf(String n) {
            if (name.equals(n)) {
                return true;
            }
            for (Marker p : parents) {
                if (p.isInstanceOf(n)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
