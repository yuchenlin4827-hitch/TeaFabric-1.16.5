package javax.sound.sampled;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * A working AudioFormat for the browser (the generated stub threw): Minecraft describes decoded sounds
 * with it (net.minecraft.class_4228, StaticSound, AlUtil). Compiled in the legacy source set because
 * javax.sound belongs to the JDK's java.desktop module.
 */
public class AudioFormat {
    protected Encoding encoding;
    protected float sampleRate;
    protected int sampleSizeInBits;
    protected int channels;
    protected int frameSize;
    protected float frameRate;
    protected boolean bigEndian;
    private Map<String, Object> properties;

    public AudioFormat(Encoding encoding, float sampleRate, int sampleSizeInBits, int channels, int frameSize,
            float frameRate, boolean bigEndian) {
        this.encoding = encoding;
        this.sampleRate = sampleRate;
        this.sampleSizeInBits = sampleSizeInBits;
        this.channels = channels;
        this.frameSize = frameSize;
        this.frameRate = frameRate;
        this.bigEndian = bigEndian;
    }

    public AudioFormat(Encoding encoding, float sampleRate, int sampleSizeInBits, int channels, int frameSize,
            float frameRate, boolean bigEndian, Map<String, Object> properties) {
        this(encoding, sampleRate, sampleSizeInBits, channels, frameSize, frameRate, bigEndian);
        this.properties = new HashMap<>(properties);
    }

    public AudioFormat(float sampleRate, int sampleSizeInBits, int channels, boolean signed, boolean bigEndian) {
        this(signed ? Encoding.PCM_SIGNED : Encoding.PCM_UNSIGNED, sampleRate, sampleSizeInBits, channels,
                channels == -1 || sampleSizeInBits == -1 ? -1 : (sampleSizeInBits + 7) / 8 * channels,
                sampleRate, bigEndian);
    }

    public Encoding getEncoding() {
        return encoding;
    }

    public float getSampleRate() {
        return sampleRate;
    }

    public int getSampleSizeInBits() {
        return sampleSizeInBits;
    }

    public int getChannels() {
        return channels;
    }

    public int getFrameSize() {
        return frameSize;
    }

    public float getFrameRate() {
        return frameRate;
    }

    public boolean isBigEndian() {
        return bigEndian;
    }

    public Map<String, Object> properties() {
        return properties == null ? Collections.<String, Object>emptyMap() : Collections.unmodifiableMap(properties);
    }

    public Object getProperty(String key) {
        return properties == null ? null : properties.get(key);
    }

    public boolean matches(AudioFormat format) {
        return format.getEncoding().equals(encoding)
                && (format.getChannels() == -1 || format.getChannels() == channels)
                && (format.getSampleRate() == -1 || format.getSampleRate() == sampleRate)
                && format.getSampleSizeInBits() == sampleSizeInBits
                && (format.getFrameRate() == -1 || format.getFrameRate() == frameRate)
                && format.getFrameSize() == frameSize
                && (sampleSizeInBits <= 8 || format.isBigEndian() == bigEndian);
    }

    @Override
    public String toString() {
        return encoding + " " + sampleRate + " Hz, " + sampleSizeInBits + " bit, " + channels + " channels, "
                + frameSize + " bytes/frame, " + (bigEndian ? "big-endian" : "little-endian");
    }

    public static class Encoding {
        public static final Encoding PCM_SIGNED = new Encoding("PCM_SIGNED");
        public static final Encoding PCM_UNSIGNED = new Encoding("PCM_UNSIGNED");
        public static final Encoding PCM_FLOAT = new Encoding("PCM_FLOAT");
        public static final Encoding ULAW = new Encoding("ULAW");
        public static final Encoding ALAW = new Encoding("ALAW");

        private final String name;

        public Encoding(String name) {
            this.name = name;
        }

        @Override
        public final boolean equals(Object other) {
            return this == other || other instanceof Encoding && name != null && name.equals(((Encoding) other).name);
        }

        @Override
        public final int hashCode() {
            return name == null ? 0 : name.hashCode();
        }

        @Override
        public final String toString() {
            return name;
        }
    }
}
