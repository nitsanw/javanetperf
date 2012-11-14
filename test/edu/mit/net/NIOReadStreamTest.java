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

import java.nio.channels.ReadableByteChannel;
import java.nio.ByteBuffer;

import org.junit.Before;
import org.junit.Test;

public class NIOReadStreamTest {
    MockReadableByteChannel channel;
    NIOReadStream stream;

    private static class MockReadableByteChannel implements ReadableByteChannel {
        public int read(ByteBuffer buffer) {
            if (nextRead == null) {
                if (end) return -1;
                return 0;
            }

            byte[] target = nextRead;
            nextRead = null;
            int length = target.length;

            // If we have too much data, split the nextRead array
            if (length > buffer.remaining()) {
                length = buffer.remaining();
                nextRead = new byte[target.length-length];
                for (int i = 0; i < nextRead.length; ++i) {
                    nextRead[i] = target[i + length];
                }
            }

            buffer.put(target, 0, length);
            return length;
        }

        public boolean isOpen() { return !closed; }
        public void close() {
            assert !closed;
            closed = true;
            end = true;
        }

        public byte[] nextRead;
        public boolean end = false;
        private boolean closed = false;
    }

    @Before
    public void setUp() {
        channel = new MockReadableByteChannel();
        stream = new NIOReadStream(channel);
    }

    @Test
    public void testZeroLength() {
        assertEquals(0, stream.dataAvailable());
        byte[] empty = new byte[0];
        stream.getBytes(empty);
    }

    @Test
    public void testEmpty() {
        byte[] foo = new byte[1];
        try {
            stream.getBytes(foo);
            fail("expected IllegalStateException");
        } catch (IllegalStateException e) {}
    }

    @Test
    public void testMultipleOneRead() {
        channel.nextRead = new byte[]{0, 1, 2, 3,};
        assertEquals(4, stream.tryRead(1));
        assertEquals(4, stream.dataAvailable());
        byte[] single = new byte[1];
        for (int i = 0; i < 4; ++i) {
            stream.getBytes(single);
            assertEquals(i, single[0]);
        }
    }

    @Test
    public void testSpanRead() {
        // Write a block that spans multiple buffers
        final int SIZE = 4096*5;
        channel.nextRead = new byte[SIZE];
        channel.nextRead[0] = 42;
        channel.nextRead[SIZE-1] = 79;

        // Read a byte off the beginning
        assertTrue(stream.tryRead(1) >= 1);
        assertTrue(1 <= stream.dataAvailable() && stream.dataAvailable() < SIZE);
        byte[] single = new byte[1];
        stream.getBytes(single);
        assertEquals(42, single[0]);

        // Read most of the block
        byte[] most = new byte[SIZE-2];
        assertEquals(SIZE-1, stream.tryRead(most.length));
        assertEquals(SIZE-1, stream.dataAvailable());
        stream.getBytes(most);

        // Read the byte off the end
        stream.getBytes(single);
        assertEquals(79, single[0]);
        assertStreamIsEmpty();
    }

    @Test
    public void testMultipleReadsOneValue() {
        final int HUGE_SIZE = 4096*16;
        byte[] huge = new byte[HUGE_SIZE];
        channel.nextRead = huge;
        assertEquals(HUGE_SIZE, stream.tryRead(HUGE_SIZE));
        assertEquals(HUGE_SIZE, stream.dataAvailable());
        stream.getBytes(huge);
        assertStreamIsEmpty();
    }

    @Test
    public void testIncompleteReads() {
        channel.nextRead = new byte[1000];
        assertEquals(1000, stream.tryRead(1500));
        assertEquals(1000, stream.dataAvailable());
        channel.nextRead = new byte[500];
        assertEquals(1500, stream.tryRead(1500));
        assertEquals(1500, stream.dataAvailable());
    }

    @Test
    public void testReadInt() {
        channel.nextRead = new byte[]{1, 2, 3, 4};
        assertEquals(4, stream.tryRead(4));
        int value = stream.getInt();
        assertEquals(0x04030201, value);

        channel.nextRead = new byte[]{-4, -1, -1, -1};
        assertEquals(4, stream.tryRead(4));
        assertEquals(-4, stream.getInt());

        channel.nextRead = new byte[]{-4, 0, 0, 0};
        assertEquals(4, stream.tryRead(4));
        assertEquals(252, stream.getInt());
    }

    @Test
    public void testEndReadComplete() {
        channel.nextRead = new byte[]{1, 2, 3,4 };
        channel.end = true;
        assertEquals(4, stream.tryRead(1));
        assertEquals(-1, stream.tryRead(42));
    }

    @Test
    public void testEndReadIncomplete() {
        channel.nextRead = new byte[]{1, 2, 3,4 };
        channel.end = true;
        assertEquals(-1, stream.tryRead(42));
    }

    @Test
    public void testClose() {
        assertFalse(channel.closed);
        stream.close();
        assertTrue(channel.closed);
    }

    private void assertStreamIsEmpty() {
        assertEquals(0, stream.tryRead(Integer.MAX_VALUE));
        assertEquals(0, stream.dataAvailable());
    }
}
