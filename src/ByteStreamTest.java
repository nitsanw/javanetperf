import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;


public class ByteStreamTest {
    private interface ByteStream {
        public void put(byte value) throws IOException;
        public void flush() throws IOException;
    }

    public static double throughput(long start, long end, int bytes) {
        double seconds = (double) (end - start)/ 1000000000.;
        double megabytes = (double) bytes / (double) (1<<20);
        return megabytes / seconds;
    }

    private static final class ByteBufferArrayStream implements ByteStream {
        private final ByteBuffer writeBuffer;
        private int offset = 0;
        private final byte[] buffer;
        private final WritableByteChannel channel;

        public ByteBufferArrayStream(WritableByteChannel channel, int buffer_length) {
            this.writeBuffer = ByteBuffer.allocateDirect(buffer_length);
            buffer = new byte[buffer_length];
            this.channel = channel;
        }

        public void put(byte value) throws IOException {
            if (offset == buffer.length) {
                // "inlining" this makes it go faster on jdk 6 and jdk 7
                //~ flush();
                writeBuffer.clear();
                writeBuffer.put(buffer, 0, offset);
                writeBuffer.flip();
                channel.write(writeBuffer);
                offset = 0;
            }

            buffer[offset] = value;
            offset += 1;
        }

        public void flush() throws IOException {
            writeBuffer.clear();
            writeBuffer.put(buffer, 0, offset);
            assert writeBuffer.position() == offset;
            writeBuffer.flip();
            assert writeBuffer.remaining() == offset;
            channel.write(writeBuffer);
            assert writeBuffer.remaining() == 0;
            offset = 0;
        }
    }

    private static final class MappedByteBufferArrayStream implements ByteStream {
        // This makes things go faster!
        private final MappedByteBuffer writeBuffer;
        private int offset = 0;
        private final byte[] buffer;
        private final WritableByteChannel channel;

        public MappedByteBufferArrayStream(WritableByteChannel channel, int buffer_length) {
            this.writeBuffer = (MappedByteBuffer) ByteBuffer.allocateDirect(buffer_length);
            buffer = new byte[buffer_length];
            this.channel = channel;
        }

        public void put(byte value) throws IOException {
            if (offset == buffer.length) {
                // "inlining" this makes it go faster on jdk 6 and jdk 7
                //~ flush();
                writeBuffer.clear();
                writeBuffer.put(buffer, 0, offset);
                writeBuffer.flip();
                channel.write(writeBuffer);
                offset = 0;
            }

            buffer[offset] = value;
            offset += 1;
        }

        public void flush() throws IOException {
            writeBuffer.clear();
            writeBuffer.put(buffer, 0, offset);
            assert writeBuffer.position() == offset;
            writeBuffer.flip();
            assert writeBuffer.remaining() == offset;
            channel.write(writeBuffer);
            assert writeBuffer.remaining() == 0;
            offset = 0;
        }
    }

    private static final class ByteBufferStream implements ByteStream {
        private final ByteBuffer writeBuffer;
        private final WritableByteChannel channel;

        public ByteBufferStream(WritableByteChannel channel, int buffer_length) {
            this.writeBuffer = ByteBuffer.allocateDirect(buffer_length);
            this.channel = channel;
        }

        public void put(byte value) throws IOException {
            if (writeBuffer.remaining() == 0) {
                // "inlining" this makes it go faster on jdk 6 and jdk 7
                //~ flush();
                writeBuffer.flip();
                channel.write(writeBuffer);
                writeBuffer.clear();
            }

            writeBuffer.put(value);
        }

        public void flush() throws IOException {
            writeBuffer.flip();
            channel.write(writeBuffer);
            writeBuffer.clear();
        }
    }

    private static final class MappedByteBufferStream implements ByteStream {
        // This makes things go faster!
        private final MappedByteBuffer writeBuffer;
        private final WritableByteChannel channel;

        public MappedByteBufferStream(WritableByteChannel channel, int buffer_length) {
            this.writeBuffer = (MappedByteBuffer) ByteBuffer.allocateDirect(buffer_length);
            this.channel = channel;
        }

        public void put(byte value) throws IOException {
            if (writeBuffer.remaining() == 0) {
                // "inlining" this makes it go faster on jdk 6 and jdk 7
                //~ flush();
                writeBuffer.flip();
                channel.write(writeBuffer);
                writeBuffer.clear();
            }

            writeBuffer.put(value);
        }

        public void flush() throws IOException {
            writeBuffer.flip();
            channel.write(writeBuffer);
            writeBuffer.clear();
        }
    }

    private static final class ByteArrayStream implements ByteStream {
        private int offset = 0;
        private final byte[] buffer;
        private final OutputStream stream;

        public ByteArrayStream(OutputStream stream, int buffer_length) {
            buffer = new byte[buffer_length];
            this.stream = stream;
        }

        public void put(byte value) throws IOException {
            if (offset == buffer.length) {
                flush();
            }

            buffer[offset] = value;
            offset += 1;
        }

        public void flush() throws IOException {
            stream.write(buffer, 0, offset);
            offset = 0;
        }
    }

    private static final class NetworkSinkServer implements Runnable {
        private final ServerSocket acceptSocket;

        public NetworkSinkServer() throws IOException {
            acceptSocket = new ServerSocket();
            acceptSocket.bind(new InetSocketAddress(0));
        }

        public void run() {
            final byte[] buffer = new byte[8192];

            while (!acceptSocket.isClosed()) {
                try {
                    Socket socket;
                    try {
                        socket = acceptSocket.accept();
                    } catch (SocketException e) {
                        // This happens when our socket is closed: stop accepting
                        break;
                    }
                    final InputStream in = socket.getInputStream();

                    while (true) {
                        int bytes = in.read(buffer);
                        if (bytes == -1) {
                            break;
                        }
                        assert 0 < bytes && bytes <= buffer.length;
                    }
                    socket.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        public void close() throws IOException {
            acceptSocket.close();
        }

        public int getPort() {
            return acceptSocket.getLocalPort();
        }
    }

    public static void error() {
        System.out.println("[direct|mapped|array|directarray|mappedarray] [size]");
    }

    public static double runTest(ByteStream stream) throws IOException {
        final int BYTES_TO_WRITE = 64 << 20;
        long start = System.nanoTime();
        for (int j = 0; j < BYTES_TO_WRITE; ++j) {
            stream.put((byte) j);
        }
        stream.flush();
        long end = System.nanoTime();

        return throughput(start, end, BYTES_TO_WRITE);
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length != 2) {
            error();
            return;
        }

        int size = Integer.parseInt(args[1]);

        FileOutputStream out = new FileOutputStream("/dev/null");
        //~ FileOutputStream out = new FileOutputStream("/dev/shm/wtf");

        ByteStream stream;
        if (args[0].equals("array")) {
            stream = new ByteArrayStream(out, size);
        } else if (args[0].equals("direct")) {
            stream = new ByteBufferStream(out.getChannel(), size);
        } else if (args[0].equals("mapped")) {
            stream = new MappedByteBufferStream(out.getChannel(), size);
        } else if (args[0].equals("directarray")) {
            stream = new ByteBufferArrayStream(out.getChannel(), size);
        } else if (args[0].equals("mappedarray")) {
            stream = new MappedByteBufferArrayStream(out.getChannel(), size);
        } else {
            error(); return;
        }

        System.out.print("/dev/null: ");
        for (int i = 0; i < 5; ++i) {
            System.out.print(runTest(stream) + " ");
            System.out.flush();
        }
        System.out.println();
        out.close();

        NetworkSinkServer sink = new NetworkSinkServer();
        Thread sinkThread = new Thread(sink);
        sinkThread.start();

        InetSocketAddress otherEnd =
                new InetSocketAddress(InetAddress.getLocalHost(), sink.getPort());
        Socket connection;
        if (args[0].equals("array")) {
            connection = new Socket(otherEnd.getAddress(), otherEnd.getPort());
            stream = new ByteArrayStream(connection.getOutputStream(), size);
        } else {
            SocketChannel channel = SocketChannel.open(otherEnd);
            connection = channel.socket();
            if (args[0].equals("direct")) {
                stream = new ByteBufferStream(channel, size);
            } else if (args[0].equals("mapped")) {
                stream = new MappedByteBufferStream(channel, size);
            } else if (args[0].equals("directarray")) {
                stream = new ByteBufferArrayStream(channel, size);
            } else if (args[0].equals("mappedarray")) {
                stream = new MappedByteBufferArrayStream(channel, size);
            } else {
                error(); return;
            }
        }

        System.out.print("localhost: ");
        for (int i = 0; i < 5; ++i) {
            System.out.print(runTest(stream) + " ");
            System.out.flush();
        }
        System.out.println();
        out.close();
        connection.close();
        sink.close();
        sinkThread.join();
    }
}
