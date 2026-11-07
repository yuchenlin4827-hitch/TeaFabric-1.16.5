package org.apache.logging.log4j;

public interface Marker {
    String getName();

    Marker[] getParents();

    boolean isInstanceOf(Marker m);

    boolean isInstanceOf(String name);
}
