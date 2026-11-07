package org.lwjgl.openal;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

/** OpenAL context API: one implicit WebAudio context. */
public class ALC10 {
    public static final int ALC_FREQUENCY = 0x1007;
    public static final int ALC_REFRESH = 0x1008;
    public static final int ALC_SYNC = 0x1009;
    public static final int ALC_MONO_SOURCES = 0x1010;
    public static final int ALC_STEREO_SOURCES = 0x1011;
    public static final int ALC_ATTRIBUTES_SIZE = 0x1002;
    public static final int ALC_ALL_ATTRIBUTES = 0x1003;

    protected ALC10() {
    }

    public static long alcOpenDevice(ByteBuffer deviceSpecifier) {
        return WebAudio.init() ? 1L : 0L;
    }

    public static long alcOpenDevice(CharSequence deviceSpecifier) {
        return alcOpenDevice((ByteBuffer) null);
    }

    public static boolean alcCloseDevice(long device) {
        return true;
    }

    public static long alcCreateContext(long device, IntBuffer attrList) {
        return 1L;
    }

    public static long alcCreateContext(long device, int[] attrList) {
        return 1L;
    }

    public static boolean alcMakeContextCurrent(long context) {
        return true;
    }

    public static void alcDestroyContext(long context) {
    }

    public static int alcGetError(long device) {
        return 0;
    }

    public static int alcGetInteger(long device, int param) {
        switch (param) {
            case ALC_MONO_SOURCES: return 247;
            case ALC_STEREO_SOURCES: return 8;
            case ALC_FREQUENCY: return 48000;
            case ALC_ATTRIBUTES_SIZE: return 5;
            default: return 0;
        }
    }

    /** ALC_ALL_ATTRIBUTES as key/value pairs terminated by 0. */
    public static void alcGetIntegerv(long device, int param, IntBuffer values) {
        if (param == ALC_ALL_ATTRIBUTES) {
            int[] attrs = {ALC_MONO_SOURCES, 247, ALC_STEREO_SOURCES, 8, 0};
            for (int i = 0; i < attrs.length && values.position() + i < values.limit(); i++) {
                values.put(values.position() + i, attrs[i]);
            }
        } else {
            values.put(values.position(), alcGetInteger(device, param));
        }
    }

    public static String alcGetString(long device, int param) {
        return "WebAudio";
    }

    public static boolean alcIsExtensionPresent(long device, CharSequence ext) {
        return false;
    }
}
