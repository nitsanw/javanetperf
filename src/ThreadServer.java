import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class ThreadServer {
    private static class ClientThread extends Thread {
        public ClientThread(Socket socket) { this.socket = socket; }

        public void run() {
            ServerBase.serverClientLoop(socket);
        }

        private final Socket socket;
    }

    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = ServerBase.parseArgs("ThreadServer", args);

        while (true) {
            Socket client = serverSocket.accept();
            ClientThread thread = new ClientThread(client);
            thread.start();
        }

        //~ serverSocket.close();
    }
}
