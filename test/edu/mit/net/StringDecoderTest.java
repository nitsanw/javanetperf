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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;

import org.junit.Before;
import org.junit.Test;


public class StringDecoderTest {
    private StringDecoder decoder;

    @Before
    public void setUp() throws IOException {
        decoder = new StringDecoder();
    }

    @Test(expected=IllegalStateException.class)
    public void testBadReserve() {
        try {
            decoder.reserve(-1);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {}

        decoder.decode(new byte[]{'a'}, 0, 1);
        decoder.reserve(5);
    }

    @Test
    public void testEmpty() {
        final byte[] EMPTY = {};
        assertEquals("", decoder.finish(EMPTY, 0, 0));
        decoder.reserve(0);
        assertEquals("", decoder.finish(EMPTY, 0, 0));
        decoder.decode(EMPTY, 0, 0);
        decoder.decode(EMPTY, 0, 0);
        assertEquals("", decoder.finish(EMPTY, 0, 0));
    }

    @Test
    public void testMultipleParts() {
        decoder.decode(new byte[]{'a', 'b', 'c'}, 1, 2);
        assertEquals("bcde", decoder.finish(new byte[]{'d', 'e', 'f'}, 0, 2));
        assertEquals("b", decoder.finish(new byte[]{'a', 'b', 'c'}, 1, 1));
    }

    @Test
    public void testMultipleOverflow() {
        byte[] buffer = new byte[StringDecoder.INITIAL_BUFFER_SIZE-1];
        for (int i = 0; i < buffer.length; ++i) {
            buffer[i] = 'a';
        }

        decoder.decode(buffer, 0, buffer.length);
        // This triggers an overflow
        decoder.decode(buffer, 0, buffer.length);
        // This also triggers an overflow
        String out = decoder.finish(buffer, 0, buffer.length);

        StringBuilder expected = new StringBuilder(buffer.length * 3);
        for (int i = 0; i < buffer.length * 3; ++i) {
            expected.append('a');
        }
        assertEquals(expected.toString(), out);
    }

    @Test
    public void testReserve() {
        byte[] buffer = new byte[(StringDecoder.INITIAL_BUFFER_SIZE-1)*3];
        for (int i = 0; i < buffer.length; ++i) {
            buffer[i] = 'a';
        }

        decoder.reserve(buffer.length);
        String out = decoder.finish(buffer, 0, buffer.length);

        StringBuilder expected = new StringBuilder(buffer.length);
        for (int i = 0; i < buffer.length; ++i) {
            expected.append('a');
        }
        assertEquals(expected.toString(), out);
    }

    @Test
    public void testIncompleteUTF8Sequence() throws UnsupportedEncodingException {
        // Truncated 3 byte sequence
        byte[] data = {(byte) 0xe2, (byte) 0x89, 0x2e};
        // String(byte[], String) converts this to ? (default replacement string)
        // StringDecoder does as well, by default
        String expected = new String(data, "UTF-8");
        assertEquals(expected, decoder.finish(data, 0, data.length));

        // truncated 3 byte sequence, followed by a 2 byte sequence
        data = new byte[]{(byte) 0xe0, (byte) 0x9f, 0x64};
        expected = new String(data, "UTF-8");
        assertEquals(expected, decoder.finish(data, 0, data.length));
    }

    /** Try all 4 byte values. Warning: the full thing takes 20 minutes to
     * run. The checked in version is small. */
    @Test
    public void testBytePatterns() throws UnsupportedEncodingException {
        // WARNING: it takes 18m45s to test all 4 byte values; we test all two byte patterns
        // final long MAX_PATTERN = (1L << 32);
        final long ONE_PERCENT = (long)(0.01 * (1L << 32));
        final long MAX_PATTERN = 1 << 16;
        ByteBuffer buffer = ByteBuffer.allocate(4);
        for (long i = 0; i < MAX_PATTERN; i++) {
            int intValue = (int) i;
            buffer.clear();
            buffer.putInt(intValue);
            String s1 = new String(buffer.array(), "UTF-8");
            String s2 = decoder.finish(buffer.array(), 0, 4);

            assertEquals(s1, s2);
            if (i != 0 && i % ONE_PERCENT == 0) {
                double percentage = i / (double) MAX_PATTERN * 100.;
                System.out.printf("%.01f%%: %d = %d characters; %s\n", percentage, intValue, s1.length(), s1);
            }
        }
    }
}
