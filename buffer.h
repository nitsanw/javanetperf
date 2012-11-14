#ifndef BUFFER_H__
#define BUFFER_H__

#include <cassert>
#include <cstring>
#include <vector>

#include "fifobuffer.h"

// A blocking read buffer implementation. It reads data in big chunks from
// descriptor, then copies them out in arbitrary sized chunks via read.
class ReadBuffer {
public:
    ReadBuffer(int descriptor);

    // Blocks until length bytes of data are copied into buffer.
    int read(void* buffer, size_t length);

private:
    int readOnce();

    struct Block {
        Block() : filled(0) {}

        int filled;
        char data[4096];
    };

    int descriptor_;
    FifoBuffer buffer_;
};

#endif
