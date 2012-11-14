#include "buffer.h"

#include <cassert>

#include <unistd.h>

using std::vector;

ReadBuffer::ReadBuffer(int descriptor) : descriptor_(descriptor) {
    assert(descriptor_ >= 0);
}

int ReadBuffer::read(void* buffer, size_t length) {
    // If we don't have enough data buffered, read some data
    // TODO: It would be better to copy data out of our buffer first, to avoid
    // unneeded allocations. It would also be better to read directly into
    // buffer, if the buffer is "big enough."
    while (buffer_.available() < static_cast<int>(length)) {
        int bytes = readOnce();
        if (bytes <= 0) return bytes;
    }

    int bytes_copied = buffer_.read(buffer, length);
    assert(bytes_copied == static_cast<int>(length));
    return bytes_copied;
}

int ReadBuffer::readOnce() {
    size_t length;
    void* write_ptr = buffer_.getWritePosition(&length);

    ssize_t bytes = ::read(descriptor_, write_ptr, length);
    if (bytes > 0) {
        buffer_.advanceWritePosition(bytes);
    }

    return bytes;
}
