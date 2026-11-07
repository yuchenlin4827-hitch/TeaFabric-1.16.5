/**
 * Additions to TeaVM's class library: java.util.concurrent types Minecraft needs that TeaVM 0.16
 * does not provide. TeaVM substitutes {@code java.util.concurrent.X} with
 * {@code org.teavm.classlib.java.util.concurrent.TX} from any class path entry.
 *
 * <p>All implementations target TeaVM's green threads (fibers on a single JS thread): blocking is
 * done with object monitors ({@code wait}/{@code notifyAll}), which TeaVM turns into fiber suspension.
 */
package org.teavm.classlib.java.util.concurrent;
