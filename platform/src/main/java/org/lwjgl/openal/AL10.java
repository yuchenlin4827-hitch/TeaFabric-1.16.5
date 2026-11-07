package org.lwjgl.openal;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/** OpenAL 1.0/1.1 subset used by Minecraft, implemented on WebAudio (see {@link WebAudio}). */
public class AL10 {
    public static final int AL_NONE = 0;
    public static final int AL_FALSE = 0;
    public static final int AL_TRUE = 1;
    public static final int AL_NO_ERROR = 0;
    public static final int AL_SOURCE_RELATIVE = 0x202;
    public static final int AL_PITCH = 0x1003;
    public static final int AL_POSITION = 0x1004;
    public static final int AL_DIRECTION = 0x1005;
    public static final int AL_VELOCITY = 0x1006;
    public static final int AL_LOOPING = 0x1007;
    public static final int AL_BUFFER = 0x1009;
    public static final int AL_GAIN = 0x100A;
    public static final int AL_ORIENTATION = 0x100F;
    public static final int AL_SOURCE_STATE = 0x1010;
    public static final int AL_INITIAL = 0x1011;
    public static final int AL_PLAYING = 0x1012;
    public static final int AL_PAUSED = 0x1013;
    public static final int AL_STOPPED = 0x1014;
    public static final int AL_BUFFERS_QUEUED = 0x1015;
    public static final int AL_BUFFERS_PROCESSED = 0x1016;
    public static final int AL_REFERENCE_DISTANCE = 0x1020;
    public static final int AL_ROLLOFF_FACTOR = 0x1021;
    public static final int AL_MAX_DISTANCE = 0x1023;
    public static final int AL_FORMAT_MONO8 = 0x1100;
    public static final int AL_FORMAT_MONO16 = 0x1101;
    public static final int AL_FORMAT_STEREO8 = 0x1102;
    public static final int AL_FORMAT_STEREO16 = 0x1103;
    public static final int AL_DISTANCE_MODEL = 0xD000;
    public static final int AL_LINEAR_DISTANCE = 0xD003;
    public static final int AL_LINEAR_DISTANCE_CLAMPED = 0xD004;

    protected AL10() {
    }

    public static int alGetError() {
        return AL_NO_ERROR;
    }

    public static void alEnable(int capability) {
    }

    public static void alDisable(int capability) {
    }

    public static void alDistanceModel(int model) {
    }

    public static void alGenSources(int[] sources) {
        for (int i = 0; i < sources.length; i++) {
            sources[i] = WebAudio.genSource();
        }
    }

    public static int alGenSources() {
        return WebAudio.genSource();
    }

    public static void alDeleteSources(int[] sources) {
        for (int s : sources) {
            WebAudio.deleteSource(s);
        }
    }

    public static void alDeleteSources(int source) {
        WebAudio.deleteSource(source);
    }

    public static void alGenBuffers(int[] buffers) {
        for (int i = 0; i < buffers.length; i++) {
            buffers[i] = WebAudio.genBuffer();
        }
    }

    public static int alGenBuffers() {
        return WebAudio.genBuffer();
    }

    public static void alDeleteBuffers(int[] buffers) {
        for (int b : buffers) {
            WebAudio.deleteBuffer(b);
        }
    }

    public static void alDeleteBuffers(int buffer) {
        WebAudio.deleteBuffer(buffer);
    }

    public static void alBufferData(int buffer, int format, ByteBuffer data, int frequency) {
        WebAudio.bufferData(buffer, format, data, frequency);
    }

    public static void alSourcei(int source, int param, int value) {
        WebAudio.sourcei(source, param, value);
    }

    public static void alSourcef(int source, int param, float value) {
        WebAudio.sourcef(source, param, value);
    }

    public static void alSource3f(int source, int param, float x, float y, float z) {
        WebAudio.source3f(source, param, x, y, z);
    }

    public static void alSourcefv(int source, int param, float[] values) {
        WebAudio.source3f(source, param, values[0], values[1], values[2]);
    }

    public static void alSourcefv(int source, int param, FloatBuffer values) {
        int p = values.position();
        WebAudio.source3f(source, param, values.get(p), values.get(p + 1), values.get(p + 2));
    }

    public static int alGetSourcei(int source, int param) {
        return WebAudio.getSourcei(source, param);
    }

    public static void alSourcePlay(int source) {
        WebAudio.play(source);
    }

    public static void alSourcePause(int source) {
        WebAudio.pause(source);
    }

    public static void alSourceStop(int source) {
        WebAudio.stop(source);
    }

    public static void alSourceRewind(int source) {
        WebAudio.stop(source);
    }

    public static void alSourceQueueBuffers(int source, int[] buffers) {
        for (int b : buffers) {
            WebAudio.queue(source, b);
        }
    }

    public static void alSourceQueueBuffers(int source, int buffer) {
        WebAudio.queue(source, buffer);
    }

    public static void alSourceUnqueueBuffers(int source, int[] buffers) {
        for (int i = 0; i < buffers.length; i++) {
            buffers[i] = WebAudio.unqueue(source);
        }
    }

    public static int alSourceUnqueueBuffers(int source) {
        return WebAudio.unqueue(source);
    }

    public static void alSourceUnqueueBuffers(int source, IntBuffer buffers) {
        for (int i = buffers.position(); i < buffers.limit(); i++) {
            buffers.put(i, WebAudio.unqueue(source));
        }
    }

    public static void alListenerf(int param, float value) {
        WebAudio.listenerf(param, value);
    }

    public static void alListener3f(int param, float x, float y, float z) {
        WebAudio.listener3f(param, x, y, z);
    }

    public static void alListenerfv(int param, float[] values) {
        if (param == AL_ORIENTATION) {
            WebAudio.listenerOrientation(values);
        } else {
            WebAudio.listener3f(param, values[0], values[1], values[2]);
        }
    }

    public static void alListenerfv(int param, FloatBuffer values) {
        float[] v = new float[values.remaining()];
        values.duplicate().get(v);
        alListenerfv(param, v);
    }

    public static String alGetString(int param) {
        return "WebAudio";
    }
}
