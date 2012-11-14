package edu.mit.net;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;

/*
 * Encodes Strings to UTF-8 more efficiently than using String.getBytes.
 * There are two advantages over the JDK. First, this allocates a buffer that
 * is slightly larger than the string. This is optimized for ASCII, and is
 * much smaller than what the JDK allocates. Second, this uses a ByteBuffer,
 * so it can return a buffer that is not completely full, while
 * String.getBytes must copy the bytes to a new array.
 * 
 * See: http://evanjones.ca/software/java-string-encoding-internals.html
 */
public final class StringByteBufferEncoder {
    static final int CHAR_BUFFER_SIZE = 1024;
    // Extra "slop" when allocating a new byte buffer: permits the string to
    // contain some extra long UTF-8 characters without needing a new buffer.
    private static final int BUFFER_EXTRA_BYTES = 64;

    private static final Charset UTF8 = Charset.forName("UTF-8");
    private final CharBuffer inBuffer = CharBuffer.allocate(CHAR_BUFFER_SIZE);
    private final CharsetEncoder encoder = UTF8.newEncoder();

    public StringByteBufferEncoder() {
        // set the buffer to "filled" so it gets filled by encode()
        inBuffer.position(inBuffer.limit());
        // Needed for U+D800 - U+DBFF = High Surrogate; U+DC00 - U+DFFF = Low Surrogates
        // Maybe others in the future? This is what the JDK does for String.getBytes().
        encoder.onMalformedInput(CodingErrorAction.REPLACE);
        // Not actually needed for UTF-8, but can't hurt
        encoder.onUnmappableCharacter(CodingErrorAction.REPLACE);
    }

    private int readInputChunk(final String source, int readOffset) {
        assert inBuffer.remaining() <= 1;
        assert readOffset <= source.length();

        final char[] inChars = inBuffer.array();

        // We need to get a chunk from the string: Compute the chunk length
        int readLength = source.length() - readOffset;
        if (readLength > inChars.length) {
            readLength = inChars.length;
        }

        // Copy the chunk from the string into our temporary buffer
        source.getChars(readOffset, readOffset + readLength, inChars, 0);
        inBuffer.position(0);
        inBuffer.limit(readLength);
        readOffset += readLength;
        return readOffset;
    }

    private ByteBuffer makeBiggerBuffer(final String source, int readOffset, ByteBuffer buffer) {
        // need a larger buffer
        // estimate the average bytes per character from the current sample
        int charsConverted = getCharsConverted(readOffset);
        double bytesPerChar;
        if (charsConverted > 0) {
            bytesPerChar = buffer.position() / (double) charsConverted;
        } else {
            // charsConverted can be 0 if the initial buffer is smaller than one character
            bytesPerChar = encoder.averageBytesPerChar();
        }

        int charsRemaining = source.length() - charsConverted;
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

    /** Encodes source into a new ByteBuffer. */
    public ByteBuffer toNewByteBuffer(String source) {
        ByteBuffer output = ByteBuffer.allocate(source.length() + BUFFER_EXTRA_BYTES);

        int readOffset = readInputChunk(source, 0);
        while (true) {
            boolean endOfInput = readOffset == source.length();
            CoderResult result = encoder.encode(inBuffer, output, endOfInput);
            if (result == CoderResult.OVERFLOW) {
                assert output.remaining() < encoder.maxBytesPerChar();
                // Make our ByteBuffer bigger
                output = makeBiggerBuffer(source, readOffset, output);
            } else {
                assert result == CoderResult.UNDERFLOW;
                readOffset -= inBuffer.remaining();
                assert readOffset >= 0;

                // If we are done, break. Otherwise, read the next chunk
                if (readOffset == source.length()) break;
                readOffset = readInputChunk(source, readOffset);
            }
        }

        CoderResult result = encoder.flush(output);
        if (result == CoderResult.OVERFLOW) {
            // I don't think this can happen with UTF-8 If it does, assert so we can figure it out
            assert false;
            // We attempt to handle it anyway
            output = makeBiggerBuffer(source, readOffset, output);
            result = encoder.flush(output);
        }
        assert result == CoderResult.UNDERFLOW;

        // reset the input buffer and the encoder, flip the output buffer
        inBuffer.position(0);
        inBuffer.limit(0);
        encoder.reset();
        output.flip();
        return output;
    }

    private int getCharsConverted(int readOffset) {
        int charsConverted = readOffset - inBuffer.remaining();
        assert 0 <= charsConverted && charsConverted <= readOffset;
        return charsConverted;
    }
}
