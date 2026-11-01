#pragma once
#include <vector>
#include <cassert>
#include <bit>
#include <cstdint>
#include <stdexcept>
#include <algorithm>

using DataVec = std::vector<uint8_t>;

class BitStack {
public:
    BitStack() { reset(); }

    void reset() {
        buffer_.clear();
        free_ = 0;
        read_i_ = 0;
    }

    bool empty() const { return buffer_.empty() && !free_ && !read_i_; }

    void push(size_t val, size_t size) {
        while (size) {
            if (!free_) {
                buffer_.push_back({});
                free_ = 8;
            }

            size_t mask_size = std::min(free_, size);
            free_ -= mask_size;
            size -= mask_size;

            *buffer_.rbegin() <<= mask_size;
            *buffer_.rbegin() |= val & ((size_t{1} << mask_size) - 1);
            val >>= mask_size;
        };
    }

    void push_bytes(const DataVec& vec) {
        assert(!free_);
        buffer_.insert(buffer_.end(), vec.begin(), vec.end());
    }

    size_t read(size_t size) {
        if (size > sizeof(size_t) * 8 || read_i_ > buffer_.size() * 8 || size > buffer_.size() * 8 - read_i_)
            throw std::runtime_error("Truncated legacy bit stream");
        size_t val = 0;
        size_t total_size = size;
        while (size) {
            size_t mask_size = std::min(8 - read_i_ % 8, size);

            size_t shift = (8 - read_i_ % 8) - mask_size;
            val |= static_cast<size_t>((buffer_[read_i_ / 8] >> shift) & ((size_t{1} << mask_size) - 1))
                   << (total_size - size);
            read_i_ += mask_size;
            size -= mask_size;
        };
        return val;
    }

    DataVec read_bytes(size_t size) {
        if (read_i_ % 8 != 0 || read_i_ / 8 > buffer_.size() || size > buffer_.size() - read_i_ / 8)
            throw std::runtime_error("Truncated legacy byte stream");
        if (size == 0) return {};
        DataVec vec(buffer_.data() + read_i_ / 8, buffer_.data() + read_i_ / 8 + size);
        read_i_ += size * 8;
        return vec;
    }

    DataVec buffer_;
    size_t free_;
    size_t read_i_;
};
