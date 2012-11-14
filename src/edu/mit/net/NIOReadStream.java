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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayDeque;
import java.util.ArrayList;

/**
Provides a non-blocking stream-like interface on top of the Java NIO ReadableByteChannel. It calls
the underlying read() method only when needed.
*/
public class NIOReadStream {
    NIOReadStream(ReadableByteChannel channel) {
        this.channel = channel;
    }

    /** @returns the number of bytes available to be read. */
    public int dataAvailable() {
        return totalAvailable;
    }

    public int getInt() {
        // TODO: Optimize?
        byte[] intbytes = new byte[4];
        getBytes(intbytes);
        int output = 0;
        for (int i = 0; i < intbytes.length; ++i) {
            output <<= 8;
            output |= ((int)intbytes[4 - i - 1]) & 0xff;
        }
        return output;
    }

    public void getBytes(byte[] output) {
        if (totalAvailable < output.length) {
            throw new IllegalStateException("Requested " + output.length + " bytes; only have "
                    + totalAvailable + " bytes; call tryRead() first");
        }

        int bytesCopied = 0;
        while (bytesCopied < output.length) {
            ByteBuffer first = readBuffers.peekFirst();
            if (first == null) {
                // Steal the write buffer
                writeBuffer.flip();
                readBuffers.add(writeBuffer);
                first = writeBuffer;
                writeBuffer = null;
            }
            assert first.remaining() > 0;

            // Copy bytes from first into output
            int bytesRemaining = first.remaining();
            int bytesToCopy = output.length - bytesCopied;
            if (bytesToCopy > bytesRemaining) bytesToCopy = bytesRemaining;
            first.get(output, bytesCopied, bytesToCopy);
            bytesCopied += bytesToCopy;
            totalAvailable -= bytesToCopy;

            if (first.remaining() == 0) {
                // read an entire block: move it to the empty buffers list
                readBuffers.poll();
                first.clear();
                emptyBuffers.add(first);
            }
        }
    }

    public void close() {
        try {
            channel.close();
        } catch (IOException e) { throw new RuntimeException(e); }
        readBuffers.clear();
        emptyBuffers.clear();
        writeBuffer = null;
    }

    /** Reads until we have at least desiredAvailable bytes buffered, there is no more data, or
    the channel is closed.
    * @returns number of bytes available for reading, or -1 if less than desiredAvailable bytes
    are available, and the channel is closed. */
    public int tryRead(int desiredAvailable) {
        // Read until we have enough, or read returns 0 or -1
        int lastRead = 1;
        while (lastRead > 0 && totalAvailable < desiredAvailable) {
            if (writeBuffer == null) {
                writeBuffer = getEmptyBuffer();
            }
            assert writeBuffer.remaining() > 0;

            try {
                lastRead = channel.read(writeBuffer);
            } catch (IOException e) { throw new RuntimeException(e); }
            if (lastRead > 0) {
                totalAvailable += lastRead;
                if (writeBuffer.remaining() == 0) {
                    writeBuffer.flip();
                    readBuffers.add(writeBuffer);
                    writeBuffer = null;
                }
            }
        }

        if (totalAvailable < desiredAvailable && lastRead == -1) {
            return -1;
        }
        return totalAvailable;
    }

    private ByteBuffer getEmptyBuffer() {
        ByteBuffer buffer;
        int size = emptyBuffers.size();
        if (size == 0) {
            buffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
            buffer.order(ByteOrder.nativeOrder());
        } else {
            buffer = emptyBuffers.get(size-1);
            emptyBuffers.remove(size-1);
        }
        return buffer;
    }

    static final int BUFFER_SIZE = 4096;
    private final ReadableByteChannel channel;
    private final ArrayDeque<ByteBuffer> readBuffers = new ArrayDeque<ByteBuffer>();
    private ByteBuffer writeBuffer = null;
    private final ArrayList<ByteBuffer> emptyBuffers = new ArrayList<ByteBuffer>();
    int totalAvailable = 0;
}
