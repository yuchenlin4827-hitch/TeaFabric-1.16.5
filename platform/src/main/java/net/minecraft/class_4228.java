package net.minecraft;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import javax.sound.sampled.AudioFormat;
import org.lwjgl.BufferUtils;
import teafabric.web.audio.WebSound;
import teafabric.web.log.LogSink;

/**
 * Browser replacement for Minecraft's OggAudioStream, which decodes with stb_vorbis. The platform layer
 * comes before the game on TeaVM's class path, so every user (SoundLoader's static sounds, streamed
 * music, RepeatingAudioStream's {@code class_4228::new}) gets this class. Decoding is done by the
 * browser ({@link WebSound}); this class hands out the result as 16-bit PCM like the original.
 */
public class class_4228 implements class_4234 {
    private final WebSound sound;
    private final AudioFormat format;
    private int frame;

    public class_4228(InputStream in) throws IOException {
        WebSound loaded = null;
        try {
            loaded = WebSound.load(in);
            format = new AudioFormat(loaded.sampleRate(), 16, loaded.channels(), true, false);
        } catch (IOException | RuntimeException | Error e) {
            // SoundLoader runs this in a CompletableFuture, which would drop the exception silently.
            LogSink.log(2, "WARN", "Sound", "Cannot load sound", e);
            if (loaded != null) {
                loaded.release();
            }
            throw e;
        }
        sound = loaded;
    }

    /** getFormat */
    @Override
    public AudioFormat method_19719() {
        return format;
    }

    /** getBuffer(size): at least {@code size} bytes while data lasts (like the original); empty at the end. */
    @Override
    public ByteBuffer method_19720(int size) throws IOException {
        int frameBytes = 2 * sound.channels();
        return read(Math.max(1, (size + frameBytes - 1) / frameBytes));
    }

    /** getBuffer(): everything that is left. */
    public ByteBuffer method_19721() throws IOException {
        return read(Integer.MAX_VALUE);
    }

    private ByteBuffer read(int maxFrames) {
        try {
            int count = Math.max(0, Math.min(maxFrames, sound.frames() - frame));
            ByteBuffer buffer = BufferUtils.createByteBuffer(count * 2 * sound.channels());
            if (count > 0) {
                sound.copyPcm16(frame, count, buffer);
                frame += count;
            }
            return buffer;
        } catch (RuntimeException | Error e) {
            LogSink.log(2, "WARN", "Sound", "Cannot read decoded sound", e);
            throw e;
        }
    }

    @Override
    public void close() {
        sound.release();
    }
}
