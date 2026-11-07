package com.mojang.text2speech;

import org.teavm.jso.JSBody;

final class WebSpeechNarrator implements Narrator {
    @Override
    public void say(String msg, boolean interrupt) {
        speak(msg, interrupt);
    }

    @Override
    public void clear() {
        cancel();
    }

    @Override
    public boolean active() {
        return supported();
    }

    @Override
    public void destroy() {
        cancel();
    }

    @JSBody(script = "return typeof speechSynthesis !== 'undefined';")
    private static native boolean supported();

    @JSBody(params = {"text", "interrupt"}, script = ""
            + "if (typeof speechSynthesis === 'undefined') return;"
            + "if (interrupt) speechSynthesis.cancel();"
            + "speechSynthesis.speak(new SpeechSynthesisUtterance(text));")
    private static native void speak(String text, boolean interrupt);

    @JSBody(script = "if (typeof speechSynthesis !== 'undefined') speechSynthesis.cancel();")
    private static native void cancel();
}
