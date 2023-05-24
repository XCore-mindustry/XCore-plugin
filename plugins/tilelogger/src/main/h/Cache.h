#pragma once
#include <vector>
#include <unordered_map>
#include <stddef.h>
#include <numeric>
#include <fstream>
#include <bit>

template<class Id>
class Cache {
public:
    void Reset(const std::filesystem::path& path, bool write) {
        ids_ = {};
        data_ = {};
        file_flags_ = std::ios::binary | std::ios::in | (write ? std::ios::app : 0);

        file_.close();
        file_.open(path, std::ios::binary | std::ios::in | (write ? std::ios::app : 0));
        BitStack bs;
        bs.buffer_.assign(std::istreambuf_iterator<char>(file_), std::istreambuf_iterator<char>());

        while (bs.read_i_ / 8 < bs.buffer_.size()) {
            size_t len = bs.read(32);
            auto [it, insert] = ids_.emplace(bs.read_bytes(len), static_cast<Id>(data_.size()));
            data_.push_back(&it->first);
        }
    }

    Id operator[](const DataVec& data) {
        auto [it, insert] = ids_.emplace(data, static_cast<Id>(data_.size()));
        if (insert) {
            data_.push_back(&it->first);
            if (file_ && file_flags_ & std::ios::app) {
                BitStack bs;
                bs.push(data.size(), 32);
                bs.push_bytes(data);
                file_.write(std::bit_cast<const char*>(bs.buffer_.data()), bs.buffer_.size());
            }
        }
        return it->second;
    }

    std::optional<Id> at(const DataVec& data) const {
        auto it = ids_.find(data);
        return it == ids_.end() ? std::nullopt : std::optional<Id>(it->second);
    }

    const DataVec& operator[](Id id) const {
        return *data_[id];
    }
    
    size_t Size() const noexcept {
        return data_.size() * sizeof(data_[0])
            + std::accumulate(ids_.begin(), ids_.end(), 0ull, [](size_t a, const std::pair<const DataVec, Id>& b){
                return a + b.first.size() * sizeof(b.first[0]) + sizeof(Id);
            });
    }

    size_t Capacity() const noexcept {
        return data_.capacity() * sizeof(data_[0])
            + std::accumulate(ids_.begin(), ids_.end(), 0ull, [](size_t a, const std::pair<const DataVec, Id>& b){
                return a + b.first.capacity() * sizeof(b.first[0]) + sizeof(Id);
            });
    }

private:
    std::unordered_map<DataVec, Id> ids_;
    std::vector<const DataVec*> data_;
    std::fstream file_;
    std::ios::openmode file_flags_;
};