#pragma once
#include <fstream>
#include <filesystem>
#include <limits>
#include <optional>
#include <unordered_map>
#include "Binary.h"
#include "BitStack.h"

struct DataHash {
    size_t operator()(const DataVec& value) const noexcept { return checksum(value); }
};

template <class Id>
class Cache {
public:
    Id operator[](const DataVec& value) {
        auto existing = ids_.find(value);
        if (existing != ids_.end()) return existing->second;
        if (data_.size() >= max_records || data_.size() >= std::numeric_limits<Id>::max())
            throw std::runtime_error("Dictionary exhausted");
        auto id = static_cast<Id>(data_.size());
        data_.push_back(value);
        ids_.emplace(value, id);
        return id;
    }
    const DataVec& operator[](Id id) const { return data_.at(id); }
    std::optional<Id> at(const DataVec& value) const {
        auto it = ids_.find(value);
        return it == ids_.end() ? std::nullopt : std::optional(it->second);
    }
    void LoadLegacy(const std::filesystem::path& path, size_t limit) {
        std::ifstream file(path, std::ios::binary);
        if (!file) throw std::runtime_error("Cannot open dictionary: " + path.string());
        if (std::filesystem::file_size(path) > 512ull * 1024 * 1024)
            throw std::runtime_error("Dictionary too large");
        BitStack input;
        input.buffer_.assign(std::istreambuf_iterator<char>(file), {});
        while (input.read_i_ / 8 < input.buffer_.size()) {
            size_t length = input.read(32);
            if (length > limit) throw std::runtime_error("Legacy dictionary value too large");
            auto value = input.read_bytes(length);
            if (at(value)) throw std::runtime_error("Duplicate legacy dictionary value");
            (*this)[value];
        }
    }
    size_t Size() const {
        size_t total = data_.size() * sizeof(DataVec);
        for (const auto& value : data_) total += value.size();
        return total;
    }
    size_t Capacity() const {
        size_t total = data_.capacity() * sizeof(DataVec);
        for (const auto& value : data_) total += value.capacity();
        return total;
    }

private:
    std::vector<DataVec> data_;
    std::unordered_map<DataVec, Id, DataHash> ids_;
};
