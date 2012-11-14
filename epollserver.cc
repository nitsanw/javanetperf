/* Based on an epoll benchmark by Yang Zhang:

http://assorted.svn.sourceforge.net/viewvc/assorted/netio-bench/trunk/src/epoll.cc

Reduced to "standard C++" by Evan Jones.
*/

#include <arpa/inet.h>
#include <assert.h>
#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/epoll.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <unistd.h>

#include "fifobuffer.h"

// Wraps the standard assert macro to avoids "unused variable" warnings when compiled away.
// Inspired by: http://powerof2games.com/node/10
// This is not the "default" because it does not conform to the requirements of the C standard,
// which requires that the NDEBUG version be ((void) 0).
#ifdef NDEBUG
#define ASSERT(x) do { (void)sizeof(x); } while(0)
#else
#define ASSERT(x) assert(x)
#endif

class MessageEchoer {
public:
    MessageEchoer(int descriptor) : descriptor_(descriptor), has_length_(0) {}

    ~MessageEchoer() {
        int error = close(descriptor_);
        ASSERT(error == 0);
    }

    bool tryRead() {
        // Read as much data as is available in the socket
        size_t write_buffer_length;
        ssize_t read_bytes;
        //~ printf("%p try read\n", this);
        int read_counter = 0;
        do {
            assert(read_counter == 0);
            read_counter += 1;
            void* write_ptr = buffer_.getWritePosition(&write_buffer_length);
            assert(write_buffer_length > 0);

            read_bytes = ::read(descriptor_, write_ptr, write_buffer_length);
            if (read_bytes > 0) {
                buffer_.advanceWritePosition(read_bytes);
            } else if (read_bytes == 0) {
                // Connection is closed: we are done
                return false;
            } else {
                assert(read_bytes == -1 && errno == EAGAIN);
            }
        } while (read_bytes == static_cast<ssize_t>(write_buffer_length));

        // Try to process as many messages as possible
        while (true) {
            int32_t length;
            if (!has_length_) {
                if (buffer_.available() < static_cast<int>(sizeof(length))) {
                    //~ printf("%p buffer has only %d bytes; need 4 to read length\n", this, buffer_.available());
                    return true;
                }
                int result = buffer_.read(message, sizeof(length));
                ASSERT(result == sizeof(length));
                has_length_ = true;
            }
            memcpy(&length, message, sizeof(length));
            //~ printf("%p has length %d\n", this, length);
            assert(length == 4);
            assert(length <= static_cast<int>(sizeof(message)-sizeof(length)));

            if (buffer_.available() < length) {
                //~ printf("%p buffer has only %d bytes; need %d\n", this, buffer_.available(), length);
                return true;
            }
            int result = buffer_.read(message+sizeof(length), length);
            ASSERT(result == length);

            processMessage(message+sizeof(length), length);

            // Echo the message as the response 
            ssize_t bytes = ::write(descriptor_, message, length+sizeof(length));
            ASSERT(bytes == static_cast<ssize_t>(length+sizeof(length)));
            has_length_ = false;
            //~ printf("%p wrote response\n", this);
        }
    }

    void processMessage(void* data, size_t length) {
        // suppress unused variable warnings
        (void)sizeof(data);
        (void) sizeof(length);
    }

private:
    int descriptor_;
    FifoBuffer buffer_;
    bool has_length_;

    char message[4096];
};

void makeSocketNonBlocking(int socket) {
    int error = fcntl(socket, F_SETFL, O_NONBLOCK | fcntl(socket, F_GETFL, 0));
    ASSERT(error == 0);
}

int main(int argc, char* argv[]) {
    if (argc != 2) {
        fputs("epollserver [port]\n", stderr);
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

    // Create our epoll file descriptor.  max_events is the maximum number of
    // events to process at a time (max number of events that we want a call to
    // epoll_wait() to "return")
    const int max_events = 16;

    // This file descriptor isn't actually bound to any socket; it's a special fd
    // that is really just used for manipulating the epoll (e.g., registering
    // more sockets/connections with it).  TODO: Figure out the rationale behind
    // why this thing is an fd.
    int epoll_fd = epoll_create(max_events);
    assert(epoll_fd >= 0);

    // Add our server fd to the epoll event loop
    makeSocketNonBlocking(server);
    struct epoll_event event;
    memset(&event, 0, sizeof(event));
    event.events = EPOLLIN | EPOLLERR | EPOLLHUP | EPOLLET;
    event.data.ptr = NULL;
    error = epoll_ctl(epoll_fd, EPOLL_CTL_ADD, server, &event);
    assert(error == 0);

    // Execute the epoll event loop
    while (true) {
        struct epoll_event events[max_events];
        int num_fds = epoll_wait(epoll_fd, events, max_events, -1);

        for (int i = 0; i < num_fds; i++) {
            // server is receiving a connection
            if (events[i].data.ptr == NULL) {
                assert(events[i].events & EPOLLIN);

                // Must call accept in a loop to accept all the connections because we are using
                // edge triggered epoll notifications
                while (true) {
                    struct sockaddr remote_addr;
                    socklen_t addr_size = sizeof(remote_addr);
                    int connection = accept(server, &remote_addr, &addr_size);
                    if (connection == -1) {
                        assert(errno == EAGAIN);
                        break;
                    }

                    // Make the connection non-blocking
                    makeSocketNonBlocking(connection);
                    //~ assert(fcntl(connection, F_GETFL, 0) & O_NONBLOCK);

                    // Add the connection to our epoll loop.  Note we are reusing our
                    // epoll_event.  Now we're actually using the ptr field to point to a
                    // free handler.  event.data is a union of {ptr, fd, ...}, so we can
                    // only use one of these.  event.data is entirely for the user; epoll
                    // doesn't actually look at this.  Note that we're passing the fd
                    // (connection) separately into epoll_ctl().
                    MessageEchoer* echoer = new MessageEchoer(connection);
                    //~ printf("accepting connection %d = %p\n", connection, echoer);
                    event.data.ptr = echoer;
                    error = epoll_ctl(epoll_fd, EPOLL_CTL_ADD, connection, &event);
                    assert(error == 0);
                }
            } else {
                // client has data
                assert(events[i].events & EPOLLIN);
                MessageEchoer* echoer = reinterpret_cast<MessageEchoer*>(events[i].data.ptr);
                if (!echoer->tryRead()) {
                    // connection is closed: the destructor will close the epoll fd
                    //~ printf("closing %p\n", echoer);
                    delete echoer;
                }
            }
        }
    }

    error = close(server);
    assert(error == 0);
    return 0;
}
