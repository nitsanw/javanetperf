#include <arpa/inet.h>
#include <assert.h>
#include <pthread.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <unistd.h>

#include "buffer.h"

// Wraps the standard assert macro to avoids "unused variable" warnings when compiled away.
// Inspired by: http://powerof2games.com/node/10
// This is not the "default" because it does not conform to the requirements of the C standard,
// which requires that the NDEBUG version be ((void) 0).
#ifdef NDEBUG
#define ASSERT(x) do { (void)sizeof(x); } while(0)
#else
#define ASSERT(x) assert(x)
#endif

void* serverThread(void* argument) {
    int socket = reinterpret_cast<intptr_t>(argument);
    assert(socket >= 0);

    ReadBuffer buffer(socket);
    char message[4096];
    while (true) {
        // Read the length into the buffer
        int32_t length;
        int error = buffer.read(message, sizeof(length));
        if (error == 0) break;
        assert(error == sizeof(length));
        memcpy(&length, message, sizeof(length));

        // Read the message
        assert(length > 0);
        assert(length + sizeof(length) <= sizeof(message));
        error = buffer.read(message+sizeof(length), length);
        assert(error == length);

        // Write the message back to the client
        error = write(socket, message, length+sizeof(length));
        assert(error == static_cast<int>(length+sizeof(length)));
    }

    int error = close(socket);
    ASSERT(error == 0);

    return NULL;
}

int main(int argc, const char* argv[]) {
    if (argc != 2) {
        fputs("threadserver [port]\n", stderr);
        return 1;
    }
    int port = atoi(argv[1]);
    assert(0 < port && port < 1 << 16);

    // Bind to port
    int server = socket(PF_INET, SOCK_STREAM, 0);
    assert(server > 0);
    struct sockaddr_in addr;
    memset(&addr, 0, sizeof(addr));
    addr.sin_family = AF_INET;
    addr.sin_addr.s_addr = INADDR_ANY;
    addr.sin_port = htons(port);
    int error = bind(server, (struct sockaddr*) &addr, sizeof(addr));
    assert(error == 0);
    error = listen(server, 2048);
    assert(error == 0);

    // Spawn client threads in the "detached" mode
    pthread_attr_t attributes;
    error = pthread_attr_init(&attributes);
    assert(error == 0);
    error = pthread_attr_setdetachstate(&attributes, PTHREAD_CREATE_DETACHED);
    assert(error == 0);

    // wait for connections
    while (true) {
        struct sockaddr_in client_addr;
        socklen_t client_addr_len = sizeof(client_addr);
        int client_socket = accept(server, (struct sockaddr*) &client_addr, &client_addr_len);
        assert(client_socket >= 0);

        // Spawn a thread for the connection
        pthread_t pid;
        int error = pthread_create(&pid, &attributes, serverThread, (void*) client_socket);
        ASSERT(error == 0);
    }

    error = pthread_attr_destroy(&attributes);
    assert(error == 0);

    error = close(server);
    assert(error == 0);

    return 0;
}

//~ public class ServerBase {
    //~ public static int bytesToInt(byte[] array, int offset) {
        //~ int length = ((int) array[offset + 3] & 0xff) << 24;
        //~ length |= ((int) array[offset + 2] & 0xff) << 16;
        //~ length |= ((int) array[offset + 1] & 0xff) << 8;
        //~ length |= ((int) array[offset + 0] & 0xff) << 0;
        //~ return length;
    //~ }

    //~ public static void intToBytes(int value, byte[] array, int offset) {
        //~ array[offset] = (byte) (value & 0xff);
        //~ value >>= 8;
        //~ array[offset+1] = (byte) (value & 0xff);
        //~ value >>= 8;
        //~ array[offset+2] = (byte) (value & 0xff);
        //~ value >>= 8;
        //~ array[offset+3] = (byte) (value & 0xff);
    //~ }

    //~ public static int parsePort(String name, String[] args) {
        //~ if (args.length != 1) {
            //~ System.err.println(name + " [listen port]");
            //~ System.exit(1);
        //~ }

        //~ return Integer.parseInt(args[0]);
    //~ }

    //~ public static ServerSocket parseArgs(String name, String[] args) {
        //~ int port = parsePort(name, args);
        //~ try {
            //~ return new ServerSocket(port);
        //~ } catch (IOException e) {
            //~ throw new RuntimeException(e);
        //~ }
    //~ }

    //~ public static class Client {
        //~ public Client(Socket socket) {
            //~ try {
                //~ this.socket = socket;
                //~ this.socket.setTcpNoDelay(true);
                //~ read = new BufferedInputStream(socket.getInputStream());
                //~ write = new BufferedOutputStream(socket.getOutputStream());
            //~ } catch (IOException e) {
                //~ throw new RuntimeException(e);
            //~ }
        //~ }

        //~ /** @return true if the request was read correctly, false if the connection closed. */
        //~ public boolean readMessage() {
            //~ try {
                //~ int bytes = read.read(lengthBytes, 0, lengthBytes.length);
                //~ if (bytes != lengthBytes.length) return false;

                //~ int length = bytesToInt(lengthBytes, 0);

                //~ message = new byte[length];
                //~ bytes = read.read(message, 0, message.length);
                //~ return bytes == message.length;
            //~ } catch (IOException e) {
                //~ throw new RuntimeException(e);
            //~ }
        //~ }

        //~ public void writeMessage() {
            //~ try {
                //~ write.write(lengthBytes, 0, lengthBytes.length);
                //~ write.write(message, 0, message.length);
                //~ write.flush();
            //~ } catch (IOException e) {
                //~ throw new RuntimeException(e);
            //~ }
        //~ }

        //~ public void setMessage(byte[] message) {
            //~ intToBytes(message.length, lengthBytes, 0);
            //~ this.message = message;
        //~ }

        //~ private final Socket socket;
        //~ private final BufferedInputStream read;
        //~ private final BufferedOutputStream write;
        //~ private final byte[] lengthBytes = new byte[4];
        //~ private byte[] message = null;
    //~ }

    //~ public static void serverClientLoop(Socket socket) {
        //~ Client c = new Client(socket);

        //~ while (true) {
            //~ if (!c.readMessage()) break;
            //~ c.writeMessage();
        //~ }
    //~ }
//~ }
