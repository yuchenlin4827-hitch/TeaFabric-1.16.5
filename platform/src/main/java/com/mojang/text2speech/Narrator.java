package com.mojang.text2speech;

/**
 * Replacement for Mojang's text2speech (native SAPI / flite / NSSpeechSynthesizer) backed by the
 * browser's Web Speech API.
 */
public interface Narrator {
    void say(String msg, boolean interrupt);

    void clear();

    boolean active();

    void destroy();

    static Narrator getNarrator() {
        return new WebSpeechNarrator();
    }

    static void setJNAPath(String path) {
    }
}
