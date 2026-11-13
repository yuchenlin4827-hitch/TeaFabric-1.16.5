package teafabric.web.audio;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.teavm.interop.Async;
import org.teavm.interop.AsyncCallback;
import org.teavm.interop.Export;
import org.teavm.jso.JSBody;
import org.teavm.jso.typedarrays.ArrayBuffer;
import org.teavm.jso.typedarrays.Int8Array;
import teafabric.web.mem.NativeMemory;

/**
 * A sound decoded by the browser (WebAudio decodeAudioData: native, off the main thread), used by the
 * replacement OggAudioStream ({@code net.minecraft.class_4228}).
 *
 * <p>Vanilla sounds are not in assets.zip: each .ogg object there is a small placeholder holding the
 * object's hash (build.gradle, webSoundPlaceholders), so Minecraft registers every sound, and the real
 * file is fetched from {@code sounds/<hash>.ogg} next to the page the first time it is played. Sounds
 * from resource packs are real Ogg files and are decoded directly.
 */
public final class WebSound {
    private static final byte[] MARKER = "TEAFABRIC-SOUND\n".getBytes(StandardCharsets.UTF_8);
    private static final Map<Integer, AsyncCallback<Boolean>> waiting = new HashMap<>();
    private static int nextId = 1;

    private final int id;
    private final int channels;
    private final int frames;
    private final float sampleRate;
    private boolean released;

    private WebSound(int id) {
        this.id = id;
        this.channels = channelsJs(id);
        this.frames = framesJs(id);
        this.sampleRate = (float) rateJs(id);
    }

    /** Reads an Ogg file (or a placeholder) and decodes it. Suspends the calling thread until done. */
    public static WebSound load(InputStream in) throws IOException {
        byte[] data = in.readAllBytes();
        String hash = placeholderHash(data);
        int id = nextId++;
        boolean ok = hash != null ? decode(id, hash, null) : decode(id, null, Int8Array.copyFromJavaArray(data));
        if (!ok) {
            throw new IOException(hash != null ? "Cannot load sound " + hash : "Cannot decode Ogg Vorbis data");
        }
        return new WebSound(id);
    }

    private static String placeholderHash(byte[] data) {
        if (data.length < MARKER.length) {
            return null;
        }
        for (int i = 0; i < MARKER.length; i++) {
            if (data[i] != MARKER[i]) {
                return null;
            }
        }
        String hash = new String(data, MARKER.length, data.length - MARKER.length, StandardCharsets.UTF_8).trim();
        return hash.matches("[0-9a-f]{40}") ? hash : null;
    }

    public int channels() {
        return channels;
    }

    public int frames() {
        return frames;
    }

    public float sampleRate() {
        return sampleRate;
    }

    /** Writes {@code count} frames from {@code start} as interleaved 16-bit little-endian PCM into {@code out}. */
    public void copyPcm16(int start, int count, ByteBuffer out) {
        copyJs(id, start, count, NativeMemory.memory(), (int) NativeMemory.address(out));
    }

    public void release() {
        if (!released) {
            released = true;
            releaseJs(id);
        }
    }

    // ---- browser side (boot.js, teafabric.sound)

    @Async
    private static native boolean decode(int id, String hash, Int8Array data);

    private static void decode(int id, String hash, Int8Array data, AsyncCallback<Boolean> callback) {
        waiting.put(id, callback);
        if (!startJs(id, hash, data)) {
            waiting.remove(id);
            callback.complete(false);
        }
    }

    /** Called by boot.js when a sound has been decoded (ok = 1) or failed (ok = 0). */
    @Export(name = "teafabricSoundLoaded")
    public static void loaded(int id, int ok) {
        AsyncCallback<Boolean> callback = waiting.remove(id);
        if (callback != null) {
            callback.complete(ok != 0);
        }
    }

    @JSBody(params = {"id", "hash", "data"}, script = "return !!(teafabric.sound && teafabric.sound.load(id, hash, data));")
    private static native boolean startJs(int id, String hash, Int8Array data);

    @JSBody(params = "id", script = "return teafabric.sound.get(id).numberOfChannels;")
    private static native int channelsJs(int id);

    @JSBody(params = "id", script = "return teafabric.sound.get(id).length;")
    private static native int framesJs(int id);

    @JSBody(params = "id", script = "return teafabric.sound.get(id).sampleRate;")
    private static native double rateJs(int id);

    @JSBody(params = {"id", "start", "count", "mem", "ptr"}, script = "teafabric.sound.copy(id, start, count, mem, ptr);")
    private static native void copyJs(int id, int start, int count, ArrayBuffer mem, int ptr);

    @JSBody(params = "id", script = "teafabric.sound.release(id);")
    private static native void releaseJs(int id);
}
