package io.netty.handler.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import java.util.ArrayList;
import java.util.List;

/**
 * Calls the protected encode/decode methods of Netty codecs directly. Protocol translators (ViaVersion's
 * {@code PipelineUtil}) reach them through reflection, which TeaVM does not have; their call sites are
 * redirected here (see redirects.txt). It lives in Netty's package so that the protected members are
 * accessible.
 */
public final class TeaFabricCodecs {
    private TeaFabricCodecs() {
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static List<Object> callDecode(ByteToMessageDecoder decoder, ChannelHandlerContext ctx, Object input)
            throws Exception {
        List<Object> output = new ArrayList<>();
        decoder.decode(ctx, (ByteBuf) input, output);
        return output;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void callEncode(MessageToByteEncoder encoder, ChannelHandlerContext ctx, Object msg, ByteBuf output)
            throws Exception {
        encoder.encode(ctx, msg, output);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static List<Object> callDecode(MessageToMessageDecoder decoder, ChannelHandlerContext ctx, Object msg)
            throws Exception {
        List<Object> output = new ArrayList<>();
        decoder.decode(ctx, msg, output);
        return output;
    }
}
