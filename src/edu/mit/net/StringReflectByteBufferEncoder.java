package edu.mit.net;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;

public final class StringReflectByteBufferEncoder {
    // Extra "slop" when allocating a new byte buffer: permits the string to
    // contain some extra long UTF-8 characters without needing a new buffer.
    private static final int BUFFER_EXTRA_BYTES = 64;

    private static final Charset UTF8 = Charset.forName("UTF-8");
    private final CharsetEncoder encoder = UTF8.newEncoder();

    public StringReflectByteBufferEncoder() {
        // Needed for U+D800 - U+DBFF = High Surrogate; U+DC00 - U+DFFF = Low Surrogates
        // Maybe others in the future? This is what the JDK does for String.getBytes().
        encoder.onMalformedInput(CodingErrorAction.REPLACE);
        // Not actually needed for UTF-8, but can't hurt
        encoder.onUnmappableCharacter(CodingErrorAction.REPLACE);
    }

    private ByteBuffer makeBiggerBuffer(final CharBuffer inBuffer, ByteBuffer buffer) {
        int charsConverted = inBuffer.position();
        int charsRemaining = inBuffer.remaining();

        // need a larger buffer
        // estimate the average bytes per character from the current sample
        double bytesPerChar;
        if (charsConverted > 0) {
            bytesPerChar = buffer.position() / (double) charsConverted;
        } else {
            // charsConverted can be 0 if the initial buffer is smaller than one character
            bytesPerChar = encoder.averageBytesPerChar();
        }

        assert charsRemaining > 0;
        int bytesRemaining = (int) (charsRemaining * bytesPerChar + 0.5);
        ByteBuffer next = ByteBuffer.allocate(buffer.position() + bytesRemaining +
                BUFFER_EXTRA_BYTES);

        // Copy the current chunk
        // TODO: Use a list of ByteBuffers to avoid copies?
        System.arraycopy(buffer.array(), 0, next.array(), 0, buffer.position());
        next.position(buffer.position());
        return next;
    }

    /** Encodes string into a new ByteBuffer. */
    public ByteBuffer toNewByteBuffer(String source) {
        if (source.length() == 0) return ByteBuffer.allocate(0);
        final CharBuffer inBuffer = ReflectionUtils.getCharBuffer(source);
        ByteBuffer output = ByteBuffer.allocate(source.length() + BUFFER_EXTRA_BYTES);

        while (inBuffer.hasRemaining()) {
            CoderResult result = encoder.encode(inBuffer, output, true);
            if (result == CoderResult.OVERFLOW) {
                assert output.remaining() < encoder.maxBytesPerChar();
                // Make our ByteBuffer bigger
                output = makeBiggerBuffer(inBuffer, output);
            } else {
                assert result == CoderResult.UNDERFLOW;
                assert !inBuffer.hasRemaining();
            }
        }

        CoderResult result = encoder.flush(output);
        if (result == CoderResult.OVERFLOW) {
            // I don't think this can happen with UTF-8 If it does, assert so we can figure it out
            assert false;
            // We attempt to handle it anyway
            output = makeBiggerBuffer(inBuffer, output);
            result = encoder.flush(output);
        }
        assert result == CoderResult.UNDERFLOW;

        // done!
        encoder.reset();
        output.flip();
        return output;
    }
}
