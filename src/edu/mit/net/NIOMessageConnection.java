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
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

/** Sends and receives blocks of bytes. */
public class NIOMessageConnection implements MessageConnection {
    public NIOMessageConnection(SocketChannel channel) {
        this.channel = channel;
        try {
            channel.socket().setTcpNoDelay(true);
            channel.configureBlocking(false);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        stream = new NIOReadStream(channel);

        writeBuffer = ByteBuffer.allocateDirect(4096);
        writeBuffer.order(ByteOrder.nativeOrder());
    }

    /** Returns a message if one is available. */
    public byte[] tryRead() {
        if (nextLength == 0) {
            int lengthBytes = stream.tryRead(Integer.SIZE/8);
            // connection closed
            if (lengthBytes == -1) return new byte[0];
            // Insufficient bytes
            if (lengthBytes < Integer.SIZE/8) return null;

            nextLength = stream.getInt();
            assert nextLength > 0;
        }
        assert nextLength > 0;

        int messageBytes = stream.tryRead(nextLength);
        // connection closed
        if (messageBytes == -1) return new byte[0];
        if (messageBytes < nextLength) return null;

        byte[] result = new byte[nextLength];
        stream.getBytes(result);
        nextLength = 0;
        return result;
    }

    /** Writes message to the channel. */
    public void write(byte[] message) {
        if (message.length == 0) {
            throw new IllegalArgumentException("message.length == 0: messages must contain data");
        }

        // Copy the size
        writeBuffer.clear();
        writeBuffer.putInt(message.length);

        // Copy the message. This outputs ONE TPC packet per write, which is good for performance.
        if (message.length > writeBuffer.remaining()) {
            // Use writev to avoid copying large messages (and to avoid buffer overflow)
            // TODO(evanj): writev is more expensive than copying for small messages. We should
            // measure to see at which point we should switch to writev, or what size our internal
            // buffer should be.
            writeBuffer.flip();
            ByteBuffer[] buffers = new ByteBuffer[2];
            buffers[0] = writeBuffer;
            buffers[1] = ByteBuffer.wrap(message);
            writeAll(buffers);
        } else {
            writeBuffer.put(message);
            writeBuffer.flip();
            writeAll(writeBuffer);
        }
    }

    /** Registers the channel's read and write events with selector. On a read, call tryRead(). On
    a write, call handleWrite(). */
    public SelectionKey register(Selector selector) {
        try {
            return channel.register(selector, SelectionKey.OP_READ);
        } catch (java.nio.channels.ClosedChannelException e) {
            throw new RuntimeException(e);
        }
    }

    public void close() {
        try {
            channel.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void writeAll(ByteBuffer buffer) {
        assert buffer.remaining() > 0;
        assert buffer.position() == 0;

        try {
            channel.write(buffer);
            if (buffer.remaining() != 0) {
                throw new RuntimeException("write() needed to block! UNSUPPORTED (but should be)");
            }
        } catch (IOException e) { throw new RuntimeException(e); }
    }

    private void writeAll(ByteBuffer[] buffers) {
        long total = 0;
        for (ByteBuffer buffer : buffers) {
            assert buffer.remaining() > 0;
            assert buffer.position() == 0;
            total += buffer.remaining();
        }

        try {
            long count = channel.write(buffers);
            if (count != total) {
                throw new RuntimeException("write() needed to block! TODO(evanj): fix");
            }
        } catch (IOException e) { throw new RuntimeException(e); }
    }

    private final SocketChannel channel;
    private final NIOReadStream stream;
    private final ByteBuffer writeBuffer;
    private int nextLength = 0;
}
