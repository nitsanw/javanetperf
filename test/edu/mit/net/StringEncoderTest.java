/*
Copyright (c) 2008
Evan Jones
Massachusetts Institute of Technology

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
*/

package edu.mit.net;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;

import org.junit.Before;
import org.junit.Test;


public class StringEncoderTest {
    private StringEncoder encoder;
    private ByteBuffer destination;
    private byte[] buffer;

    @Before
    public void setUp() throws IOException {
        encoder = new StringEncoder();
        destination = ByteBuffer.allocate(4096);
        buffer = destination.array();
    }

    @Test
    public void testEmpty() {
        assertTrue(encoder.encode("", destination));
        assertEquals(0, destination.position());
        assertTrue(encoder.encode("", destination));
    }

    @Test
    public void testSimpleASCII() {
        buffer[1] = 42;
        assertTrue(encoder.encode("a", destination));
        assertEquals(1, destination.position());
        assertEquals('a', buffer[0]);
        assertEquals(42, buffer[1]);

        destination.position(52);
        destination.limit(52+17);
        assertTrue(encoder.encode("hello", destination));
        assertEquals(52+5, destination.position());
        assertEquals('h', buffer[52]);
        assertEquals('o', buffer[56]);
        assertEquals(0, buffer[57]);
    }

    @Test
    public void testSimpleOverflow() {
        destination.limit(0);
        assertFalse(encoder.encode("hello", destination));
        assertFalse(encoder.encode("hello", destination));
        destination.limit(5);
        destination.position(3);
        assertFalse(encoder.encode("hello", destination));
        assertEquals(5, destination.position());
        assertEquals('h', buffer[3]);
        assertEquals('e', buffer[4]);
        assertEquals(0, buffer[5]);

        destination.limit(1);
        destination.position(0);
        assertFalse(encoder.encode("hello", destination));
        assertEquals(1, destination.position());
        assertEquals('l', buffer[0]);

        destination.clear();
        assertTrue(encoder.encode("hello", destination));
        assertEquals(2, destination.position());
        assertEquals('l', buffer[0]);
        assertEquals('o', buffer[1]);
        assertEquals(0, buffer[2]);
    }

    // UTF-8: e2 89 a2
    private static final char NOT_EQUIVALENT_TO = '\u2262';

    @Test
    public void testMultiByteSequenceOverflow() {
        String out = NOT_EQUIVALENT_TO + ".";
        destination.limit(1);
        assertFalse(encoder.encode(out, destination));
        assertEquals(0, destination.position());
        destination.limit(2);
        assertFalse(encoder.encode(out, destination));
        assertEquals(0, destination.position());
        destination.limit(3);
        assertFalse(encoder.encode(out, destination));
        assertEquals(3, destination.position());
        assertEquals((byte) 0xe2, buffer[0]);
        assertEquals((byte) 0x89, buffer[1]);
        assertEquals((byte) 0xa2, buffer[2]);
        destination.clear();
        assertTrue(encoder.encode(out, destination));
        assertEquals(1, destination.position());
        assertEquals('.', buffer[0]);
    }

    @Test
    public void testSplitSurrogate() throws UnsupportedEncodingException {
        // Build a string with ASCII and one surrogate pair that will be split in half
        StringBuilder build = new StringBuilder(StringEncoder.CHAR_BUFFER_SIZE + 1);
        for (int i = 0; i < StringEncoder.CHAR_BUFFER_SIZE - 1; i++) {
            build.append('A');
        }
        build.append((char) 0xdbff);
        build.append((char) 0xdc00);
        String out = build.toString();
        assertEquals(StringEncoder.CHAR_BUFFER_SIZE + 1, out.length());

        // Encode it
        assertTrue(encoder.encode(out, destination));
        byte[] bytes = Arrays.copyOf(destination.array(), destination.position());
        assertArrayEquals(out.getBytes("UTF-8"), bytes);
    }

    @Test
    public void testReset() {
        String out = NOT_EQUIVALENT_TO + ".";
        destination.limit(1);
        assertFalse(encoder.encode(out, destination));
        assertEquals(0, destination.position());

        encoder.reset();
        assertTrue(encoder.encode("a", destination));
        assertEquals(1, destination.position());
        assertEquals('a', buffer[0]);
    }

    private static final String ALL_CHARS_STRING;
    private static final byte[] ALL_CHARS_STRING_BYTES;
    static {
        final int LAST_CHAR = Character.MAX_CODE_POINT;

        StringBuilder builder = new StringBuilder(LAST_CHAR + 1);
        for (int i = 0; i <= LAST_CHAR; ++i) {
            builder.append(Character.toChars(i));
        }
        ALL_CHARS_STRING = builder.toString();
        try {
            ALL_CHARS_STRING_BYTES = ALL_CHARS_STRING.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testLongString() throws UnsupportedEncodingException, IOException {
        int totalBytes = 0;
        ArrayList<byte[]> bytes = new ArrayList<byte[]>();

        int rounds = 0;
        boolean done = false;
        while (!done) {
            done = encoder.encode(ALL_CHARS_STRING, destination);
            totalBytes += destination.position();
            bytes.add(Arrays.copyOf(destination.array(), destination.position()));
            destination.clear();
            rounds += 1;
        }
        assertTrue(rounds > 1);

        byte[] output = new byte[totalBytes];
        int offset = 0;
        for (byte[] b : bytes) {
            System.arraycopy(b, 0, output, offset, b.length);
            offset += b.length;
        }

        assertArrayEquals(ALL_CHARS_STRING_BYTES, output);
    }

    @Test
    public void testLongStringByteBuffer() {
        ByteBuffer out = encoder.toNewByteBuffer(ALL_CHARS_STRING);
        byte[] b = Arrays.copyOf(out.array(), out.limit());
        assertArrayEquals(ALL_CHARS_STRING_BYTES, b);
    }

    @Test
    public void testLongStringByteArray() {
        byte[] out = encoder.toNewArray(ALL_CHARS_STRING);
        assertArrayEquals(ALL_CHARS_STRING_BYTES, out);
    }

    private static void assertByteBufferEquals(byte[] original, ByteBuffer other) {
        assertEquals(original.length, other.remaining());
        for (int j = 0; j < original.length; j++) {
            assertEquals(original[j], other.array()[j]);
        }
    }

    /** Try to encode all Unicode code points; compare with String.getBytes("UTF-8") */
    @Test
    public void testAllCodePoints() throws UnsupportedEncodingException {
        double maxBytesPerChar = -1;
        ByteBuffer out = ByteBuffer.allocate(4);
        for (int i = 0; i <= Character.MAX_CODE_POINT; i++) {
            String s = new String(Character.toChars(i));
            assert 0 < s.length() && s.length() <= 2;
            byte[] utf8 = s.getBytes("UTF-8");

            boolean complete = encoder.encode(s, out);
            assertTrue(complete);
            out.flip();
            assertByteBufferEquals(utf8, out);
            out.clear();

            byte[] utf8_temp = encoder.toNewArray(s);
            assertArrayEquals(utf8, utf8_temp);

            ByteBuffer other = encoder.toNewByteBuffer(s);
            assertByteBufferEquals(utf8, other);

            double bytesPerChar = utf8.length / (double) s.length();
            if (bytesPerChar > maxBytesPerChar) {
                maxBytesPerChar = bytesPerChar;
            }
        }

        assertEquals(maxBytesPerChar, StringEncoder.UTF8_MAX_BYTES_PER_CHAR, 0);
    }

    /** Try to encode all UTF-16 combinations, some of which are invalid. */
    @Test
    public void testAllTwoChars() throws UnsupportedEncodingException {
        ByteBuffer out = ByteBuffer.allocate(6);
        char[] array = new char[2];

        // WARNING: it takes 18m45s to test all 2 character values; we test all two byte patterns
        //~ final long MAX_PATTERN = (1L << 32);
        final long ONE_PERCENT = (long)(0.01 * (1L << 32));
        final long MAX_PATTERN = 10 << 16;
        for (long i = 0; i < MAX_PATTERN; i++) {
            array[0] = (char) ((i >> 16) & (0xffffL));
            array[1] = (char) (i & (0xffffL));

            String s = new String(array);
            byte[] utf8 = s.getBytes("UTF-8");
            boolean complete = encoder.encode(s, out);
            assertTrue(complete);
            assertEquals(utf8.length, out.position());
            for (int j = 0; j < utf8.length; j++) {
                assertEquals(utf8[j], out.array()[j]);
            }
            out.clear();

            if (i != 0 && i % ONE_PERCENT == 0) {
                double percentage = i / (double) MAX_PATTERN * 100.;
                System.out.printf("%.01f%%: %d = %d characters; %s\n",
                        percentage, i, s.length(), s);
            }
        }
    }
}
