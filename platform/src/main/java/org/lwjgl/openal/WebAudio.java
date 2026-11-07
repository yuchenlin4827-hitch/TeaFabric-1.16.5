package org.lwjgl.openal;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSObject;
import teafabric.web.mem.NativeMemory;

/**
 * OpenAL semantics on WebAudio. Buffers become AudioBuffers; a source is a GainNode → PannerNode
 * chain (inverse-distance disabled: Minecraft uses AL_LINEAR_DISTANCE) and plays either one static
 * buffer or a queue of streamed buffers scheduled back to back on the audio clock.
 */
final class WebAudio {
    private WebAudio() {
    }

    static final class Source {
        JSObject node; // { gain, panner }
        int buffer;
        boolean looping;
        boolean relative;
        float gain = 1;
        float pitch = 1;
        float maxDistance = Float.MAX_VALUE;
        float refDistance = 1;
        float rolloff = 1;
        float[] position = new float[3];
        int state = AL10.AL_INITIAL;
        final ArrayDeque<int[]> queue = new ArrayDeque<>(); // {buffer, endTimeMillis-as-int-key}
        final ArrayDeque<Integer> processed = new ArrayDeque<>();
        final List<Double> queueEnds = new ArrayList<>();
        double nextStart;
        JSObject playing; // current AudioBufferSourceNode (static mode)
    }

    private static final List<Source> sources = new ArrayList<>();
    private static final List<JSObject> buffers = new ArrayList<>();
    private static boolean ok;

    static boolean init() {
        ok = initJs();
        if (sources.isEmpty()) {
            sources.add(null);
            buffers.add(null);
        }
        return ok;
    }

    @JSBody(script = "try { teafabric.audio = teafabric.audio || new (window.AudioContext || "
            + "window.webkitAudioContext)(); teafabric.audioGain = teafabric.audio.createGain(); "
            + "teafabric.audioGain.connect(teafabric.audio.destination); return true; } catch (e) { return false; }")
    private static native boolean initJs();

    @JSBody(script = "return teafabric.audio.currentTime;")
    private static native double now();

    static int genSource() {
        Source s = new Source();
        s.node = createNode();
        sources.add(s);
        return sources.size() - 1;
    }

    @JSBody(script = "var a = teafabric.audio; var g = a.createGain(); var p = a.createPanner();"
            + "p.panningModel = 'equalpower'; p.distanceModel = 'linear'; p.refDistance = 1; p.maxDistance = 16;"
            + "p.rolloffFactor = 1; g.connect(p); p.connect(teafabric.audioGain); return {gain: g, panner: p};")
    private static native JSObject createNode();

    static void deleteSource(int id) {
        Source s = source(id);
        if (s != null) {
            stop(id);
            disconnect(s.node);
            sources.set(id, null);
        }
    }

    @JSBody(params = "n", script = "n.gain.disconnect(); n.panner.disconnect();")
    private static native void disconnect(JSObject n);

    static int genBuffer() {
        buffers.add(null);
        return buffers.size() - 1;
    }

    static void deleteBuffer(int id) {
        if (id > 0 && id < buffers.size()) {
            buffers.set(id, null);
        }
    }

    private static Source source(int id) {
        return id > 0 && id < sources.size() ? sources.get(id) : null;
    }

    static void bufferData(int buffer, int format, ByteBuffer data, int frequency) {
        if (!ok || buffer <= 0 || buffer >= buffers.size()) {
            return;
        }
        int channels = format == AL10.AL_FORMAT_STEREO8 || format == AL10.AL_FORMAT_STEREO16 ? 2 : 1;
        boolean sixteen = format == AL10.AL_FORMAT_MONO16 || format == AL10.AL_FORMAT_STEREO16;
        long ptr = NativeMemory.address(data);
        buffers.set(buffer, makeBuffer(NativeMemory.memory(), (int) ptr, data.remaining(), channels, sixteen,
                frequency));
    }

    @JSBody(params = {"mem", "ptr", "bytes", "channels", "sixteen", "rate"}, script = ""
            + "var frames = Math.floor(bytes / ((sixteen ? 2 : 1) * channels));"
            + "if (frames <= 0) return null;"
            + "var buf = teafabric.audio.createBuffer(channels, frames, rate);"
            + "if (sixteen) { var src = new Int16Array(mem, ptr, frames * channels);"
            + "  for (var c = 0; c < channels; c++) { var out = buf.getChannelData(c);"
            + "    for (var i = 0; i < frames; i++) out[i] = src[i * channels + c] / 32768; } }"
            + "else { var src8 = new Uint8Array(mem, ptr, frames * channels);"
            + "  for (var c2 = 0; c2 < channels; c2++) { var out2 = buf.getChannelData(c2);"
            + "    for (var j = 0; j < frames; j++) out2[j] = (src8[j * channels + c2] - 128) / 128; } }"
            + "return buf;")
    private static native JSObject makeBuffer(JSObject mem, int ptr, int bytes, int channels, boolean sixteen,
            int rate);

    static void sourcei(int id, int param, int value) {
        Source s = source(id);
        if (s == null) {
            return;
        }
        switch (param) {
            case AL10.AL_BUFFER:
                s.buffer = value;
                s.queue.clear();
                s.processed.clear();
                break;
            case AL10.AL_LOOPING:
                s.looping = value != 0;
                setLoop(s.playing, s.looping);
                break;
            case AL10.AL_SOURCE_RELATIVE:
                s.relative = value != 0;
                applyPosition(s);
                break;
            default:
                break;
        }
    }

    static void sourcef(int id, int param, float value) {
        Source s = source(id);
        if (s == null) {
            return;
        }
        switch (param) {
            case AL10.AL_GAIN: s.gain = value; setGain(s.node, value); break;
            case AL10.AL_PITCH: s.pitch = value; setRate(s.playing, value); break;
            case AL10.AL_MAX_DISTANCE: s.maxDistance = value; setDistance(s.node, s.refDistance, value, s.rolloff);
                break;
            case AL10.AL_REFERENCE_DISTANCE: s.refDistance = value;
                setDistance(s.node, value, s.maxDistance, s.rolloff); break;
            case AL10.AL_ROLLOFF_FACTOR: s.rolloff = value; setDistance(s.node, s.refDistance, s.maxDistance, value);
                break;
            default: break;
        }
    }

    static void source3f(int id, int param, float x, float y, float z) {
        Source s = source(id);
        if (s != null && param == AL10.AL_POSITION) {
            s.position[0] = x;
            s.position[1] = y;
            s.position[2] = z;
            applyPosition(s);
        }
    }

    private static void applyPosition(Source s) {
        float[] p = s.position;
        if (s.relative) { // relative sources are expressed against the listener position
            setPannerPosition(s.node, p[0] + listener[0], p[1] + listener[1], p[2] + listener[2]);
        } else {
            setPannerPosition(s.node, p[0], p[1], p[2]);
        }
    }

    @JSBody(params = {"n", "v"}, script = "n.gain.gain.value = v;")
    private static native void setGain(JSObject n, float v);

    @JSBody(params = {"n", "x", "y", "z"}, script = "var p = n.panner;"
            + "if (p.positionX) { p.positionX.value = x; p.positionY.value = y; p.positionZ.value = z; }"
            + "else p.setPosition(x, y, z);")
    private static native void setPannerPosition(JSObject n, float x, float y, float z);

    @JSBody(params = {"n", "ref", "max", "roll"}, script = "var p = n.panner; p.refDistance = Math.max(ref, 0.0001);"
            + "p.maxDistance = Math.min(Math.max(max, p.refDistance + 0.001), 1e6); p.rolloffFactor = Math.min(roll, 1);")
    private static native void setDistance(JSObject n, float ref, float max, float roll);

    @JSBody(params = {"b", "loop"}, script = "if (b) b.loop = loop;")
    private static native void setLoop(JSObject b, boolean loop);

    @JSBody(params = {"b", "rate"}, script = "if (b) b.playbackRate.value = rate;")
    private static native void setRate(JSObject b, float rate);

    @JSBody(params = {"n", "buf", "loop", "rate", "when"}, script = ""
            + "var a = teafabric.audio; if (a.state === 'suspended') a.resume();"
            + "var s = a.createBufferSource(); s.buffer = buf; s.loop = loop; s.playbackRate.value = rate;"
            + "s.connect(n.gain); s.start(Math.max(when, a.currentTime)); return s;")
    private static native JSObject start(JSObject n, JSObject buf, boolean loop, float rate, double when);

    @JSBody(params = "b", script = "if (b) { try { b.stop(); } catch (e) {} b.disconnect(); }")
    private static native void stopNode(JSObject b);

    @JSBody(params = "b", script = "return b ? b.duration : 0;")
    private static native double duration(JSObject b);

    static void play(int id) {
        Source s = source(id);
        if (s == null || !ok) {
            return;
        }
        if (s.state == AL10.AL_PLAYING) {
            return;
        }
        s.state = AL10.AL_PLAYING;
        if (!s.queue.isEmpty()) {
            s.nextStart = now();
            s.queueEnds.clear();
            for (int[] q : s.queue) {
                scheduleQueued(s, q[0]);
            }
            return;
        }
        JSObject buf = s.buffer > 0 && s.buffer < buffers.size() ? buffers.get(s.buffer) : null;
        if (buf != null) {
            s.playing = start(s.node, buf, s.looping, s.pitch, now());
            s.queueEnds.clear();
            s.queueEnds.add(s.looping ? Double.MAX_VALUE : now() + duration(buf) / Math.max(s.pitch, 0.01));
        } else {
            s.state = AL10.AL_STOPPED;
        }
    }

    private static void scheduleQueued(Source s, int buffer) {
        JSObject buf = buffer > 0 && buffer < buffers.size() ? buffers.get(buffer) : null;
        if (buf == null) {
            s.queueEnds.add(s.nextStart);
            return;
        }
        double start = Math.max(s.nextStart, now());
        start(s.node, buf, false, s.pitch, start);
        s.nextStart = start + duration(buf) / Math.max(s.pitch, 0.01);
        s.queueEnds.add(s.nextStart);
    }

    static void pause(int id) {
        Source s = source(id);
        if (s != null && s.state == AL10.AL_PLAYING) {
            stopNode(s.playing);
            s.playing = null;
            s.state = AL10.AL_PAUSED;
        }
    }

    static void stop(int id) {
        Source s = source(id);
        if (s != null) {
            stopNode(s.playing);
            s.playing = null;
            s.state = AL10.AL_STOPPED;
            for (int[] q : s.queue) {
                s.processed.add(q[0]);
            }
            s.queue.clear();
            s.queueEnds.clear();
        }
    }

    static void queue(int id, int buffer) {
        Source s = source(id);
        if (s == null) {
            return;
        }
        s.queue.add(new int[] {buffer});
        if (s.state == AL10.AL_PLAYING) {
            scheduleQueued(s, buffer);
        }
    }

    static int unqueue(int id) {
        Source s = source(id);
        if (s == null) {
            return 0;
        }
        updateQueue(s);
        Integer b = s.processed.poll();
        return b == null ? 0 : b;
    }

    private static void updateQueue(Source s) {
        double t = now();
        while (!s.queue.isEmpty() && !s.queueEnds.isEmpty() && s.queueEnds.get(0) <= t) {
            s.processed.add(s.queue.poll()[0]);
            s.queueEnds.remove(0);
        }
    }

    static int getSourcei(int id, int param) {
        Source s = source(id);
        if (s == null) {
            return 0;
        }
        switch (param) {
            case AL10.AL_SOURCE_STATE:
                if (s.state == AL10.AL_PLAYING) {
                    updateQueue(s);
                    boolean done = s.queueEnds.isEmpty() || s.queueEnds.get(s.queueEnds.size() - 1) <= now();
                    if (done && !s.looping) {
                        s.state = AL10.AL_STOPPED;
                    }
                }
                return s.state;
            case AL10.AL_BUFFERS_PROCESSED:
                updateQueue(s);
                return s.processed.size();
            case AL10.AL_BUFFERS_QUEUED:
                return s.queue.size();
            default:
                return 0;
        }
    }

    private static final float[] listener = new float[3];

    static void listenerf(int param, float value) {
        if (param == AL10.AL_GAIN && ok) {
            setMasterGain(value);
        }
    }

    @JSBody(params = "v", script = "teafabric.audioGain.gain.value = v;")
    private static native void setMasterGain(float v);

    static void listener3f(int param, float x, float y, float z) {
        if (param == AL10.AL_POSITION && ok) {
            listener[0] = x;
            listener[1] = y;
            listener[2] = z;
            setListenerPosition(x, y, z);
        }
    }

    @JSBody(params = {"x", "y", "z"}, script = "var l = teafabric.audio.listener;"
            + "if (l.positionX) { l.positionX.value = x; l.positionY.value = y; l.positionZ.value = z; }"
            + "else l.setPosition(x, y, z);")
    private static native void setListenerPosition(float x, float y, float z);

    static void listenerOrientation(float[] v) {
        if (ok && v.length >= 6) {
            setOrientation(v[0], v[1], v[2], v[3], v[4], v[5]);
        }
    }

    @JSBody(params = {"fx", "fy", "fz", "ux", "uy", "uz"}, script = "var l = teafabric.audio.listener;"
            + "if (l.forwardX) { l.forwardX.value = fx; l.forwardY.value = fy; l.forwardZ.value = fz;"
            + "l.upX.value = ux; l.upY.value = uy; l.upZ.value = uz; } else l.setOrientation(fx, fy, fz, ux, uy, uz);")
    private static native void setOrientation(float fx, float fy, float fz, float ux, float uy, float uz);
}
