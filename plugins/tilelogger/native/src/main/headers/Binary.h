#pragma once
#include <cstdint>
#include <stdexcept>
#include <string>
#include <vector>

using DataVec = std::vector<uint8_t>;
inline constexpr size_t max_config_bytes = 1024 * 1024;
inline constexpr size_t max_frame_bytes = max_config_bytes + 4096;
inline constexpr size_t max_records = 10000000;

struct Encoder {
    DataVec bytes;
    void integer(uint64_t value, unsigned width) {
        for (unsigned i = 0; i < width; ++i) bytes.push_back(static_cast<uint8_t>(value >> (i * 8)));
    }
    void blob(const DataVec& value) {
        if (value.size() > max_config_bytes) throw std::invalid_argument("Payload too large");
        integer(value.size(), 4);
        bytes.insert(bytes.end(), value.begin(), value.end());
    }
    void text(const std::string& value) { blob(DataVec(value.begin(), value.end())); }
};

struct Decoder {
    const DataVec& bytes;
    size_t offset{};
    size_t remaining() const { return bytes.size() - offset; }
    uint64_t integer(unsigned width) {
        if (width > 8 || width > remaining()) throw std::runtime_error("Truncated journal field");
        uint64_t value = 0;
        for (unsigned i = 0; i < width; ++i) value |= uint64_t(bytes[offset++]) << (i * 8);
        return value;
    }
    DataVec blob(size_t limit = max_config_bytes) {
        size_t length = integer(4);
        if (length > limit || length > remaining())
            throw std::runtime_error("Invalid journal payload length");
        DataVec result(bytes.begin() + offset, bytes.begin() + offset + length);
        offset += length;
        return result;
    }
    std::string text(size_t limit) {
        auto b = blob(limit);
        return std::string(b.begin(), b.end());
    }
    void end() const {
        if (remaining()) throw std::runtime_error("Unexpected journal fields");
    }
};

inline uint32_t checksum(const DataVec& bytes) {
    uint32_t hash = 2166136261u;
    for (uint8_t b : bytes) {
        hash ^= b;
        hash *= 16777619u;
    }
    return hash;
}

inline DataVec frame(const DataVec& bytes) {
    if (bytes.empty() || bytes.size() > max_frame_bytes) throw std::invalid_argument("Invalid frame size");
    Encoder out;
    out.integer(bytes.size(), 4);
    out.integer(checksum(bytes), 4);
    out.bytes.insert(out.bytes.end(), bytes.begin(), bytes.end());
    return out.bytes;
}
