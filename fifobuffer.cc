#include "buffer.h"

#include <cassert>

#include <unistd.h>

using std::vector;

FifoBuffer::FifoBuffer() : read_position_(0), write_position_(0) {
    blocks_.push_back(new Block());
}

FifoBuffer::~FifoBuffer() {
    for (size_t i = 0; i < blocks_.size(); ++i) {
        delete blocks_[i];
    }
}

void* FifoBuffer::getWritePosition(size_t* max_length) {
    Block* current = blocks_.back();
    int remaining = static_cast<int>(sizeof(current->data)) - write_position_;
    assert(remaining >= 0);
    if (remaining == 0) {
        current = new Block();
        blocks_.push_back(current);
        write_position_ = 0;
        remaining = sizeof(current->data);
    }

    char* end = current->data + write_position_;
    *max_length = remaining;
    return end;
}

void FifoBuffer::advanceWritePosition(size_t length) {
    write_position_ += length;
    assert(write_position_ <= static_cast<int>(sizeof(blocks_.back()->data)));
}

int FifoBuffer::read(void* buffer, size_t length) {
    int bytes_copied = 0;
    char* current = reinterpret_cast<char*>(buffer);

    while (bytes_copied < static_cast<int>(length) && hasBytesAvailable()) {
        // Get the number of bytes remaining in the next block
        int remaining_in_block;
        if (blocks_.size() == 1) {
            remaining_in_block = write_position_ - read_position_;
        } else {
            assert(blocks_.size() > 1);
            remaining_in_block = sizeof(blocks_.front()->data) - read_position_;
        }
        assert(remaining_in_block > 0);

        int to_copy = std::min(static_cast<int>(length) - bytes_copied, remaining_in_block);

        memcpy(current, blocks_.front()->data + read_position_, to_copy);
        current += to_copy;
        read_position_ += to_copy;
        bytes_copied += to_copy;

        if (to_copy == remaining_in_block) {
            // Consumed an entire block: remove it
            if (blocks_.size() > 1) {
                assert(read_position_ == sizeof(blocks_.front()->data));
                delete blocks_.front();
                blocks_.erase(blocks_.begin());
            } else {
                // Last block: just reset the counter
                assert(blocks_.size() == 1);
                assert(read_position_ == write_position_);
                write_position_ = 0;
            }
            read_position_ = 0;
        }
    }

    assert(0 <= bytes_copied && bytes_copied <= static_cast<int>(length));
    return bytes_copied;
}
