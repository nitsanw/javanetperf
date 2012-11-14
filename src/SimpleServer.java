import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class SimpleServer {
    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = ServerBase.parseArgs("SimpleServer", args);
        Socket client = serverSocket.accept();

        System.out.println("client connected");
        ServerBase.serverClientLoop(client);
        System.out.println("client disconnected");

        serverSocket.close();
    }
}
