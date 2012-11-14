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

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;

import org.junit.Before;
import org.junit.Test;


public class StringByteBufferEncoderTest {
    private StringByteBufferEncoder encoder;
    private ByteBuffer destination;

    @Before
    public void setUp() throws IOException {
        encoder = new StringByteBufferEncoder();
    }

    @Test
    public void testEmpty() {
        destination = encoder.toNewByteBuffer("");
        assertEquals(0, destination.remaining());
    }

    private static void assertMatch(String s, ByteBuffer b) {
        byte[] bytes;
        try {
            bytes = s.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
        assertEquals(bytes.length, b.remaining());
        for (int i = 0; i < bytes.length; i++) {
            assertEquals(bytes[i], b.get());
        }
    }

    @Test
    public void testSimpleASCII() {
        destination = encoder.toNewByteBuffer("a");
        assertMatch("a", destination);
    }

    @Test
    public void testSplitSurrogate() throws UnsupportedEncodingException {
        // Build a string with ASCII and one surrogate pair that will be split in half
        StringBuilder build = new StringBuilder(StringByteBufferEncoder.CHAR_BUFFER_SIZE + 1);
        for (int i = 0; i < StringEncoder.CHAR_BUFFER_SIZE - 1; i++) {
            build.append('A');
        }
        build.append((char) 0xdbff);
        build.append((char) 0xdc00);
        String out = build.toString();
        assertEquals(StringEncoder.CHAR_BUFFER_SIZE + 1, out.length());

        // Encode it
        destination = encoder.toNewByteBuffer(out);
        assertMatch(out, destination);
    }

    private static final String ALL_CHARS_STRING;
    static {
        final int LAST_CHAR = Character.MAX_CODE_POINT;

        StringBuilder builder = new StringBuilder(LAST_CHAR + 1);
        for (int i = 0; i <= LAST_CHAR; ++i) {
            builder.append(Character.toChars(i));
        }
        ALL_CHARS_STRING = builder.toString();
    }

    @Test
    public void testLongStringByteBuffer() {
        ByteBuffer out = encoder.toNewByteBuffer(ALL_CHARS_STRING);
        assertMatch(ALL_CHARS_STRING, out);
    }

    /** Try to encode all Unicode code points; compare with String.getBytes("UTF-8") */
    @Test
    public void testAllCodePoints() throws UnsupportedEncodingException {
        for (int i = 0; i <= Character.MAX_CODE_POINT; i++) {
            String s = new String(Character.toChars(i));

            ByteBuffer other = encoder.toNewByteBuffer(s);
            assertMatch(s, other);
        }
    }
}
