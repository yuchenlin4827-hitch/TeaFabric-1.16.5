package org.lwjgl.stb;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import org.lwjgl.PointerBuffer;

/**
 * Ogg Vorbis decoding is done by the browser (see teafabric.web.audio.WebSoundLoader, wired in by
 * the web mod); the push-data API reports an error so any other caller fails cleanly.
 */
public final class STBVorbis {
    public static final int VORBIS_need_more_data = 1;
    public static final int VORBIS_invalid_api_mixing = 2;

    private STBVorbis() {
    }

    public static long stb_vorbis_open_pushdata(ByteBuffer datablock, IntBuffer datablockMemoryConsumedInBytes,
            IntBuffer error, STBVorbisAlloc allocBuffer) {
        datablockMemoryConsumedInBytes.put(datablockMemoryConsumedInBytes.position(), 0);
        error.put(error.position(), VORBIS_invalid_api_mixing);
        return 0;
    }

    public static int stb_vorbis_decode_frame_pushdata(long f, ByteBuffer datablock, IntBuffer channels,
            PointerBuffer output, IntBuffer samples) {
        return 0;
    }

    public static STBVorbisInfo stb_vorbis_get_info(long f, STBVorbisInfo info) {
        return info;
    }

    public static int stb_vorbis_get_error(long f) {
        return VORBIS_invalid_api_mixing;
    }

    public static void stb_vorbis_close(long f) {
    }
}
