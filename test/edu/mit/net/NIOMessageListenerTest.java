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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;

import org.junit.Before;
import org.junit.Test;

public class NIOMessageListenerTest {
    NIOMessageListener listener;
    NIOMessageListener.Event e;

    @Before
    public void setUp() throws IOException {
        listener = new NIOMessageListener();
        listener.bind(0);
    }

    private Socket connectClient() throws IOException {
        return new Socket(InetAddress.getByName(null), listener.getLocalPort());
    }

    private void macosxNIOHack() {
        try {
            // This is needed on Mac OS X to ensure that the data gets to the other side.
            // getOutputStream.flush() and Thread.yield() were not sufficient.
            Thread.sleep(1);
        } catch (InterruptedException e) { throw new RuntimeException(e); }
    }

    private static final byte MESSAGE_BYTE = 1;
    private static final byte[] MESSAGE = new byte[]{1, 0, 0, 0, MESSAGE_BYTE};
    private void writeMessage(Socket socket) throws IOException {
        write(socket, MESSAGE);
    }
    private void write(Socket socket, byte[] bytes) throws IOException {
        socket.getOutputStream().write(bytes);
        macosxNIOHack();
    }

    private void assertIsMessage(byte[] message) {
        assertEquals(1, message.length);
        assertEquals(MESSAGE_BYTE, message[0]);
    }

    @Test
    public void testNothing() {
        assertNull(listener.getNextEvent());
        assertNull(listener.getNextEvent());
    }

    @Test
    public void testSingle() throws IOException {
        Socket client = connectClient();
        e = listener.getNextEvent();
        MessageConnection connection = e.connection;
        assertNotNull(connection);
        assertNull(e.message);

        assertNull(listener.getNextEvent());

        writeMessage(client);
        e = listener.getNextEvent();
        assertEquals(connection, e.connection);
        assertIsMessage(e.message);

        assertNull(listener.getNextEvent());

        client.close();
        macosxNIOHack();
        e = listener.getNextEvent();
        assertEquals(connection, e.connection);
        assertNull(e.message);
    }

    @Test
    public void testPollMultiple() throws IOException {
        Socket client = connectClient();
        assertNotNull(listener.getNextEvent());

        // Write two messages in one go
        write(client, new byte[] { 1, 0, 0, 0, 42, 2, 0, 0, 0, 1, 2 });
        e = listener.getNextEvent();
        assertEquals(1, e.message.length);
        assertEquals(42, e.message[0]);

        e = listener.getNextEvent();
        assertEquals(2, e.message.length);
        assertEquals(2, e.message[1]);
    }

    @Test
    public void testClose() throws IOException {
        Socket client = connectClient();
        assertNotNull(listener.getNextEvent());

        // Close should close the listener, as well as all connected sockets
        listener.close();
        int bytes = client.getInputStream().read(new byte[4096]);
        assertEquals(-1, bytes);
    }

    @Test
    public void testBlockForNextPartMessage() throws IOException {
        final Socket client = connectClient();
        assertNotNull(listener.getNextEvent());

        // Write part of a message
        write(client, new byte[]{4, 0, 0, 0, 0, 1});

        // Write the rest of the message in another thread, after a delay
        Thread writer = new Thread() {
            public void run() {
                try {
                    Thread.sleep(100);
                    write(client, new byte[]{2, 3});
                } catch (Exception e) { throw new RuntimeException(e); }
            }
        };
        writer.start();
        e = listener.blockForNextEvent();
        assertEquals(4, e.message.length);
    }
}
