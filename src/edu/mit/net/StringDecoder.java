package edu.mit.net;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;

public class StringDecoder {
    static final Charset UTF8 = Charset.forName("UTF-8");
    static final int INITIAL_BUFFER_SIZE = 1024;
    private static final int SIZE_ALIGNMENT_BITS = 10;  // = 1024
    private static final int SIZE_ALIGNMENT = 1 << SIZE_ALIGNMENT_BITS;
    private static final int SIZE_ALIGNMENT_MASK = (1 << SIZE_ALIGNMENT_BITS)-1;

    private CharBuffer outBuffer = CharBuffer.allocate(INITIAL_BUFFER_SIZE);

    private final CharsetDecoder decoder = UTF8.newDecoder();

    public StringDecoder() {
        // This matches the default behaviour for String(byte[], "UTF-8");
        // TODO: Support throwing exceptions on invalid UTF-8?
        decoder.onMalformedInput(CodingErrorAction.REPLACE);
    }

    /** Reserve space for the next string that will be <= expectedLength characters long. Must only
    be called when the buffer is empty. */
    public void reserve(int expectedLength) {
        if (expectedLength < 0) {
            throw new IllegalArgumentException(
                    "expectedLength cannot be negative (= " + expectedLength+ ")");
        }
        if (outBuffer.position() != 0) {
            throw new IllegalStateException("cannot be called except after finish()");
        }

        if (expectedLength > outBuffer.capacity()) {
            // Allocate a temporary buffer large enough for this string rounded up
            // TODO: Does this size alignment help at all?
            int desiredLength = expectedLength;
            if ((desiredLength & SIZE_ALIGNMENT_MASK) != 0) {
                // round up
                desiredLength = (expectedLength + SIZE_ALIGNMENT) & ~SIZE_ALIGNMENT_MASK;
            }
            assert desiredLength % SIZE_ALIGNMENT == 0;

            outBuffer = CharBuffer.allocate(desiredLength);
        }
        assert outBuffer.position() == 0;
        assert expectedLength <= outBuffer.capacity();
    }

    public void decode(byte[] source, int offset, int length) {
        decode(source, offset, length, false);
    }

    private void decode(byte[] source, int offset, int length, boolean endOfInput) {
        // TODO: we could cache the input ByteBuffer if source doesn't change
        final ByteBuffer input = ByteBuffer.wrap(source, offset, length);

        // Call decode at least once to pass the endOfInput signal through
        do {
            CoderResult result = decoder.decode(input, outBuffer, endOfInput);
            if (result != CoderResult.UNDERFLOW) {
                // Error handling
                if (result == CoderResult.OVERFLOW) {
                    // double the buffer size and retry
                    CharBuffer next = CharBuffer.allocate(outBuffer.capacity() * 2);
                    System.arraycopy(outBuffer.array(), 0, next.array(), 0, outBuffer.position());
                    next.position(outBuffer.position());
                    assert next.remaining() >= outBuffer.capacity();
                    outBuffer = next;
                } else {
                    // We disable errors in the constructor (replace instead)
                    assert false;
                    // TODO: Are there any unmappable sequences for UTF-8?
                    assert result.isMalformed();
                }
            }
        } while (input.hasRemaining());
        assert !input.hasRemaining();
    }

    public String finish(byte[] source, int offset, int length) {
        decode(source, offset, length, true);

        CoderResult result = decoder.flush(outBuffer);
        if (result == CoderResult.OVERFLOW) {
            throw new RuntimeException("TODO: Handle overflow?");
        } else if (result != CoderResult.UNDERFLOW) {
            throw new RuntimeException("TODO: Handle errors?");
        }
        assert result == CoderResult.UNDERFLOW;

        // Copy out the string
        String out = new String(outBuffer.array(), 0, outBuffer.position());

        // Reset for the next string
        outBuffer.clear();
        decoder.reset();

        return out;
    }
}
