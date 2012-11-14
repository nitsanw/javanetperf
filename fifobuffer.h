#ifndef FIFOBUFFER_H__
#define FIFOBUFFER_H__

#include <cassert>
#include <cstring>
#include <vector>

// A byte buffer that manages an infinite buffer with a "write position" and
// a read position.
// TODO: This requires that blocks are completely filled. This could lead to
// small writes to fill the last chunk of data in the block. We should remove
// this requirement.
class FifoBuffer {
public:
    FifoBuffer();
    ~FifoBuffer();

    // Returns a pointer to the internal buffer where new data should be
    // written. The caller cannot write more than max_length bytes.
    // After copying bytes into the buffer, advanceWritePosition() must be
    // called.
    void* getWritePosition(size_t* max_length);

    // Advances the write position by length bytes. The caller must have
    // filled the bytes pointed to by getWritePosition() with valid data.
    void advanceWritePosition(size_t length);

    // Copies up to length bytes from the read position into buffer. Returns
    // the number of bytes actually copied into buffer.
    int read(void* buffer, size_t length);

    // TODO: Provide a getReadPosition interface to avoid extra copies?

    // Returns the number of bytes available to be read.
    int available() const {
        assert(blocks_.size() > 0);
        return (blocks_.size()-1) * sizeof(blocks_.front()->data) + write_position_;
    }

    bool hasBytesAvailable() const {
        return blocks_.size() > 1 || read_position_ < write_position_;
    }

private:
    // Should be 1 page on most systems
    static const int BLOCK_LENGTH = 4096;
    struct Block {
        char data[BLOCK_LENGTH];
    };

    std::vector<Block*> blocks_;
    int read_position_;
    int write_position_;
};

#endif
