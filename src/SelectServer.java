import java.io.IOException;

import edu.mit.net.NIOMessageListener;

public class SelectServer {
    public static void main(String[] args) throws IOException {
        NIOMessageListener listener = new NIOMessageListener();
        listener.bind(ServerBase.parsePort("SelectServer", args));

        NIOMessageListener.Event e;
        while ((e = listener.blockForNextEvent()) != null) {
            if (e.message == null) {
                // New connection or connection closed: ignore
            } else {
                // Echo back the message
                e.connection.write(e.message);
            }
        }
    }
}
