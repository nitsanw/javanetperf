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
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayDeque;
import java.util.Iterator;

/** Listens for client connections. */
public class NIOMessageListener implements MessageListener {
    public NIOMessageListener() {
        try {
            // Create a socket for listening to client requests, register it with a selector
            server = ServerSocketChannel.open();
            server.configureBlocking(false);
            selector = Selector.open();
        } catch (IOException e) { throw new RuntimeException(e); }
    }

    public void bind(int port) {
        try {
            server.socket().bind(new InetSocketAddress(port));
            SelectionKey serverKey = server.register(selector, SelectionKey.OP_ACCEPT);
            serverKey.attach(server);
        } catch (IOException e) { throw new RuntimeException(e); }
    }

    public int getLocalPort() { return server.socket().getLocalPort(); }

    /** @returns the next event from the client connections. */
    public Event getNextEvent() {
        try {
            Event e = eventQueue.poll();
            if (e == null) {
                if (selector.selectNow() != 0) {
                    handleSelectedKeys();
                    e = eventQueue.poll();
                }
            }
            return e;
        } catch (IOException e) { throw new RuntimeException(e); }
    }

    /** @returns the next event from the client connections. */
    public Event blockForNextEvent() {
        try {
            Event e;
            while ((e = eventQueue.poll()) == null) {
                // select could return 0 if we are woken or interrupted. In that case, return null
                if (selector.select() == 0) {
                    return null;
                }

                // this might not create an event if it is a partial message read
                handleSelectedKeys();
            }
            return e;
        } catch (IOException e) { throw new RuntimeException(e); }
    }

    public void close() {
        try {
            for (SelectionKey key : selector.keys()) {
                if (key.attachment() != server) {
                    MessageConnection connection = (MessageConnection) key.attachment();
                    connection.close();
                }
            }
            selector.close();
            server.close();
            eventQueue.clear();
        } catch (IOException e) { throw new RuntimeException(e); }
    }

    private void handleSelectedKeys() throws IOException {
        for (Iterator<SelectionKey> it = selector.selectedKeys().iterator(); it.hasNext(); ) {
            SelectionKey key = it.next();
            it.remove();

            if (key.attachment() == server) {
                assert key.isAcceptable();
                SocketChannel client = server.accept();
                assert client != null;
                MessageConnection connection = new NIOMessageConnection(client);
                SelectionKey clientKey = connection.register(selector);
                clientKey.attach(connection);
                eventQueue.add(new Event(connection, null));
            } else {
                assert key.isReadable();
                MessageConnection connection = (MessageConnection) key.attachment();
                byte[] data;
                while ((data = connection.tryRead()) != null) {
                    if (data.length == 0) {
                        // Connection closed
                        connection.close();
                        eventQueue.add(new Event(connection, null));
                        break;
                    } else {
                        eventQueue.add(new Event(connection, data));
                    }
                }
            }
        }
    }

    private final ServerSocketChannel server;
    private final Selector selector;
    private final ArrayDeque<Event> eventQueue =  new ArrayDeque<Event>();
}
