import java.net.ServerSocket;
import java.net.Socket;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;

public class ServerBase {
    public static int bytesToInt(byte[] array, int offset) {
        int length = ((int) array[offset + 3] & 0xff) << 24;
        length |= ((int) array[offset + 2] & 0xff) << 16;
        length |= ((int) array[offset + 1] & 0xff) << 8;
        length |= ((int) array[offset + 0] & 0xff) << 0;
        return length;
    }

    public static void intToBytes(int value, byte[] array, int offset) {
        array[offset] = (byte) (value & 0xff);
        value >>= 8;
        array[offset+1] = (byte) (value & 0xff);
        value >>= 8;
        array[offset+2] = (byte) (value & 0xff);
        value >>= 8;
        array[offset+3] = (byte) (value & 0xff);
    }

    public static int parsePort(String name, String[] args) {
        if (args.length != 1) {
            System.err.println(name + " [listen port]");
            System.exit(1);
        }

        return Integer.parseInt(args[0]);
    }

    public static ServerSocket parseArgs(String name, String[] args) {
        int port = parsePort(name, args);
        try {
            return new ServerSocket(port);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static class Client {
        public Client(Socket socket) {
            try {
                this.socket = socket;
                this.socket.setTcpNoDelay(true);
                read = new BufferedInputStream(socket.getInputStream());
                write = new BufferedOutputStream(socket.getOutputStream());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        /** @return true if the request was read correctly, false if the connection closed. */
        public boolean readMessage() {
            try {
                int bytes = read.read(lengthBytes, 0, lengthBytes.length);
                if (bytes != lengthBytes.length) return false;

                // Allocate a message if needed
                int length = bytesToInt(lengthBytes, 0);
                if (length != message.length) {
                    message = new byte[length];
                }

                bytes = read.read(message, 0, message.length);
                return bytes == message.length;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        public void writeMessage() {
            try {
                write.write(lengthBytes, 0, lengthBytes.length);
                write.write(message, 0, message.length);
                write.flush();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        public void setMessage(byte[] message) {
            intToBytes(message.length, lengthBytes, 0);
            this.message = message;
        }

        private final Socket socket;
        private final BufferedInputStream read;
        private final BufferedOutputStream write;
        private final byte[] lengthBytes = new byte[4];
        // Allocate a zero byte array to avoid an NPE on first call to readMessage
        private byte[] message = new byte[0];
    }

    public static void serverClientLoop(Socket socket) {
        Client c = new Client(socket);

        while (true) {
            if (!c.readMessage()) break;
            c.writeMessage();
        }
    }
}
