#pragma once
#include <vector>
#include <cassert>
#include <bit>

using DataVec = std::vector<uint8_t>;

class BitStack {
public:
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
            *buffer_.rbegin() |= val & ~(~0 << mask_size);
            val >>= mask_size;
        };
    }

    void push_bytes(const DataVec& vec) {
        assert(!free_);
        buffer_.insert(buffer_.end(), vec.begin(), vec.end());
    }

    size_t pop(size_t size) {
        size_t val = 0;
        while (size) {
            if (free_ == 8) {
                buffer_.pop_back();
                free_ = 0;
            }

            size_t mask_size = std::min(8 - free_, size);
            free_ += mask_size;
            size -= mask_size;

            val <<= mask_size;
            val |= *buffer_.rbegin() & ~(~0 << mask_size);
            *buffer_.rbegin() >>= mask_size;
        };
        return val;
    }

    size_t read(size_t size) {
        size_t val = 0;
        size_t total_size = size;
        while (size) {
            size_t mask_size = std::min(8 - read_i_ % 8, size);

            size_t shift = (8 - read_i_ % 8) - mask_size;
            val |= (buffer_[read_i_ / 8] & (~(~0 << mask_size) << shift)) >> shift << (total_size - size);
            read_i_ += mask_size;
            size -= mask_size;
        };
        return val;
    }

    DataVec read_bytes(size_t size) {
        assert(read_i_ % 8 == 0);
        DataVec vec(buffer_.data() + read_i_ / 8, buffer_.data() + read_i_ / 8 + size);
        read_i_ += size * 8;
        return vec;
    }

    DataVec buffer_;
    size_t free_ = 0;
    size_t read_i_ = 0;
};

#if defined(_MSC_VER)
#pragma warning(disable : 4463)
#else
#pragma GCC diagnostic ignored "-Woverflow"
#endif

#define GET_BIT_FIELD_WIDTH(T, f) \
    []() constexpr -> size_t \
    { \
        T t{}; \
        t.f = static_cast<decltype(t.f)>(~0); \
        size_t bitcount = 0; \
        while (t.f != 0) { \
            t.f >>= 1; \
            ++bitcount; \
        } \
        return bitcount; \
    }()